package gemini

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"
	"todo-backend/internal/domain"

	"google.golang.org/genai"
)

type GeminiClient struct {
	client *genai.Client
	model  string
}

// classifyErr bọc lỗi từ Gemini: nếu là 429/hết quota thì gắn domain.ErrAIRateLimited
// để handler trả HTTP 429; các lỗi khác giữ nguyên.
func classifyErr(err error) error {
	if err == nil {
		return nil
	}
	msg := err.Error()
	if strings.Contains(msg, "RESOURCE_EXHAUSTED") || strings.Contains(msg, "Error 429") || strings.Contains(msg, "quota") {
		return fmt.Errorf("%w: %v", domain.ErrAIRateLimited, err)
	}
	return err
}

// DefaultModel là model dùng khi GEMINI_MODEL không được đặt.
const DefaultModel = "gemini-2.5-flash"

// NewGeminiClient khởi tạo client. model rỗng -> dùng DefaultModel.
// Cho phép đổi model qua biến môi trường GEMINI_MODEL mà không cần sửa code
// (vd "gemini-2.5-flash-lite" khi model chính đã hết hạn mức).
func NewGeminiClient(ctx context.Context, apiKey string, model string) (*GeminiClient, error) {
	if model == "" {
		model = DefaultModel
	}
	if apiKey == "" {
		return &GeminiClient{
			client: nil,
			model:  model,
		}, fmt.Errorf("GEMINI_API_KEY environment variable is empty")
	}

	client, err := genai.NewClient(ctx, &genai.ClientConfig{
		APIKey: apiKey,
	})
	if err != nil {
		return &GeminiClient{
			client: nil,
			model:  model,
		}, fmt.Errorf("failed to create Gemini client: %w", err)
	}

	return &GeminiClient{
		client: client,
		model:  model,
	}, nil
}

// CreateEmbedding biến một đoạn text thành "vector ngữ nghĩa" gồm 768 số thực (embedding).
// Hiểu nôm na: vector là "tọa độ" của câu trong không gian nghĩa — 2 câu ý nghĩa giống nhau
// thì 2 vector nằm gần nhau. Đây là bước cốt lõi để lưu & tìm trí nhớ trong vector DB (Qdrant).
//
// Ví dụ:
//
//	text = "Người dùng hay trì hoãn việc viết báo cáo"
//	→ []float32{0.0123, -0.0456, 0.0789, ...}   // đúng 768 phần tử
//	(nếu chưa cấu hình GEMINI_API_KEY → trả lỗi "not initialized")
func (c *GeminiClient) CreateEmbedding(ctx context.Context, text string) ([]float32, error) {
	if c == nil || c.client == nil {
		return nil, fmt.Errorf("Gemini client is not initialized. Please set a valid GEMINI_API_KEY in your .env file")
	}

	contents := []*genai.Content{
		{
			Parts: []*genai.Part{
				{
					Text: text,
				},
			},
		},
	}

	dim := int32(768)
	config := &genai.EmbedContentConfig{
		OutputDimensionality: &dim,
	}

	resp, err := c.client.Models.EmbedContent(ctx, "gemini-embedding-001", contents, config)
	if err != nil {
		return nil, fmt.Errorf("gemini embedding error: %w", err)
	}

	if len(resp.Embeddings) == 0 || len(resp.Embeddings[0].Values) == 0 {
		return nil, fmt.Errorf("gemini returned empty embeddings")
	}

	return resp.Embeddings[0].Values, nil
}

// ParseTask nhờ LLM tách một câu ngôn ngữ tự nhiên thành task có cấu trúc (JSON).
//
// Tham số (lấy một trường hợp cụ thể để dễ hình dung):
//
//	text       = "họp team lúc 3h chiều mai bàn về dự án"   // câu người dùng gõ
//	nowContext = "2026-07-08T09:00:00+07:00"                // thời điểm hiện tại (RFC3339), để suy ra "mai"
//
// Kết quả trả về:
//
//	&domain.ParsedTask{
//	    Title:       "Họp team bàn về dự án",
//	    Description: "",
//	    Priority:    "MEDIUM",
//	    DueDate:     "2026-07-09T15:00:00+07:00",   // "3h chiều mai" đã quy ra mốc tuyệt đối
//	    Category:    "WORK",
//	}
//
// (Temperature 0.2 = ưu tiên ổn định, ít "sáng tạo". Câu trả về được ép JSON qua ResponseMIMEType.)
func (c *GeminiClient) ParseTask(ctx context.Context, text string, nowContext string) (*domain.ParsedTask, error) {
	if c == nil || c.client == nil {
		return nil, fmt.Errorf("Gemini client is not initialized. Please set a valid GEMINI_API_KEY in your .env file")
	}

	systemPrompt := `You convert a user's natural-language note into ONE structured to-do task as JSON.
Extract:
- "title": short imperative title (required).
- "description": any extra detail, else empty string.
- "priority": one of "LOW", "MEDIUM", "HIGH" (infer urgency; default "MEDIUM").
- "due_date": absolute RFC3339 timestamp (e.g. "2026-06-20T15:00:00+07:00") resolved from the provided current time, or null if the note has no time/date. Keep the user's timezone offset from the current time.
- "category": exactly one of "PERSONAL", "WORK", "OTHER" inferred from context (work/meetings/email => WORK; personal life/health/family/hobbies => PERSONAL; otherwise OTHER). Default "OTHER".

Write title/description in the SAME language as the user's note (Vietnamese stays Vietnamese).
Return ONLY a JSON object with exactly these keys. No markdown.`

	userPrompt := fmt.Sprintf("Current time: %s\n\nUser note:\n%s", nowContext, text)

	config := &genai.GenerateContentConfig{
		SystemInstruction: &genai.Content{
			Parts: []*genai.Part{
				{
					Text: systemPrompt,
				},
			},
		},
		ResponseMIMEType: "application/json",
		Temperature:      genai.Ptr[float32](0.2),
	}

	contents := []*genai.Content{
		{
			Parts: []*genai.Part{
				{
					Text: userPrompt,
				},
			},
		},
	}

	resp, err := c.client.Models.GenerateContent(ctx, c.model, contents, config)
	if err != nil {
		return nil, classifyErr(fmt.Errorf("gemini parse task error: %w", err))
	}

	raw := resp.Text()
	var parsed domain.ParsedTask
	if err := json.Unmarshal([]byte(raw), &parsed); err != nil {
		cleaned := stripCodeBlocks(raw)
		if err := json.Unmarshal([]byte(cleaned), &parsed); err != nil {
			return nil, fmt.Errorf("failed to parse Gemini quick-add response: %w. Raw: %s", err, raw)
		}
	}

	if parsed.Title == "" {
		return nil, fmt.Errorf("could not extract a task title from the note")
	}
	return &parsed, nil
}

// GenerateDailyPlan nhờ LLM xếp các task thành lịch trong ngày theo khung giờ, có cân nhắc
// sở thích người dùng và thói quen rút ra từ trí nhớ.
//
// Tham số (một trường hợp minh họa):
//
//	tasks    = [ {Title:"Viết báo cáo", Priority:"HIGH"}, {Title:"Tập gym", Priority:"LOW"} ]
//	prefs    = {MorningStartTime:"08:00", EveningEndTime:"18:00", WorkDurationPreference:60}  // mỗi khối 60'
//	memories = [ {Content:"Người dùng tập trung tốt vào buổi sáng"} ]                          // để AI ưu tiên việc khó buổi sáng
//	localTime = "10:15"   // giờ hiện tại của user → KHÔNG xếp việc trước mốc này
//
// Kết quả trả về (mảng khung giờ):
//
//	[ {Start:"10:15", End:"11:15", TaskID:"...", Title:"Viết báo cáo"},
//	  {Start:"11:15", End:"12:15", TaskID:"...", Title:"Tập gym"} ]
func (c *GeminiClient) GenerateDailyPlan(ctx context.Context, tasks []*domain.Task, prefs *domain.UserPreferences, memories []*domain.MemoryItem, localTime string) ([]domain.PlanSlot, error) {
	if c == nil || c.client == nil {
		return nil, fmt.Errorf("Gemini client is not initialized. Please set a valid GEMINI_API_KEY in your .env file")
	}

	systemPrompt := `You are an AI scheduler. Your job is to output a structured daily schedule in JSON format based on:
1. User active tasks (title, status, priority, due date, category).
2. User preferences (morning start time, evening end time, default work block size in minutes).
3. User habits and history (long-term memories).
4. Current time of day (if provided).

Scheduling Rules:
- Schedule each task into a time block of roughly the user's default work block size (split larger work sensibly).
- Order tasks by priority (HIGH first) and by due date (earlier due dates first).
- Prefer to keep slots within the user's morning start time and evening end time.
- If a current local time is provided (e.g., "10:15"), you MUST ONLY schedule tasks starting from or after this time. Do not suggest slots in the past.
- IMPORTANT: If the current local time is already at or after the user's evening end time, DO NOT return an empty schedule. Instead, still schedule the tasks starting from the current time into the remaining hours of the day (up to a reasonable late cutoff of 23:59). The user explicitly asked for a plan, so always provide one whenever there is at least one active task and any time left before midnight.
- Only return an empty array if there are genuinely no active tasks to schedule, or there is truly no time left before midnight.
- Suggest a logical, realistic daily schedule that avoids overlaps.

Return ONLY a JSON array of slots with this exact structure:
[
  {"start": "HH:MM", "end": "HH:MM", "task_id": "uuid", "title": "Task title"}
]`

	tasksJSON, _ := json.Marshal(tasks)
	prefsJSON, _ := json.Marshal(prefs)
	memoriesJSON, _ := json.Marshal(memories)

	var timeContext string
	if localTime != "" {
		timeContext = fmt.Sprintf("\n- Current Local Time of the User: %s (DO NOT schedule tasks before this time)\n", localTime)
	}

	userPrompt := fmt.Sprintf(
		"Tasks:\n%s\n\nPreferences:\n%s\n\nUser long-term memory/habits:\n%s\n%s\nPlease construct today's daily schedule.",
		tasksJSON, prefsJSON, memoriesJSON, timeContext,
	)

	config := &genai.GenerateContentConfig{
		SystemInstruction: &genai.Content{
			Parts: []*genai.Part{
				{
					Text: systemPrompt,
				},
			},
		},
		ResponseMIMEType: "application/json",
		Temperature:      genai.Ptr[float32](0.2),
	}

	contents := []*genai.Content{
		{
			Parts: []*genai.Part{
				{
					Text: userPrompt,
				},
			},
		},
	}

	resp, err := c.client.Models.GenerateContent(ctx, c.model, contents, config)
	if err != nil {
		return nil, classifyErr(fmt.Errorf("gemini generate daily plan error: %w", err))
	}

	rawContent := resp.Text()
	var slots []domain.PlanSlot
	err = json.Unmarshal([]byte(rawContent), &slots)
	if err != nil {
		cleaned := stripCodeBlocks(rawContent)
		err = json.Unmarshal([]byte(cleaned), &slots)
		if err != nil {
			return nil, fmt.Errorf("failed to parse Gemini daily plan response: %w. Raw response: %s", err, rawContent)
		}
	}

	return slots, nil
}

// GetCoachResponse sinh câu trả lời của "AI Coach" — có ngữ cảnh về task, thói quen và lịch sử chat.
// Đây là bước cuối của luồng RAG: memories đã được tìm sẵn (bằng vector) rồi nhét vào prompt.
//
// Tham số (một trường hợp minh họa):
//
//	message  = "Dạo này tôi thấy nản, không muốn làm gì cả"     // tin nhắn mới của user
//	tasks    = [ {Title:"Ôn thi cuối kỳ", Status:"TODO"} ]      // việc đang mở, để AI nhắc đúng ngữ cảnh
//	memories = [ {Content:"Người dùng hay trì hoãn việc học"} ] // trí nhớ liên quan (tìm từ Qdrant)
//	history  = [ {Role:"user",Content:"..."}, {Role:"assistant",Content:"..."} ]  // 10 tin gần nhất
//
// Kết quả trả về (chuỗi lời khuyên):
//
//	"Mình hiểu cảm giác đó. Mình để ý bạn hay hoãn việc học — thử bắt đầu chỉ 15 phút với môn dễ nhất nhé..."
func (c *GeminiClient) GetCoachResponse(ctx context.Context, message string, tasks []*domain.Task, memories []*domain.MemoryItem, history []*domain.ChatMessage) (string, error) {
	if c == nil || c.client == nil {
		return "", fmt.Errorf("Gemini client is not initialized. Please set a valid GEMINI_API_KEY in your .env file")
	}

	systemPrompt := `You are an empathetic, insightful AI Coach for a To-Do application. Your goal is to guide, motivate, and counsel the user regarding their task completions and habits.
Use the provided user details:
1. Current Active Tasks.
2. Long-Term memories about the user (e.g., their habits, frequent postponements, typical peaks of productivity).
3. Chat history context.

Be direct but encouraging. Refer to their previous history if they have patterns of delaying specific tasks (e.g. "I noticed you postponed documentation tasks 4 times in the past. Try working on it for just 15 minutes today").`

	tasksJSON, _ := json.Marshal(tasks)
	memoriesJSON, _ := json.Marshal(memories)
	contextPrompt := fmt.Sprintf(
		"--- USER CONTEXT ---\nActive Tasks: %s\nUser Habits & Memories: %s\n--------------------",
		tasksJSON, memoriesJSON,
	)

	fullSystemPrompt := systemPrompt + "\n\n" + contextPrompt

	var contents []*genai.Content

	// Prepend history context
	for _, msg := range history {
		role := "user"
		if msg.Role == "assistant" {
			role = "model"
		}
		contents = append(contents, &genai.Content{
			Role: role,
			Parts: []*genai.Part{
				{
					Text: msg.Content,
				},
			},
		})
	}

	// Append the new message
	contents = append(contents, &genai.Content{
		Role: "user",
		Parts: []*genai.Part{
			{
				Text: message,
			},
		},
	})

	config := &genai.GenerateContentConfig{
		SystemInstruction: &genai.Content{
			Parts: []*genai.Part{
				{
					Text: fullSystemPrompt,
				},
			},
		},
		Temperature: genai.Ptr[float32](0.7),
	}

	resp, err := c.client.Models.GenerateContent(ctx, c.model, contents, config)
	if err != nil {
		return "", classifyErr(err)
	}

	return resp.Text(), nil
}

// ExtractMemories nhờ LLM đọc lịch sử hoạt động + chat để rút ra các "thói quen/quan sát" dạng câu ngắn.
// Đây là nguồn sinh ra trí nhớ; sau đó mỗi câu sẽ được embedding và lưu vào Qdrant (xem memory usecase).
//
// Tham số (một trường hợp minh họa):
//
//	logs = [ {Action:"POSTPONED", Details:"Viết báo cáo"},          // các sự kiện: hoãn/hoàn thành/tạo...
//	         {Action:"COMPLETED", Details:"Trả lời email"} ]
//	messages = [ {Role:"user", Content:"Tôi thích làm việc vào buổi sáng"} ]  // vài tin chat gần đây
//
// Kết quả trả về (mảng câu quan sát):
//
//	[ "Người dùng thường trì hoãn việc viết báo cáo",
//	  "Người dùng thích làm việc vào buổi sáng" ]
//	(trả về [] nếu dữ liệu rỗng hoặc không có tín hiệu gì đáng ghi)
func (c *GeminiClient) ExtractMemories(ctx context.Context, logs []*domain.TaskLog, messages []*domain.ChatMessage) ([]string, error) {
	if c == nil || c.client == nil {
		return nil, fmt.Errorf("Gemini client is not initialized. Please set a valid GEMINI_API_KEY in your .env file")
	}

	systemPrompt := `You are a background behavioral analyst. Analyze the following user logs (events, status changes, postponements) and chat messages.
Your objective is to extract useful, durable observations about the user's habits, work style, preferences, or recurring behaviors.
Be generous: even a single clear signal from the data is worth recording (e.g. the kind of tasks they create, when they work, what they postpone or complete, stated preferences from chat). You do NOT need many repetitions to record an observation.
Examples of good extractions:
- "User frequently postpones writing reports."
- "User works best on coding tasks in the early morning."
- "User tends to create high-priority tasks with tight deadlines."
- "User mentioned they prefer focusing in the morning."
- "User completed several short tasks but cancelled a long one."

Write each statement in the same language the user predominantly uses in their chat messages (default to Vietnamese if unsure).
Output a JSON array of strings containing the extracted statements. Only output an empty array [] if the data is truly empty or contains no usable signal whatsoever.
Return ONLY the JSON array.`

	logsJSON, _ := json.Marshal(logs)
	messagesJSON, _ := json.Marshal(messages)
	userPrompt := fmt.Sprintf(
		"Activity Logs:\n%s\n\nRecent Chat Messages:\n%s\n\nPlease extract long-term patterns.",
		logsJSON, messagesJSON,
	)

	config := &genai.GenerateContentConfig{
		SystemInstruction: &genai.Content{
			Parts: []*genai.Part{
				{
					Text: systemPrompt,
				},
			},
		},
		ResponseMIMEType: "application/json",
		Temperature:      genai.Ptr[float32](0.2),
	}

	contents := []*genai.Content{
		{
			Parts: []*genai.Part{
				{
					Text: userPrompt,
				},
			},
		},
	}

	resp, err := c.client.Models.GenerateContent(ctx, c.model, contents, config)
	if err != nil {
		return nil, classifyErr(err)
	}

	raw := resp.Text()
	var insights []string
	err = json.Unmarshal([]byte(raw), &insights)
	if err != nil {
		cleaned := stripCodeBlocks(raw)
		err = json.Unmarshal([]byte(cleaned), &insights)
		if err != nil {
			return nil, fmt.Errorf("failed to parse memory extraction JSON: %w. Raw: %s", err, raw)
		}
	}

	return insights, nil
}

// stripCodeBlocks gỡ hàng rào markdown ```json ... ``` khi LLM lỡ bọc JSON trong code block.
// Ví dụ: s = "```json\n{\"title\":\"X\"}\n```"  →  "{\"title\":\"X\"}"
func stripCodeBlocks(s string) string {
	if len(s) > 7 && s[:7] == "```json" {
		s = s[7:]
	} else if len(s) > 3 && s[:3] == "```" {
		s = s[3:]
	}
	if len(s) > 3 && s[len(s)-3:] == "```" {
		s = s[:len(s)-3]
	}
	return strings.TrimSpace(s)
}
