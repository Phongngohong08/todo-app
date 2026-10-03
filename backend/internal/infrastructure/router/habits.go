package router

import (
	"net/http"
	"time"
	"todo-backend/internal/domain"
	"todo-backend/internal/usecase/focus"
	"todo-backend/internal/usecase/habit"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
)

// ── Thói quen ────────────────────────────────────────────────────────────────

func (rm *RouteManager) handleSyncHabits(c *gin.Context) {
	since, ok := sinceParam(c)
	if !ok {
		return
	}
	changes, err := rm.habitUC.Changes(c.Request.Context(), c.GetString("userID"), since)
	if err != nil {
		respondError(c, err)
		return
	}
	c.JSON(http.StatusOK, changes)
}

func (rm *RouteManager) handleSaveHabit(c *gin.Context) {
	id, ok := uuidParam(c, "id")
	if !ok {
		return
	}
	var input habit.HabitInput
	if !bindJSON(c, &input) {
		return
	}
	h, err := rm.habitUC.Save(c.Request.Context(), id, c.GetString("userID"), input)
	if err != nil {
		respondError(c, err)
		return
	}
	c.JSON(http.StatusOK, h)
}

func (rm *RouteManager) handleDeleteHabit(c *gin.Context) {
	id, ok := uuidParam(c, "id")
	if !ok {
		return
	}
	if err := rm.habitUC.Delete(c.Request.Context(), id, c.GetString("userID")); err != nil {
		respondError(c, err)
		return
	}
	c.Status(http.StatusNoContent)
}

func (rm *RouteManager) handleSetHabitCheckin(c *gin.Context) {
	id, ok := uuidParam(c, "id")
	if !ok {
		return
	}
	var input habit.CheckinInput
	if !bindJSON(c, &input) {
		return
	}
	checkin, err := rm.habitUC.SetCheckin(c.Request.Context(), c.GetString("userID"), id, c.Param("date"), input.Done)
	if err != nil {
		respondError(c, err)
		return
	}
	c.JSON(http.StatusOK, checkin)
}

// ── Lịch sử tập trung ────────────────────────────────────────────────────────

func (rm *RouteManager) handleSyncFocusSessions(c *gin.Context) {
	since, ok := sinceParam(c)
	if !ok {
		return
	}
	changes, err := rm.focusUC.Changes(c.Request.Context(), c.GetString("userID"), since)
	if err != nil {
		respondError(c, err)
		return
	}
	c.JSON(http.StatusOK, changes)
}

func (rm *RouteManager) handleRecordFocusSession(c *gin.Context) {
	id, ok := uuidParam(c, "id")
	if !ok {
		return
	}
	var input focus.SessionInput
	if !bindJSON(c, &input) {
		return
	}
	s, err := rm.focusUC.Record(c.Request.Context(), id, c.GetString("userID"), input)
	if err != nil {
		respondError(c, err)
		return
	}
	c.JSON(http.StatusOK, s)
}

// ── Tham số dùng chung ───────────────────────────────────────────────────────

// sinceParam đọc ?since=<RFC3339> (mốc server_time của lần đồng bộ trước). Không có = tải toàn bộ.
func sinceParam(c *gin.Context) (*time.Time, bool) {
	s := c.Query("since")
	if s == "" {
		return nil, true
	}
	t, err := time.Parse(time.RFC3339Nano, s)
	if err != nil {
		respondError(c, domain.NewValidationError("since phải theo định dạng RFC3339"))
		return nil, false
	}
	return &t, true
}

// uuidParam: id trên đường dẫn phải là UUID; sai thì trả 404 như bản ghi không tồn tại.
func uuidParam(c *gin.Context, name string) (string, bool) {
	id := c.Param(name)
	if _, err := uuid.Parse(id); err != nil {
		respondError(c, domain.ErrNotFound)
		return "", false
	}
	return id, true
}
