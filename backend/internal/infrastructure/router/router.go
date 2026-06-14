package router

import (
	"fmt"
	"log"
	"net/http"
	"time"
	"todo-backend/internal/domain"
	"todo-backend/internal/usecase/auth"
	"todo-backend/internal/usecase/coach"
	"todo-backend/internal/usecase/memory"
	"todo-backend/internal/usecase/plan"
	"todo-backend/internal/usecase/quickadd"
	"todo-backend/internal/usecase/task"

	"github.com/gin-gonic/gin"
)

type RouteManager struct {
	authUC     *auth.AuthUseCase
	taskUC     *task.TaskUseCase
	planUC     *plan.PlanUseCase
	coachUC    *coach.CoachUseCase
	memoryUC   *memory.MemoryUseCase
	quickAddUC *quickadd.QuickAddUseCase
	memoryRepo domain.MemoryRepository
	statsRepo  domain.StatsRepository
	userRepo   domain.UserRepository
}

func NewRouteManager(
	authUC *auth.AuthUseCase,
	taskUC *task.TaskUseCase,
	planUC *plan.PlanUseCase,
	coachUC *coach.CoachUseCase,
	memoryUC *memory.MemoryUseCase,
	quickAddUC *quickadd.QuickAddUseCase,
	memoryRepo domain.MemoryRepository,
	statsRepo domain.StatsRepository,
	userRepo domain.UserRepository,
) *RouteManager {
	return &RouteManager{
		authUC:     authUC,
		taskUC:     taskUC,
		planUC:     planUC,
		coachUC:    coachUC,
		memoryUC:   memoryUC,
		quickAddUC: quickAddUC,
		memoryRepo: memoryRepo,
		statsRepo:  statsRepo,
		userRepo:   userRepo,
	}
}

func (rm *RouteManager) SetupRouter() *gin.Engine {
	r := gin.Default()

	// Enable CORS
	r.Use(func(c *gin.Context) {
		c.Writer.Header().Set("Access-Control-Allow-Origin", "*")
		c.Writer.Header().Set("Access-Control-Allow-Credentials", "true")
		c.Writer.Header().Set("Access-Control-Allow-Headers", "Content-Type, Content-Length, Accept-Encoding, X-CSRF-Token, Authorization, accept, origin, Cache-Control, X-Requested-With")
		c.Writer.Header().Set("Access-Control-Allow-Methods", "POST, OPTIONS, GET, PUT, DELETE")

		if c.Request.Method == "OPTIONS" {
			c.AbortWithStatus(204)
			return
		}

		c.Next()
	})

	api := r.Group("/api/v1")
	{
		// Auth Routes
		authGroup := api.Group("/auth")
		{
			authGroup.POST("/register", rm.handleRegister)
			authGroup.POST("/login", rm.handleLogin)
			authGroup.POST("/refresh", rm.handleRefresh)
		}

		// Protected Routes
		protected := api.Group("")
		protected.Use(AuthMiddleware(rm.authUC))
		{
			// User Preferences
			protected.GET("/preferences", rm.handleGetPreferences)
			protected.PUT("/preferences", rm.handleUpdatePreferences)

			// Tasks Routes
			tasksGroup := protected.Group("/tasks")
			{
				tasksGroup.POST("", rm.handleCreateTask)
				tasksGroup.GET("", rm.handleListTasks)
				tasksGroup.GET("/:id", rm.handleGetTask)
				tasksGroup.PUT("/:id", rm.handleUpdateTask)
				tasksGroup.DELETE("/:id", rm.handleDeleteTask)
				
				tasksGroup.POST("/:id/start", rm.handleStartTask)
				tasksGroup.POST("/:id/complete", rm.handleCompleteTask)
				tasksGroup.POST("/:id/postpone", rm.handlePostponeTask)
				tasksGroup.POST("/:id/cancel", rm.handleCancelTask)
			}

			// Daily Plans Routes
			plansGroup := protected.Group("/plans")
			{
				plansGroup.GET("/daily", rm.handleGetDailyPlan)
				plansGroup.POST("/daily/generate", rm.handleGenerateDailyPlan)
			}

			// AI Coach Chat
			aiGroup := protected.Group("/ai")
			{
				aiGroup.POST("/chat", rm.handleAIChat)
				aiGroup.POST("/parse-task", rm.handleParseTask)
				aiGroup.GET("/memories", rm.handleListMemories)
				aiGroup.DELETE("/memories/:id", rm.handleDeleteMemory)
				aiGroup.POST("/memories/trigger-extraction", rm.handleTriggerMemoryExtraction) // Dev/Admin endpoint to manually trigger background extraction
			}

			// Statistics summary
			protected.GET("/stats/summary", rm.handleGetStatsSummary)
		}
	}

	return r
}

// Handler functions

func (rm *RouteManager) handleRegister(c *gin.Context) {
	var input auth.RegisterInput
	if err := c.ShouldBindJSON(&input); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	user, err := rm.authUC.Register(c.Request.Context(), input)
	if err != nil {
		handleError(c, http.StatusInternalServerError, err)
		return
	}

	c.JSON(http.StatusCreated, user)
}

func (rm *RouteManager) handleLogin(c *gin.Context) {
	var input auth.LoginInput
	if err := c.ShouldBindJSON(&input); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	res, err := rm.authUC.Login(c.Request.Context(), input)
	if err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, res)
}

type RefreshInput struct {
	RefreshToken string `json:"refresh_token" binding:"required"`
}

func (rm *RouteManager) handleRefresh(c *gin.Context) {
	var input RefreshInput
	if err := c.ShouldBindJSON(&input); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	res, err := rm.authUC.Refresh(c.Request.Context(), input.RefreshToken)
	if err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, res)
}

func (rm *RouteManager) handleGetPreferences(c *gin.Context) {
	userID := c.GetString("userID")
	prefs, err := rm.userRepo.GetPreferences(c.Request.Context(), userID)
	if err != nil {
		handleError(c, http.StatusInternalServerError, err)
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
	WorkDurationPreference int    `json:"work_duration_preference" binding:"required"`
}

func (rm *RouteManager) handleUpdatePreferences(c *gin.Context) {
	userID := c.GetString("userID")
	var input UpdatePreferencesInput
	if err := c.ShouldBindJSON(&input); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	prefs := &domain.UserPreferences{
		UserID:                 userID,
		MorningStartTime:       input.MorningStartTime,
		EveningEndTime:         input.EveningEndTime,
		WorkDurationPreference: input.WorkDurationPreference,
		UpdatedAt:              time.Now(),
	}

	err := rm.userRepo.UpdatePreferences(c.Request.Context(), prefs)
	if err != nil {
		handleError(c, http.StatusInternalServerError, err)
		return
	}

	c.JSON(http.StatusOK, prefs)
}

func (rm *RouteManager) handleCreateTask(c *gin.Context) {
	userID := c.GetString("userID")
	var input task.CreateTaskInput
	if err := c.ShouldBindJSON(&input); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	t, err := rm.taskUC.Create(c.Request.Context(), userID, input)
	if err != nil {
		handleError(c, http.StatusInternalServerError, err)
		return
	}

	c.JSON(http.StatusCreated, t)
}

func (rm *RouteManager) handleListTasks(c *gin.Context) {
	userID := c.GetString("userID")
	status := c.Query("status")
	query := c.Query("q")
	tag := c.Query("tag")

	var dueDateBefore *time.Time
	dueDateBeforeStr := c.Query("due_date_before")
	if dueDateBeforeStr != "" {
		parsed, err := time.Parse(time.RFC3339, dueDateBeforeStr)
		if err == nil {
			dueDateBefore = &parsed
		}
	}

	tasks, err := rm.taskUC.List(c.Request.Context(), userID, status, dueDateBefore, query, tag)
	if err != nil {
		handleError(c, http.StatusInternalServerError, err)
		return
	}

	c.JSON(http.StatusOK, tasks)
}

func (rm *RouteManager) handleGetTask(c *gin.Context) {
	userID := c.GetString("userID")
	id := c.Param("id")

	t, err := rm.taskUC.GetByID(c.Request.Context(), id, userID)
	if err != nil {
		if err == domain.ErrTaskNotFound {
			c.JSON(http.StatusNotFound, gin.H{"error": "task not found"})
			return
		}
		handleError(c, http.StatusInternalServerError, err)
		return
	}

	c.JSON(http.StatusOK, t)
}

func (rm *RouteManager) handleUpdateTask(c *gin.Context) {
	userID := c.GetString("userID")
	id := c.Param("id")

	var input task.UpdateTaskInput
	if err := c.ShouldBindJSON(&input); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	t, err := rm.taskUC.Update(c.Request.Context(), id, userID, input)
	if err != nil {
		if err == domain.ErrTaskNotFound {
			c.JSON(http.StatusNotFound, gin.H{"error": "task not found"})
			return
		}
		handleError(c, http.StatusInternalServerError, err)
		return
	}

	c.JSON(http.StatusOK, t)
}

func (rm *RouteManager) handleDeleteTask(c *gin.Context) {
	userID := c.GetString("userID")
	id := c.Param("id")

	err := rm.taskUC.Delete(c.Request.Context(), id, userID)
	if err != nil {
		if err == domain.ErrTaskNotFound {
			c.JSON(http.StatusNotFound, gin.H{"error": "task not found"})
			return
		}
		handleError(c, http.StatusInternalServerError, err)
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "task deleted successfully"})
}

func (rm *RouteManager) handleStartTask(c *gin.Context) {
	userID := c.GetString("userID")
	id := c.Param("id")

	t, err := rm.taskUC.Start(c.Request.Context(), id, userID)
	if err != nil {
		if err == domain.ErrTaskNotFound {
			c.JSON(http.StatusNotFound, gin.H{"error": "task not found"})
			return
		}
		if err == domain.ErrInvalidStatusTrans {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}
		handleError(c, http.StatusInternalServerError, err)
		return
	}

	c.JSON(http.StatusOK, t)
}

func (rm *RouteManager) handleCompleteTask(c *gin.Context) {
	userID := c.GetString("userID")
	id := c.Param("id")

	t, err := rm.taskUC.Complete(c.Request.Context(), id, userID)
	if err != nil {
		if err == domain.ErrTaskNotFound {
			c.JSON(http.StatusNotFound, gin.H{"error": "task not found"})
			return
		}
		if err == domain.ErrInvalidStatusTrans {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}
		handleError(c, http.StatusInternalServerError, err)
		return
	}

	c.JSON(http.StatusOK, t)
}

func (rm *RouteManager) handlePostponeTask(c *gin.Context) {
	userID := c.GetString("userID")
	id := c.Param("id")

	var input task.PostponeTaskInput
	if err := c.ShouldBindJSON(&input); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	t, err := rm.taskUC.Postpone(c.Request.Context(), id, userID, input)
	if err != nil {
		if err == domain.ErrTaskNotFound {
			c.JSON(http.StatusNotFound, gin.H{"error": "task not found"})
			return
		}
		if err == domain.ErrInvalidStatusTrans {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}
		handleError(c, http.StatusInternalServerError, err)
		return
	}

	c.JSON(http.StatusOK, t)
}

func (rm *RouteManager) handleCancelTask(c *gin.Context) {
	userID := c.GetString("userID")
	id := c.Param("id")

	t, err := rm.taskUC.Cancel(c.Request.Context(), id, userID)
	if err != nil {
		if err == domain.ErrTaskNotFound {
			c.JSON(http.StatusNotFound, gin.H{"error": "task not found"})
			return
		}
		if err == domain.ErrInvalidStatusTrans {
			c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
			return
		}
		handleError(c, http.StatusInternalServerError, err)
		return
	}

	c.JSON(http.StatusOK, t)
}


func (rm *RouteManager) handleGetDailyPlan(c *gin.Context) {
	userID := c.GetString("userID")
	dateStr := c.Query("date")
	
	var date time.Time
	var err error
	if dateStr != "" {
		date, err = time.Parse("2006-01-02", dateStr)
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "invalid date format, use YYYY-MM-DD"})
			return
		}
	} else {
		date = time.Now()
	}

	plan, err := rm.planUC.GetPlan(c.Request.Context(), userID, date)
	if err != nil {
		handleError(c, http.StatusInternalServerError, err)
		return
	}

	if plan == nil {
		// Attempt to auto generate
		localTime := c.Query("local_time")
		plan, err = rm.planUC.Generate(c.Request.Context(), userID, date, localTime)
		if err != nil {
			handleError(c, http.StatusInternalServerError, fmt.Errorf("failed to auto-generate daily plan: %w", err))
			return
		}
	}

	c.JSON(http.StatusOK, plan)
}

func (rm *RouteManager) handleGenerateDailyPlan(c *gin.Context) {
	userID := c.GetString("userID")
	
	// Default to today
	date := time.Now()
	localTime := c.Query("local_time")

	plan, err := rm.planUC.Generate(c.Request.Context(), userID, date, localTime)
	if err != nil {
		handleError(c, http.StatusInternalServerError, err)
		return
	}

	c.JSON(http.StatusOK, plan)
}

func (rm *RouteManager) handleAIChat(c *gin.Context) {
	userID := c.GetString("userID")
	var input coach.ChatInput
	if err := c.ShouldBindJSON(&input); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	reply, err := rm.coachUC.Chat(c.Request.Context(), userID, input.Message)
	if err != nil {
		handleError(c, http.StatusInternalServerError, err)
		return
	}

	c.JSON(http.StatusOK, coach.ChatResponse{Reply: reply})
}

type ParseTaskInput struct {
	Text      string `json:"text" binding:"required"`
	LocalTime string `json:"local_time"` // RFC3339 thời điểm hiện tại của user (tùy chọn)
}

func (rm *RouteManager) handleParseTask(c *gin.Context) {
	var input ParseTaskInput
	if err := c.ShouldBindJSON(&input); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	nowContext := input.LocalTime
	if nowContext == "" {
		nowContext = time.Now().Format(time.RFC3339)
	}

	parsed, err := rm.quickAddUC.Parse(c.Request.Context(), input.Text, nowContext)
	if err != nil {
		handleError(c, http.StatusInternalServerError, err)
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
		handleError(c, http.StatusInternalServerError, err)
		return
	}

	c.JSON(http.StatusOK, memories)
}

func (rm *RouteManager) handleDeleteMemory(c *gin.Context) {
	id := c.Param("id")
	if rm.memoryRepo == nil {
		c.JSON(http.StatusNotImplemented, gin.H{"error": "vector store not configured"})
		return
	}

	err := rm.memoryRepo.Delete(c.Request.Context(), id)
	if err != nil {
		handleError(c, http.StatusInternalServerError, err)
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
		handleError(c, http.StatusInternalServerError, err)
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"message":   "memory extraction completed",
		"analyzed":  res.Analyzed,
		"extracted": res.Saved,
	})
}

func (rm *RouteManager) handleGetStatsSummary(c *gin.Context) {
	userID := c.GetString("userID")
	summary, err := rm.statsRepo.GetSummary(c.Request.Context(), userID)
	if err != nil {
		handleError(c, http.StatusInternalServerError, err)
		return
	}

	c.JSON(http.StatusOK, summary)
}

func handleError(c *gin.Context, status int, err error) {
	log.Printf("[API ERROR] %s %s | Status: %d | Error: %v", c.Request.Method, c.Request.URL.Path, status, err)
	c.JSON(status, gin.H{"error": err.Error()})
}
