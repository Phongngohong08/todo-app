package assist

import (
	"context"
	"regexp"
	"sort"
	"strings"
	"todo-backend/internal/domain"
)

// Giới hạn kết quả AI trước khi trả về app — LLM có thể trả quá nhiều bước hoặc số phút vô lý.
const (
	MaxSubtasks      = 10
	maxSubtaskLength = 255
	minEstimate      = 5
	maxEstimate      = 1440 // khớp giới hạn estimated_minutes của TaskInput
	maxExamples      = 20
)

// Client được hiện thực bởi GeminiClient.
type Client interface {
	BreakdownTask(ctx context.Context, title, description string) (subtasks []string, estimatedMinutes int, err error)
	EstimateTask(ctx context.Context, title, description string, examples []domain.EstimateExample) (int, error)
}

type AssistUseCase struct {
	client   Client
	taskRepo domain.TaskRepository
}

func NewAssistUseCase(client Client, taskRepo domain.TaskRepository) *AssistUseCase {
	return &AssistUseCase{client: client, taskRepo: taskRepo}
}

// Breakdown là kết quả "Nhờ AI chia nhỏ việc này": các bước con gợi ý + tổng thời lượng ước tính.
// Chỉ TRẢ VỀ — app cho người dùng xem trước rồi mới thêm vào việc.
type Breakdown struct {
	Subtasks         []string `json:"subtasks"`
	EstimatedMinutes int      `json:"estimated_minutes"`
}

// Breakdown chia một việc lớn thành tối đa MaxSubtasks bước cụ thể.
func (u *AssistUseCase) Breakdown(ctx context.Context, title, description string) (*Breakdown, error) {
	title = strings.TrimSpace(title)
	if title == "" {
		return nil, domain.NewValidationError("Cần tiêu đề công việc để chia nhỏ")
	}
	steps, minutes, err := u.client.BreakdownTask(ctx, title, strings.TrimSpace(description))
	if err != nil {
		return nil, err
	}
	clean := cleanSubtasks(steps)
	if len(clean) == 0 {
		return nil, domain.NewValidationError("AI chưa chia được việc này. Hãy mô tả rõ hơn mục tiêu.")
	}
	return &Breakdown{Subtasks: clean, EstimatedMinutes: snapMinutes(minutes)}, nil
}

// Estimate gợi ý thời lượng (phút) cho một việc, dựa trên các việc người dùng đã ước tính trước đây.
func (u *AssistUseCase) Estimate(ctx context.Context, userID, title, description string) (int, error) {
	title = strings.TrimSpace(title)
	if title == "" {
		return 0, domain.NewValidationError("Cần tiêu đề công việc để ước tính")
	}
	minutes, err := u.client.EstimateTask(ctx, title, strings.TrimSpace(description), u.examples(ctx, userID))
	if err != nil {
		return 0, err
	}
	if minutes <= 0 {
		return 0, domain.NewValidationError("AI chưa ước tính được thời lượng cho việc này")
	}
	return snapMinutes(minutes), nil
}

// examples lấy các việc gần đây nhất người dùng đã tự đặt thời lượng (lỗi đọc DB thì bỏ qua — không có mẫu
// AI vẫn ước tính được).
func (u *AssistUseCase) examples(ctx context.Context, userID string) []domain.EstimateExample {
	if u.taskRepo == nil {
		return nil
	}
	tasks, err := u.taskRepo.List(ctx, userID, domain.TaskFilter{})
	if err != nil {
		return nil
	}
	var withEstimate []*domain.Task
	for _, t := range tasks {
		if t.EstimatedMinutes > 0 {
			withEstimate = append(withEstimate, t)
		}
	}
	sort.SliceStable(withEstimate, func(i, j int) bool { return withEstimate[i].UpdatedAt.After(withEstimate[j].UpdatedAt) })
	out := make([]domain.EstimateExample, 0, maxExamples)
	for _, t := range withEstimate {
		if len(out) == maxExamples {
			break
		}
		out = append(out, domain.EstimateExample{Title: t.Title, Minutes: t.EstimatedMinutes})
	}
	return out
}

// listMarker: ký hiệu đầu dòng LLM hay thêm ("1. ", "2) ", "- ", "• ") — chỉ bỏ ký hiệu, giữ số có nghĩa ("5 phút khởi động").
var listMarker = regexp.MustCompile(`^\s*(?:[-•*]|\d+[.)])\s*`)

// cleanSubtasks: bỏ dòng trống/trùng, cắt độ dài, giữ tối đa MaxSubtasks bước.
func cleanSubtasks(in []string) []string {
	seen := make(map[string]bool, len(in))
	out := make([]string, 0, len(in))
	for _, s := range in {
		s = strings.TrimSpace(listMarker.ReplaceAllString(s, ""))
		if s == "" {
			continue
		}
		if r := []rune(s); len(r) > maxSubtaskLength {
			s = string(r[:maxSubtaskLength])
		}
		key := strings.ToLower(s)
		if seen[key] {
			continue
		}
		seen[key] = true
		out = append(out, s)
		if len(out) == MaxSubtasks {
			break
		}
	}
	return out
}

// snapMinutes làm tròn tới bội số 5 phút trong [minEstimate, maxEstimate]; 0 (không có) giữ nguyên 0.
func snapMinutes(m int) int {
	if m <= 0 {
		return 0
	}
	m = (m + 2) / 5 * 5
	if m < minEstimate {
		return minEstimate
	}
	if m > maxEstimate {
		return maxEstimate
	}
	return m
}
