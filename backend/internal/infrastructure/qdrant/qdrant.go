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

func NewQdrantClient(host, port string) *QdrantClient {
	return &QdrantClient{
		baseURL:    fmt.Sprintf("http://%s:%s", host, port),
		collection: "user_memories_gemini",
		httpClient: &http.Client{Timeout: 10 * time.Second},
	}
}

// InitCollection creates the collection if it does not exist
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
