package domain

// EstimateExample là một việc người dùng đã từng ước tính thời lượng — gửi cho AI làm mẫu để
// gợi ý thời lượng sát với nhịp làm việc của chính người đó.
type EstimateExample struct {
	Title   string `json:"title"`
	Minutes int    `json:"minutes"`
}
