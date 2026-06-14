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

func NewGeminiClient(ctx context.Context, apiKey string) (*GeminiClient, error) {
	if apiKey == "" {
		return &GeminiClient{
			client: nil,
			model:  "gemini-2.5-flash",
		}, fmt.Errorf("GEMINI_API_KEY environment variable is empty")
	}

	client, err := genai.NewClient(ctx, &genai.ClientConfig{
		APIKey: apiKey,
	})
	if err != nil {
		return &GeminiClient{
			client: nil,
			model:  "gemini-2.5-flash",
		}, fmt.Errorf("failed to create Gemini client: %w", err)
	}

	return &GeminiClient{
		client: client,
		model:  "gemini-2.5-flash",
	}, nil
}

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

// ParseTask tách một câu ngôn ngữ tự nhiên thành task có cấu trúc.
// nowContext là thời điểm hiện tại của người dùng (RFC3339) để suy ra các mốc tương đối ("ngày mai", "thứ 6").
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
- "estimated_duration": integer minutes if implied (e.g. "1 tiếng" = 60), else 0.
- "tags": array of short lowercase tags inferred from context (e.g. ["công việc"], ["sức khỏe"]). Empty array if none.

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
		return nil, fmt.Errorf("gemini parse task error: %w", err)
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

func (c *GeminiClient) GenerateDailyPlan(ctx context.Context, tasks []*domain.Task, prefs *domain.UserPreferences, memories []*domain.MemoryItem, localTime string) ([]domain.PlanSlot, error) {
	if c == nil || c.client == nil {
		return nil, fmt.Errorf("Gemini client is not initialized. Please set a valid GEMINI_API_KEY in your .env file")
	}

	systemPrompt := `You are an AI scheduler. Your job is to output a structured daily schedule in JSON format based on:
1. User active tasks (title, status, duration, priority, due date, preferred_time_start, preferred_time_end).
2. User preferences (morning start time, evening end time, work block size).
3. User habits and history (long-term memories).
4. Current time of day (if provided).

Scheduling Rules:
- If a current local time is provided (e.g., "10:15"), you MUST ONLY schedule tasks starting from or after this time. Do not suggest slots in the past.
- Tasks may have "preferred_time_start" and "preferred_time_end" values (e.g. "17:00" and "20:00" for a workout). You MUST prioritize scheduling these tasks strictly within their preferred windows.
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
		return nil, fmt.Errorf("gemini generate daily plan error: %w", err)
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
		return "", err
	}

	return resp.Text(), nil
}

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
		return nil, err
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
