package domain

import "errors"

// Lỗi nghiệp vụ dùng chung. Tầng HTTP (router.respondError) ánh xạ chúng sang mã trạng thái phù hợp;
// mọi lỗi KHÁC bị coi là lỗi nội bộ → 500 với thông báo chung (chi tiết chỉ ghi log).
var (
	ErrEmailExists        = errors.New("email already exists")
	ErrInvalidCredentials = errors.New("invalid email or password")
	ErrInvalidToken       = errors.New("invalid or expired token")
	// ErrAIUnavailable: chưa cấu hình GEMINI_API_KEY → tính năng AI tạm không dùng được (HTTP 503).
	ErrAIUnavailable = errors.New("AI service is not configured")
)

// ValidationError là lỗi do dữ liệu đầu vào không hợp lệ; Message được trả nguyên văn cho client (HTTP 400).
type ValidationError struct {
	Message string
}

func (e *ValidationError) Error() string { return e.Message }

func NewValidationError(msg string) error { return &ValidationError{Message: msg} }
