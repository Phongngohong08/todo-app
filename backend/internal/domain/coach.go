package domain

// Loại hành động AI Coach có thể ĐỀ XUẤT. Server không tự thực hiện: app hiện nút xác nhận và áp dụng
// qua luồng offline-first thường ngày (ghi Room → đồng bộ), nên người dùng luôn là người quyết định.
const (
	CoachActionReschedule  = "RESCHEDULE"    // dời task_ids sang due_date
	CoachActionAddSubtasks = "ADD_SUBTASKS"  // thêm subtasks vào task_ids[0]
	CoachActionCreateTask  = "CREATE_TASK"   // tạo việc mới: title, due_date, priority
	CoachActionSetPriority = "SET_PRIORITY"  // đổi độ ưu tiên task_ids sang priority
	CoachActionAddToMyDay  = "ADD_TO_MY_DAY" // đưa task_ids vào "Ngày của tôi" hôm nay
)

// MaxCoachActions giới hạn số đề xuất trong một câu trả lời — nhiều hơn thì người dùng khó xem xét.
const MaxCoachActions = 3

// CoachAction là một thay đổi AI đề xuất kèm nhãn để hiển thị trên nút.
type CoachAction struct {
	Type     string   `json:"type"`
	Label    string   `json:"label"`               // mô tả ngắn cho nút, vd "Dời 3 việc quá hạn sang thứ 2"
	TaskIDs  []string `json:"task_ids,omitempty"`  // chỉ gồm id có thật trong danh sách việc đang mở
	DueDate  string   `json:"due_date,omitempty"`  // RFC3339 (RESCHEDULE, CREATE_TASK)
	AllDay   bool     `json:"all_day,omitempty"`   // due_date chỉ có ngày
	Priority string   `json:"priority,omitempty"`  // LOW | MEDIUM | HIGH
	Title    string   `json:"title,omitempty"`     // CREATE_TASK
	Subtasks []string `json:"subtasks,omitempty"`  // ADD_SUBTASKS
}

// CoachReply là câu trả lời của AI Coach: lời khuyên + (tùy chọn) các hành động đề xuất.
type CoachReply struct {
	Reply   string        `json:"reply"`
	Actions []CoachAction `json:"actions"`
}
