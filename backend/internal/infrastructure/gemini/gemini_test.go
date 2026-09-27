package gemini

import "testing"

func TestParseCoachReply(t *testing.T) {
	r := parseCoachReply("```json\n{\"reply\":\"Thử 15 phút nhé\",\"actions\":[{\"type\":\"ADD_TO_MY_DAY\",\"label\":\"Đưa vào hôm nay\",\"task_ids\":[\"t1\"]}]}\n```")
	if r.Reply != "Thử 15 phút nhé" || len(r.Actions) != 1 || r.Actions[0].TaskIDs[0] != "t1" {
		t.Fatalf("unexpected parse: %+v", r)
	}

	// Model lỡ trả văn bản thường → vẫn hiển thị được, không có hành động
	plain := parseCoachReply("Bạn làm tốt lắm!")
	if plain.Reply != "Bạn làm tốt lắm!" || plain.Actions == nil || len(plain.Actions) != 0 {
		t.Fatalf("plain text should become the reply with no actions, got %+v", plain)
	}
}
