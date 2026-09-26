package router

import (
	"context"
	"errors"
	"log"
	"net/http"
	"strconv"
	"strings"
	"time"
	"todo-backend/internal/domain"
	"todo-backend/internal/infrastructure/ratelimit"
	"todo-backend/internal/usecase/auth"
	"todo-backend/internal/usecase/category"
	"todo-backend/internal/usecase/coach"
	"todo-backend/internal/usecase/memory"
	"todo-backend/internal/usecase/plan"
	"todo-backend/internal/usecase/quickadd"
	"todo-backend/internal/usecase/task"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
)

// Options gom cấu hình tầng HTTP (lấy từ config.Config).
type Options struct {
	CORSAllowedOrigins []string
	TrustedProxies     []string
	Timezone           *time.Location // múi giờ mặc định khi client không gửi tz/date
	AIRatePerMinute    int
	AIDailyLimit       int
	// HealthCheck kiểm tra phụ thuộc bắt buộc (Postgres) cho endpoint /healthz.
	HealthCheck func(ctx context.Context) error
}

type RouteManager struct {
	authUC     *auth.AuthUseCase
	taskUC     *task.TaskUseCase
	planUC     *plan.PlanUseCase
	coachUC    *coach.CoachUseCase
	memoryUC   *memory.MemoryUseCase
	quickAddUC *quickadd.QuickAddUseCase
	categoryUC *category.CategoryUseCase
	memoryRepo domain.MemoryRepository
	statsRepo  domain.StatsRepository
	userRepo   domain.UserRepository
	opts       Options

	// Giới hạn tần suất (xem SetupRouter để biết con số cụ thể)
	loginLimiter    *ratelimit.Limiter
	registerLimiter *ratelimit.Limiter
	tokenLimiter    *ratelimit.Limiter
	apiLimiter      *ratelimit.Limiter
	extractLimiter  *ratelimit.Limiter
	ai              *aiGuard
}

// Dependencies gom các usecase/repository mà tầng HTTP cần.
type Dependencies struct {
	Auth       *auth.AuthUseCase
	Tasks      *task.TaskUseCase
	Plans      *plan.PlanUseCase
	Coach      *coach.CoachUseCase
	Memory     *memory.MemoryUseCase
	QuickAdd   *quickadd.QuickAddUseCase
	Categories *category.CategoryUseCase
	MemoryRepo domain.MemoryRepository
	StatsRepo  domain.StatsRepository
	UserRepo   domain.UserRepository
}

func NewRouteManager(deps Dependencies, opts Options) *RouteManager {
	if opts.Timezone == nil {
		opts.Timezone = time.UTC
	}
	if opts.AIRatePerMinute <= 0 {
		opts.AIRatePerMinute = 6
	}

	return &RouteManager{
		authUC:     deps.Auth,
		taskUC:     deps.Tasks,
		planUC:     deps.Plans,
		coachUC:    deps.Coach,
		memoryUC:   deps.Memory,
		quickAddUC: deps.QuickAdd,
		categoryUC: deps.Categories,
		memoryRepo: deps.MemoryRepo,
		statsRepo:  deps.StatsRepo,
		userRepo:   deps.UserRepo,
		opts:       opts,

		// Đăng nhập: dồn 5 lần, sau đó 5 lần/phút mỗi IP — đủ cho gõ sai vài lần, chặn dò mật khẩu.
		loginLimiter: ratelimit.New(12*time.Second, 5),
		// Đăng ký: 3 lần, sau đó 1 lần/phút mỗi IP — chặn tạo tài khoản hàng loạt.
		registerLimiter: ratelimit.New(time.Minute, 3),
		// Refresh/logout: app tự gọi khi token hết hạn; 10 lần dồn, 30 lần/phút mỗi IP.
		tokenLimiter: ratelimit.New(2*time.Second, 10),
		// Mọi API đã đăng nhập: 60 request dồn, 5 request/giây mỗi user.
		apiLimiter: ratelimit.New(200*time.Millisecond, 60),
		// Phân tích trí nhớ thủ công tốn 1 lượt LLM + nhiều lượt embedding: 2 lần dồn, 1 lần/20 phút.
		extractLimiter: ratelimit.New(20*time.Minute, 2),
		ai: &aiGuard{
			perMinute: ratelimit.New(time.Minute/time.Duration(opts.AIRatePerMinute), opts.AIRatePerMinute),
			daily:     ratelimit.NewDailyQuota(opts.AIDailyLimit, opts.Timezone),
		},
	}
}

func (rm *RouteManager) SetupRouter() (*gin.Engine, error) {
	r := gin.Default()

	// Chỉ tin X-Forwarded-For từ reverse proxy đã khai báo; nếu không, client tự đặt header để
	// giả IP và né giới hạn tần suất theo IP.
	if err := r.SetTrustedProxies(rm.opts.TrustedProxies); err != nil {
		return nil, err
	}

	if len(rm.opts.CORSAllowedOrigins) > 0 {
		r.Use(CORSMiddleware(rm.opts.CORSAllowedOrigins))
	}
	r.Use(BodyLimitMiddleware(maxBodyBytes))

	r.GET("/healthz", rm.handleHealth)

	api := r.Group("/api/v1")
	{
		// Auth Routes
		authGroup := api.Group("/auth")
		{
			authGroup.POST("/register", RateLimitMiddleware(rm.registerLimiter, byClientIP), rm.handleRegister)
			authGroup.POST("/login", RateLimitMiddleware(rm.loginLimiter, byClientIP), rm.handleLogin)
			authGroup.POST("/refresh", RateLimitMiddleware(rm.tokenLimiter, byClientIP), rm.handleRefresh)
			authGroup.POST("/logout", RateLimitMiddleware(rm.tokenLimiter, byClientIP), rm.handleLogout)
		}

		// Protected Routes
		protected := api.Group("")
		protected.Use(AuthMiddleware(rm.authUC), RateLimitMiddleware(rm.apiLimiter, byUserID))
		{
			// User Preferences
			protected.GET("/preferences", rm.handleGetPreferences)
			protected.PUT("/preferences", rm.handleUpdatePreferences)

			// Danh mục tự tạo (thay toàn bộ danh sách mỗi lần lưu)
			protected.GET("/categories", rm.handleListCategories)
			protected.PUT("/categories", rm.handleReplaceCategories)

			// Tasks Routes
			tasksGroup := protected.Group("/tasks")
			{
				tasksGroup.POST("", rm.handleCreateTask)
				tasksGroup.GET("", rm.handleListTasks)
				// Đồng bộ offline: thay đổi kể từ mốc since (kể cả task đã xóa)
				tasksGroup.GET("/sync", rm.handleSyncTasks)
				tasksGroup.GET("/:id", rm.handleGetTask)
				// PUT = ghi toàn bộ trạng thái, tạo mới nếu chưa có (idempotent, id do client sinh)
				tasksGroup.PUT("/:id", rm.handleSaveTask)
				tasksGroup.DELETE("/:id", rm.handleDeleteTask)

				tasksGroup.POST("/:id/complete", rm.handleCompleteTask)
			}

			// Daily Plans Routes (giới hạn AI kiểm tra bên trong handler: chỉ trừ lượt khi thực sự gọi AI)
			plansGroup := protected.Group("/plans")
			{
				plansGroup.GET("/daily", rm.handleGetDailyPlan)
				plansGroup.POST("/daily/generate", rm.handleGenerateDailyPlan)
			}

			// AI Coach Chat
			aiGroup := protected.Group("/ai")
			{
				aiGroup.POST("/chat", rm.ai.middleware(), rm.handleAIChat)
				aiGroup.GET("/chat/history", rm.handleChatHistory)
				aiGroup.POST("/parse-task", rm.ai.middleware(), rm.handleParseTask)
				aiGroup.GET("/memories", rm.handleListMemories)
				aiGroup.DELETE("/memories/:id", rm.handleDeleteMemory)
				aiGroup.POST("/memories/trigger-extraction", // phân tích thủ công (nút "Phân tích" trong app)
					RateLimitMiddleware(rm.extractLimiter, byUserID), rm.ai.middleware(), rm.handleTriggerMemoryExtraction)
			}

			// Statistics summary
			protected.GET("/stats/summary", rm.handleGetStatsSummary)
		}
	}

	return r, nil
}

// ── Health ───────────────────────────────────────────────────────────────────

func (rm *RouteManager) handleHealth(c *gin.Context) {
	if rm.opts.HealthCheck != nil {
		ctx, cancel := context.WithTimeout(c.Request.Context(), 2*time.Second)
		defer cancel()
		if err := rm.opts.HealthCheck(ctx); err != nil {
			log.Printf("[HEALTH] database check failed: %v", err)
			c.JSON(http.StatusServiceUnavailable, gin.H{"status": "unavailable"})
			return
		}
	}
	c.JSON(http.StatusOK, gin.H{"status": "ok"})
}

// ── Auth ─────────────────────────────────────────────────────────────────────

func (rm *RouteManager) handleRegister(c *gin.Context) {
	var input auth.RegisterInput
	if !bindJSON(c, &input) {
		return
	}

	user, err := rm.authUC.Register(c.Request.Context(), input)
	if err != nil {
		respondError(c, err)
		return
	}

	c.JSON(http.StatusCreated, user)
}

func (rm *RouteManager) handleLogin(c *gin.Context) {
	var input auth.LoginInput
	if !bindJSON(c, &input) {
		return
	}

	res, err := rm.authUC.Login(c.Request.Context(), input)
	if err != nil {
		respondError(c, err)
		return
	}

	c.JSON(http.StatusOK, res)
}

type RefreshInput struct {
	RefreshToken string `json:"refresh_token" binding:"required,max=4096"`
}

func (rm *RouteManager) handleRefresh(c *gin.Context) {
	var input RefreshInput
	if !bindJSON(c, &input) {
		return
	}

	res, err := rm.authUC.Refresh(c.Request.Context(), input.RefreshToken)
	if err != nil {
		respondError(c, err)
		return
	}

	c.JSON(http.StatusOK, res)
}

// handleLogout thu hồi refresh token (mọi phiên của tài khoản). Nhận refresh token trong body thay vì
// dùng access token, để vẫn đăng xuất được khi access token đã hết hạn.
func (rm *RouteManager) handleLogout(c *gin.Context) {
	var input RefreshInput
	if !bindJSON(c, &input) {
		return
	}

	if err := rm.authUC.Logout(c.Request.Context(), input.RefreshToken); err != nil {
		respondError(c, err)
		return
	}

	c.Status(http.StatusNoContent)
}

// ── Preferences ──────────────────────────────────────────────────────────────

func (rm *RouteManager) handleGetPreferences(c *gin.Context) {
	userID := c.GetString("userID")
	prefs, err := rm.userRepo.GetPreferences(c.Request.Context(), userID)
	if err != nil {
		respondError(c, err)
		return
	}
	if prefs == nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "preferences not found"})
		return
	}
	c.JSON(http.StatusOK, prefs)
}

type UpdatePreferencesInput struct {
	MorningStartTime       string `json:"morning_start_time" binding:"required"`
	EveningEndTime         string `json:"evening_end_time" binding:"required"`
	WorkDurationPreference int    `json:"work_duration_preference" binding:"required,min=15,max=480"`
}

func (rm *RouteManager) handleUpdatePreferences(c *gin.Context) {
	userID := c.GetString("userID")
	var input UpdatePreferencesInput
	if !bindJSON(c, &input) {
		return
	}

	morning, errM := parseClock(input.MorningStartTime)
	evening, errE := parseClock(input.EveningEndTime)
	if errM != nil || errE != nil {
		respondError(c, domain.NewValidationError("Giờ phải có dạng HH:mm, ví dụ 08:00"))
		return
	}
	if !evening.After(morning) {
		respondError(c, domain.NewValidationError("Giờ kết thúc phải sau giờ bắt đầu"))
		return
	}

	prefs := &domain.UserPreferences{
		UserID:                 userID,
		MorningStartTime:       morning.Format("15:04"),
		EveningEndTime:         evening.Format("15:04"),
		WorkDurationPreference: input.WorkDurationPreference,
		UpdatedAt:              time.Now(),
	}

	if err := rm.userRepo.UpdatePreferences(c.Request.Context(), prefs); err != nil {
		respondError(c, err)
		return
	}

	c.JSON(http.StatusOK, prefs)
}

// ── Tasks ────────────────────────────────────────────────────────────────────

func (rm *RouteManager) handleCreateTask(c *gin.Context) {
	userID := c.GetString("userID")
	var input task.CreateTaskInput
	if !bindJSON(c, &input) {
		return
	}

	t, err := rm.taskUC.Create(c.Request.Context(), userID, input)
	if err != nil {
		respondError(c, err)
		return
	}

	c.JSON(http.StatusCreated, t)
}

func (rm *RouteManager) handleListTasks(c *gin.Context) {
	userID := c.GetString("userID")
	status := c.Query("status")
	query := c.Query("q")
	category := c.Query("category")

	if status != "" && status != string(domain.StatusTodo) && status != string(domain.StatusCompleted) {
		respondError(c, domain.NewValidationError("status phải là TODO hoặc COMPLETED"))
		return
	}
	if len([]rune(query)) > 200 || len([]rune(category)) > 50 {
		respondError(c, domain.NewValidationError("Từ khóa hoặc danh mục quá dài"))
		return
	}

	var dueDateBefore *time.Time
	if s := c.Query("due_date_before"); s != "" {
		parsed, err := time.Parse(time.RFC3339, s)
		if err != nil {
			respondError(c, domain.NewValidationError("due_date_before phải theo định dạng RFC3339"))
			return
		}
		dueDateBefore = &parsed
	}

	tasks, err := rm.taskUC.List(c.Request.Context(), userID, status, dueDateBefore, query, category)
	if err != nil {
		respondError(c, err)
		return
	}
	if tasks == nil {
		tasks = []*domain.Task{} // trả [] thay vì null
	}

	c.JSON(http.StatusOK, tasks)
}

func (rm *RouteManager) handleGetTask(c *gin.Context) {
	id, ok := taskIDParam(c)
	if !ok {
		return
	}

	t, err := rm.taskUC.GetByID(c.Request.Context(), id, c.GetString("userID"))
	if err != nil {
		respondError(c, err)
		return
	}

	c.JSON(http.StatusOK, t)
}

// handleSaveTask ghi toàn bộ trạng thái task, tạo mới nếu id chưa tồn tại. Gửi lại cùng request nhiều lần
// cho cùng kết quả (idempotent) — điều kiện để app đồng bộ lại an toàn sau khi mất mạng giữa chừng.
func (rm *RouteManager) handleSaveTask(c *gin.Context) {
	id, ok := taskIDParam(c)
	if !ok {
		return
	}

	var input task.TaskInput
	if !bindJSON(c, &input) {
		return
	}
	if input.ID != "" && input.ID != id {
		respondError(c, domain.NewValidationError("id trong body khác id trên đường dẫn"))
		return
	}

	t, err := rm.taskUC.Save(c.Request.Context(), id, c.GetString("userID"), input)
	if err != nil {
		respondError(c, err)
		return
	}

	c.JSON(http.StatusOK, t)
}

// handleSyncTasks trả về thay đổi kể từ ?since=<server_time lần trước> (RFC3339). Không có since = tải toàn bộ.
func (rm *RouteManager) handleSyncTasks(c *gin.Context) {
	var since *time.Time
	if s := c.Query("since"); s != "" {
		t, err := time.Parse(time.RFC3339Nano, s)
		if err != nil {
			respondError(c, domain.NewValidationError("since phải theo định dạng RFC3339"))
			return
		}
		since = &t
	}

	changes, err := rm.taskUC.Changes(c.Request.Context(), c.GetString("userID"), since)
	if err != nil {
		respondError(c, err)
		return
	}

	c.JSON(http.StatusOK, changes)
}

// ── Categories ───────────────────────────────────────────────────────────────

type CategoriesPayload struct {
	Categories []string `json:"categories" binding:"max=50,dive,max=50"`
}

func (rm *RouteManager) handleListCategories(c *gin.Context) {
	names, err := rm.categoryUC.List(c.Request.Context(), c.GetString("userID"))
	if err != nil {
		respondError(c, err)
		return
	}
	c.JSON(http.StatusOK, CategoriesPayload{Categories: names})
}

func (rm *RouteManager) handleReplaceCategories(c *gin.Context) {
	var input CategoriesPayload
	if !bindJSON(c, &input) {
		return
	}

	names, err := rm.categoryUC.Replace(c.Request.Context(), c.GetString("userID"), input.Categories)
	if err != nil {
		respondError(c, err)
		return
	}
	c.JSON(http.StatusOK, CategoriesPayload{Categories: names})
}

func (rm *RouteManager) handleDeleteTask(c *gin.Context) {
	id, ok := taskIDParam(c)
	if !ok {
		return
	}

	if err := rm.taskUC.Delete(c.Request.Context(), id, c.GetString("userID")); err != nil {
		respondError(c, err)
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "task deleted successfully"})
}

func (rm *RouteManager) handleCompleteTask(c *gin.Context) {
	id, ok := taskIDParam(c)
	if !ok {
		return
	}

	t, err := rm.taskUC.Complete(c.Request.Context(), id, c.GetString("userID"))
	if err != nil {
		respondError(c, err)
		return
	}

	c.JSON(http.StatusOK, t)
}

// ── Daily plans ──────────────────────────────────────────────────────────────

func (rm *RouteManager) handleGetDailyPlan(c *gin.Context) {
	userID := c.GetString("userID")
	date, ok := rm.planDateParam(c)
	if !ok {
		return
	}
	localTime, ok := localTimeParam(c)
	if !ok {
		return
	}

	plan, err := rm.planUC.GetPlan(c.Request.Context(), userID, date)
	if err != nil {
		respondError(c, err)
		return
	}

	if plan == nil {
		// Chưa có lịch → tự tạo (có thể gọi AI)
		plan, ok = rm.generatePlan(c, userID, date, localTime)
		if !ok {
			return
		}
	}

	c.JSON(http.StatusOK, plan)
}

func (rm *RouteManager) handleGenerateDailyPlan(c *gin.Context) {
	userID := c.GetString("userID")
	// App gửi date theo lịch của máy; thiếu thì lấy "hôm nay" theo APP_TIMEZONE (không dùng giờ UTC của server).
	date, ok := rm.planDateParam(c)
	if !ok {
		return
	}
	localTime, ok := localTimeParam(c)
	if !ok {
		return
	}

	plan, ok := rm.generatePlan(c, userID, date, localTime)
	if !ok {
		return
	}

	c.JSON(http.StatusOK, plan)
}

// generatePlan chỉ trừ hạn mức AI khi user còn việc cần xếp (Generate sẽ thực sự gọi AI).
// Trả false khi đã ghi phản hồi lỗi.
func (rm *RouteManager) generatePlan(c *gin.Context, userID string, date time.Time, localTime string) (*domain.DailyPlan, bool) {
	ctx := c.Request.Context()

	hasTasks, err := rm.planUC.HasActiveTasks(ctx, userID)
	if err != nil {
		respondError(c, err)
		return nil, false
	}
	if hasTasks && !rm.ai.allow(c) {
		return nil, false
	}

	plan, err := rm.planUC.Generate(ctx, userID, date, localTime)
	if err != nil {
		respondError(c, err)
		return nil, false
	}
	return plan, true
}

// ── AI ───────────────────────────────────────────────────────────────────────

func (rm *RouteManager) handleAIChat(c *gin.Context) {
	userID := c.GetString("userID")
	var input coach.ChatInput
	if !bindJSON(c, &input) {
		return
	}
	if strings.TrimSpace(input.Message) == "" {
		respondError(c, domain.NewValidationError("Tin nhắn không được để trống"))
		return
	}

	reply, err := rm.coachUC.Chat(c.Request.Context(), userID, input.Message)
	if err != nil {
		respondError(c, err)
		return
	}

	c.JSON(http.StatusOK, coach.ChatResponse{Reply: reply})
}

const (
	defaultChatHistory = 50
	maxChatHistory     = 200
)

// handleChatHistory trả về lịch sử chat (cũ → mới) để app hiển thị lại cuộc trò chuyện với AI Coach.
func (rm *RouteManager) handleChatHistory(c *gin.Context) {
	limit := defaultChatHistory
	if s := c.Query("limit"); s != "" {
		n, err := strconv.Atoi(s)
		if err != nil || n < 1 {
			respondError(c, domain.NewValidationError("limit phải là số nguyên dương"))
			return
		}
		limit = min(n, maxChatHistory)
	}

	messages, err := rm.coachUC.History(c.Request.Context(), c.GetString("userID"), limit)
	if err != nil {
		respondError(c, err)
		return
	}
	c.JSON(http.StatusOK, messages)
}

type ParseTaskInput struct {
	Text      string `json:"text" binding:"required,max=1000"`
	LocalTime string `json:"local_time"` // RFC3339 thời điểm hiện tại của user (tùy chọn)
}

func (rm *RouteManager) handleParseTask(c *gin.Context) {
	var input ParseTaskInput
	if !bindJSON(c, &input) {
		return
	}

	// Chỉ đưa vào prompt một mốc thời gian hợp lệ; thiếu/sai định dạng thì dùng giờ hiện tại theo APP_TIMEZONE.
	nowContext := time.Now().In(rm.opts.Timezone).Format(time.RFC3339)
	if input.LocalTime != "" {
		if t, err := time.Parse(time.RFC3339, input.LocalTime); err == nil {
			nowContext = t.Format(time.RFC3339)
		}
	}

	parsed, err := rm.quickAddUC.Parse(c.Request.Context(), input.Text, nowContext)
	if err != nil {
		respondError(c, err)
		return
	}

	c.JSON(http.StatusOK, parsed)
}

func (rm *RouteManager) handleListMemories(c *gin.Context) {
	userID := c.GetString("userID")
	if rm.memoryRepo == nil {
		c.JSON(http.StatusNotImplemented, gin.H{"error": "vector store not configured"})
		return
	}

	memories, err := rm.memoryRepo.List(c.Request.Context(), userID)
	if err != nil {
		respondError(c, err)
		return
	}
	if memories == nil {
		memories = []*domain.MemoryItem{}
	}

	c.JSON(http.StatusOK, memories)
}

func (rm *RouteManager) handleDeleteMemory(c *gin.Context) {
	id := c.Param("id")
	if _, err := uuid.Parse(id); err != nil {
		respondError(c, domain.NewValidationError("id không hợp lệ"))
		return
	}
	if rm.memoryRepo == nil {
		c.JSON(http.StatusNotImplemented, gin.H{"error": "vector store not configured"})
		return
	}

	if err := rm.memoryRepo.Delete(c.Request.Context(), id); err != nil {
		respondError(c, err)
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "memory deleted successfully"})
}

func (rm *RouteManager) handleTriggerMemoryExtraction(c *gin.Context) {
	userID := c.GetString("userID")
	if rm.memoryUC == nil {
		c.JSON(http.StatusNotImplemented, gin.H{"error": "memory extractor not configured"})
		return
	}

	// Phân tích thủ công nhìn lại 30 ngày để có đủ dữ liệu rút thói quen
	res, err := rm.memoryUC.ExtractAndStoreMemories(c.Request.Context(), userID, 30*24*time.Hour)
	if err != nil {
		respondError(c, err)
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"message":   "memory extraction completed",
		"analyzed":  res.Analyzed,
		"extracted": res.Saved,
	})
}

// ── Stats ────────────────────────────────────────────────────────────────────

func (rm *RouteManager) handleGetStatsSummary(c *gin.Context) {
	loc, ok := rm.locationParam(c)
	if !ok {
		return
	}

	summary, err := rm.statsRepo.GetSummary(c.Request.Context(), c.GetString("userID"), loc)
	if err != nil {
		respondError(c, err)
		return
	}

	c.JSON(http.StatusOK, summary)
}

// ── Helpers ──────────────────────────────────────────────────────────────────

// bindJSON đọc body JSON; lỗi thì tự trả 400 (hoặc 413 nếu body quá lớn) và trả về false.
func bindJSON(c *gin.Context, obj any) bool {
	if err := c.ShouldBindJSON(obj); err != nil {
		var tooLarge *http.MaxBytesError
		if errors.As(err, &tooLarge) {
			c.JSON(http.StatusRequestEntityTooLarge, gin.H{"error": "Dữ liệu gửi lên quá lớn"})
			return false
		}
		c.JSON(http.StatusBadRequest, gin.H{"error": "Dữ liệu không hợp lệ: " + err.Error()})
		return false
	}
	return true
}

// taskIDParam kiểm tra :id là UUID trước khi chạm DB (nếu không Postgres báo lỗi cú pháp → 500).
func taskIDParam(c *gin.Context) (string, bool) {
	id := c.Param("id")
	if _, err := uuid.Parse(id); err != nil {
		respondError(c, domain.ErrTaskNotFound)
		return "", false
	}
	return id, true
}

// locationParam đọc múi giờ IANA (vd "Asia/Ho_Chi_Minh") từ query tz. Thiếu hoặc không nhận ra
// (máy có thể gửi tên mà bản tzdata của server chưa có) thì dùng APP_TIMEZONE thay vì làm hỏng request.
// Trả về bool để thống nhất với các hàm đọc tham số khác (hiện luôn true).
func (rm *RouteManager) locationParam(c *gin.Context) (*time.Location, bool) {
	tz := c.Query("tz")
	if tz == "" || tz == "Local" {
		return rm.opts.Timezone, true
	}
	loc, err := time.LoadLocation(tz)
	if err != nil {
		log.Printf("[API] unknown tz %q, falling back to %s", tz, rm.opts.Timezone)
		return rm.opts.Timezone, true
	}
	return loc, true
}

// planDateParam đọc ?date=YYYY-MM-DD; thiếu thì lấy hôm nay theo múi giờ của người dùng.
func (rm *RouteManager) planDateParam(c *gin.Context) (time.Time, bool) {
	loc, ok := rm.locationParam(c)
	if !ok {
		return time.Time{}, false
	}
	s := c.Query("date")
	if s == "" {
		now := time.Now().In(loc)
		return time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, loc), true
	}
	date, err := time.ParseInLocation("2006-01-02", s, loc)
	if err != nil {
		respondError(c, domain.NewValidationError("date phải có dạng YYYY-MM-DD"))
		return time.Time{}, false
	}
	return date, true
}

// localTimeParam đọc ?local_time=HH:mm (tùy chọn). Giá trị đi thẳng vào prompt nên phải đúng định dạng.
func localTimeParam(c *gin.Context) (string, bool) {
	s := c.Query("local_time")
	if s == "" {
		return "", true
	}
	t, err := parseClock(s)
	if err != nil {
		respondError(c, domain.NewValidationError("local_time phải có dạng HH:mm"))
		return "", false
	}
	return t.Format("15:04"), true
}

func parseClock(s string) (time.Time, error) {
	return time.Parse("15:04", strings.TrimSpace(s))
}

const internalErrorMessage = "Lỗi máy chủ nội bộ. Vui lòng thử lại sau."

// respondError ánh xạ lỗi nghiệp vụ sang mã HTTP. Lỗi không nhận diện được → 500 với thông báo chung;
// chi tiết (lỗi SQL, phản hồi thô của Gemini...) chỉ ghi vào log, không gửi cho client.
func respondError(c *gin.Context, err error) {
	var validation *domain.ValidationError
	switch {
	case errors.As(err, &validation):
		c.JSON(http.StatusBadRequest, gin.H{"error": validation.Message})
	case errors.Is(err, domain.ErrTaskNotFound):
		c.JSON(http.StatusNotFound, gin.H{"error": "task not found"})
	case errors.Is(err, domain.ErrInvalidStatusTrans):
		c.JSON(http.StatusBadRequest, gin.H{"error": "Công việc đã được hoàn thành trước đó"})
	case errors.Is(err, domain.ErrEmailExists):
		c.JSON(http.StatusConflict, gin.H{"error": "Email đã được sử dụng"})
	case errors.Is(err, domain.ErrInvalidCredentials):
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Email hoặc mật khẩu không đúng"})
	case errors.Is(err, domain.ErrInvalidToken):
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Phiên đăng nhập không hợp lệ hoặc đã hết hạn"})
	case errors.Is(err, domain.ErrAIRateLimited):
		// Hết hạn mức/quá tải từ nhà cung cấp AI → 429 kèm thông báo thân thiện.
		logAPIError(c, http.StatusTooManyRequests, err)
		c.JSON(http.StatusTooManyRequests, gin.H{
			"error": "AI đang quá tải hoặc đã hết lượt sử dụng trong ngày. Vui lòng thử lại sau ít phút.",
		})
	case errors.Is(err, domain.ErrAIUnavailable):
		logAPIError(c, http.StatusServiceUnavailable, err)
		c.JSON(http.StatusServiceUnavailable, gin.H{"error": "Tính năng AI tạm thời chưa khả dụng."})
	case errors.Is(err, context.DeadlineExceeded):
		logAPIError(c, http.StatusGatewayTimeout, err)
		c.JSON(http.StatusGatewayTimeout, gin.H{"error": "Máy chủ phản hồi quá lâu. Vui lòng thử lại."})
	default:
		logAPIError(c, http.StatusInternalServerError, err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": internalErrorMessage})
	}
}

func logAPIError(c *gin.Context, status int, err error) {
	log.Printf("[API ERROR] %s %s | Status: %d | Error: %v", c.Request.Method, c.Request.URL.Path, status, err)
}
