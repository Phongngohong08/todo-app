package qdrant

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"time"
	"todo-backend/internal/domain"
)

type QdrantClient struct {
	baseURL    string
	collection string
	httpClient *http.Client
}

// NewQdrantClient tạo client trỏ tới Qdrant (vector database) qua REST.
// "collection" giống một cái "bảng" chứa các vector — ở đây tên cố định "user_memories_gemini".
//
// Ví dụ: NewQdrantClient("localhost", "6333")  → gọi API tại http://localhost:6333
func NewQdrantClient(host, port string) *QdrantClient {
	return &QdrantClient{
		baseURL:    fmt.Sprintf("http://%s:%s", host, port),
		collection: "user_memories_gemini",
		httpClient: &http.Client{Timeout: 10 * time.Second},
	}
}

// InitCollection tạo collection nếu chưa có (gọi một lần lúc khởi động server).
// Khai báo mỗi vector dài 768 số và đo độ giống bằng "Cosine" (cùng hướng = giống nghĩa).
// Hai con số này PHẢI khớp với embedding của Gemini (cũng 768 chiều), nếu lệch sẽ lỗi khi lưu/tìm.
//
// Output: nil nếu collection đã sẵn sàng (dù mới tạo hay đã tồn tại); error nếu không kết nối được Qdrant.
func (c *QdrantClient) InitCollection(ctx context.Context) error {
	url := fmt.Sprintf("%s/collections/%s", c.baseURL, c.collection)

	// Check if exists
	req, err := http.NewRequestWithContext(ctx, "GET", url, nil)
	if err != nil {
		return err
	}
	resp, err := c.httpClient.Do(req)
	if err == nil {
		resp.Body.Close()
		if resp.StatusCode == http.StatusOK {
			return nil // Already exists
		}
	}

	// Create collection
	createPayload := map[string]interface{}{
		"vectors": map[string]interface{}{
			"size":     768, // Gemini embedding dimension
			"distance": "Cosine",
		},
	}
	bodyBytes, _ := json.Marshal(createPayload)

	req, err = http.NewRequestWithContext(ctx, "PUT", url, bytes.NewBuffer(bodyBytes))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err = c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("qdrant connect error: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("failed to create qdrant collection, status: %d", resp.StatusCode)
	}

	return nil
}

type qdrantPoint struct {
	ID      string                 `json:"id"`
	Vector  []float32              `json:"vector"`
	Payload map[string]interface{} `json:"payload"`
}

// Save ghi (upsert) một trí nhớ vào Qdrant: lưu vector + kèm "payload" (dữ liệu gốc để đọc lại sau).
// Một điểm (point) gồm: id, vector (768 số) và payload (user_id, content, ...).
//
// Ví dụ input:
//
//	item = {ID:"a1b2", UserID:"u1", Content:"Người dùng thích buổi sáng",
//	        Vector:[]float32{0.01, -0.04, ...}(768 số), MemoryType:"observation"}
//	→ lưu 1 điểm vào collection; wait=true nghĩa là ghi xong mới trả về (đọc lại thấy ngay).
func (c *QdrantClient) Save(ctx context.Context, item *domain.MemoryItem) error {
	url := fmt.Sprintf("%s/collections/%s/points?wait=true", c.baseURL, c.collection)

	point := qdrantPoint{
		ID:     item.ID,
		Vector: item.Vector,
		Payload: map[string]interface{}{
			"user_id":     item.UserID,
			"memory_type": item.MemoryType,
			"content":     item.Content,
			"source":      item.Source,
			"created_at":  item.CreatedAt.Unix(),
		},
	}

	payload := map[string]interface{}{
		"points": []qdrantPoint{point},
	}
	bodyBytes, _ := json.Marshal(payload)

	req, err := http.NewRequestWithContext(ctx, "PUT", url, bytes.NewBuffer(bodyBytes))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("failed to upsert point to qdrant, status: %d", resp.StatusCode)
	}

	return nil
}

type qdrantSearchQuery struct {
	Vector      []float32              `json:"vector"`
	Limit       int                    `json:"limit"`
	WithPayload bool                   `json:"with_payload"`
	Filter      map[string]interface{} `json:"filter"`
}

type qdrantSearchResult struct {
	Result []struct {
		ID      string                 `json:"id"`
		Score   float64                `json:"score"`
		Payload map[string]interface{} `json:"payload"`
	} `json:"result"`
}

// Search tìm các trí nhớ CÓ NGHĨA GIỐNG NHẤT với vector truy vấn (đây là trái tim của RAG).
// Chỉ tìm trong trí nhớ của đúng user (Filter theo user_id), sắp theo độ giống giảm dần.
//
// Tham số (một trường hợp minh họa):
//
//	userID = "u1"
//	vector = embedding("Tôi thấy nản, hay trì hoãn")   // vector của câu người dùng vừa hỏi
//	limit  = 3                                          // lấy 3 trí nhớ liên quan nhất
//
// Kết quả trả về (giống nhất xếp trước):
//
//	[ {Content:"Người dùng hay trì hoãn việc học"}, {Content:"..."}, {Content:"..."} ]
func (c *QdrantClient) Search(ctx context.Context, userID string, vector []float32, limit int) ([]*domain.MemoryItem, error) {
	url := fmt.Sprintf("%s/collections/%s/points/search", c.baseURL, c.collection)

	searchReq := qdrantSearchQuery{
		Vector:      vector,
		Limit:       limit,
		WithPayload: true,
		Filter: map[string]interface{}{
			"must": []map[string]interface{}{
				{
					"key": "user_id",
					"match": map[string]interface{}{
						"value": userID,
					},
				},
			},
		},
	}
	bodyBytes, _ := json.Marshal(searchReq)

	req, err := http.NewRequestWithContext(ctx, "POST", url, bytes.NewBuffer(bodyBytes))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("qdrant search failed, status: %d", resp.StatusCode)
	}

	var searchRes qdrantSearchResult
	if err := json.NewDecoder(resp.Body).Decode(&searchRes); err != nil {
		return nil, err
	}

	var memories []*domain.MemoryItem
	for _, item := range searchRes.Result {
		memories = append(memories, mapPayloadToMemory(item.ID, item.Payload))
	}

	return memories, nil
}

// MaxSimilarity trả về ĐIỂM GIỐNG CAO NHẤT (thang Cosine 0..1) giữa vector và trí nhớ hiện có của user.
// Dùng để KHỬ TRÙNG: trước khi lưu một quan sát mới, kiểm tra xem đã có cái gần giống chưa.
//
// Tham số (một trường hợp minh họa):
//
//	userID = "u1"
//	vector = embedding("Người dùng thích làm việc buổi sáng")
//
// Kết quả trả về:
//
//	0.93  → đã có trí nhớ rất giống (thường ≥ 0.90 sẽ bị coi là trùng và bỏ qua)
//	0.42  → khác biệt, nên lưu
//	0     → user chưa có trí nhớ nào
func (c *QdrantClient) MaxSimilarity(ctx context.Context, userID string, vector []float32) (float64, error) {
	url := fmt.Sprintf("%s/collections/%s/points/search", c.baseURL, c.collection)

	searchReq := qdrantSearchQuery{
		Vector:      vector,
		Limit:       1,
		WithPayload: false,
		Filter: map[string]interface{}{
			"must": []map[string]interface{}{
				{
					"key": "user_id",
					"match": map[string]interface{}{
						"value": userID,
					},
				},
			},
		},
	}
	bodyBytes, _ := json.Marshal(searchReq)

	req, err := http.NewRequestWithContext(ctx, "POST", url, bytes.NewBuffer(bodyBytes))
	if err != nil {
		return 0, err
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return 0, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return 0, fmt.Errorf("qdrant similarity search failed, status: %d", resp.StatusCode)
	}

	var searchRes qdrantSearchResult
	if err := json.NewDecoder(resp.Body).Decode(&searchRes); err != nil {
		return 0, err
	}

	if len(searchRes.Result) == 0 {
		return 0, nil
	}
	return searchRes.Result[0].Score, nil
}

// Delete xóa một trí nhớ theo id.
// Ví dụ: Delete(ctx, "a1b2")  → gỡ điểm "a1b2" khỏi collection.
func (c *QdrantClient) Delete(ctx context.Context, id string) error {
	url := fmt.Sprintf("%s/collections/%s/points/delete?wait=true", c.baseURL, c.collection)

	payload := map[string]interface{}{
		"points": []string{id},
	}
	bodyBytes, _ := json.Marshal(payload)

	req, err := http.NewRequestWithContext(ctx, "POST", url, bytes.NewBuffer(bodyBytes))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("failed to delete point from qdrant, status: %d", resp.StatusCode)
	}

	return nil
}

type qdrantScrollQuery struct {
	Filter      map[string]interface{} `json:"filter"`
	Limit       int                    `json:"limit"`
	WithPayload bool                   `json:"with_payload"`
}

type qdrantScrollResult struct {
	Result struct {
		Points []struct {
			ID      string                 `json:"id"`
			Payload map[string]interface{} `json:"payload"`
		} `json:"points"`
	} `json:"result"`
}

// List lấy TẤT CẢ trí nhớ của một user (tối đa 100), KHÔNG cần vector truy vấn — khác Search ở chỗ
// không xếp theo độ giống mà chỉ "cuộn" (scroll) toàn bộ. Dùng cho màn hiển thị danh sách trí nhớ
// và cho việc lập kế hoạch ngày (nạp mọi thói quen).
//
// Ví dụ: List(ctx, "u1")  → [ {Content:"..."}, {Content:"..."}, ... ]  (mọi trí nhớ của u1)
func (c *QdrantClient) List(ctx context.Context, userID string) ([]*domain.MemoryItem, error) {
	url := fmt.Sprintf("%s/collections/%s/points/scroll", c.baseURL, c.collection)

	scrollReq := qdrantScrollQuery{
		Limit:       100,
		WithPayload: true,
		Filter: map[string]interface{}{
			"must": []map[string]interface{}{
				{
					"key": "user_id",
					"match": map[string]interface{}{
						"value": userID,
					},
				},
			},
		},
	}
	bodyBytes, _ := json.Marshal(scrollReq)

	req, err := http.NewRequestWithContext(ctx, "POST", url, bytes.NewBuffer(bodyBytes))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("qdrant scroll failed, status: %d", resp.StatusCode)
	}

	var scrollRes qdrantScrollResult
	if err := json.NewDecoder(resp.Body).Decode(&scrollRes); err != nil {
		return nil, err
	}

	var memories []*domain.MemoryItem
	for _, item := range scrollRes.Result.Points {
		memories = append(memories, mapPayloadToMemory(item.ID, item.Payload))
	}

	return memories, nil
}

// mapPayloadToMemory chuyển "payload" (map JSON thô Qdrant trả về) thành struct MemoryItem gọn gàng.
// Ví dụ:
//
//	id = "a1b2", payload = {"user_id":"u1","content":"...","memory_type":"observation","created_at":1.72e9}
//	→ &MemoryItem{ID:"a1b2", UserID:"u1", Content:"...", MemoryType:"observation", CreatedAt: <time>}
//
// (Vector không đọc lại vì hiển thị/đọc không cần tới; ép kiểu an toàn bằng ", ok".)
func mapPayloadToMemory(id string, payload map[string]interface{}) *domain.MemoryItem {
	var mType, content, source string
	var uID string
	var createdAt int64

	if v, ok := payload["user_id"].(string); ok {
		uID = v
	}
	if v, ok := payload["memory_type"].(string); ok {
		mType = v
	}
	if v, ok := payload["content"].(string); ok {
		content = v
	}
	if v, ok := payload["source"].(string); ok {
		source = v
	}
	if v, ok := payload["created_at"].(float64); ok {
		createdAt = int64(v)
	}

	return &domain.MemoryItem{
		ID:         id,
		UserID:     uID,
		MemoryType: mType,
		Content:    content,
		Source:     source,
		CreatedAt:  time.Unix(createdAt, 0),
	}
}
