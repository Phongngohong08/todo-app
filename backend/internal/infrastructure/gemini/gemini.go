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
Your objective is to extract long-term behavioral patterns, habits, or strong preferences.
Examples of good extractions:
- "User frequently postpones writing reports."
- "User works best on coding tasks in the early morning."
- "User tends to complete short tasks successfully but struggles with tasks exceeding 2 hours."
- "User tends to feel unmotivated on Friday afternoons."

Output a JSON array of strings containing the extracted statements. If no meaningful behavioral pattern is found, output an empty array: [].
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
