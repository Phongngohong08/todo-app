package category

import (
	"reflect"
	"testing"
	"todo-backend/internal/domain"
)

func TestNormalizeSettings(t *testing.T) {
	got := NormalizeSettings([]domain.ListSettings{
		{Name: " WORK ", Color: "#4C6EF5", Emoji: "💼", Sections: []string{" Cần làm ", "cần làm", "", "Đang làm"}},
		{Name: "work", Color: "#000000"},                  // trùng tên
		{Name: "Học tập", Color: "red", Emoji: "📚"},      // màu sai → bỏ màu
		{Name: "Trống", Color: "", Emoji: ""},             // không có gì khác mặc định → không lưu
		{Name: "", Color: "#123456"},                      // tên rỗng
	})
	want := []domain.ListSettings{
		{Name: "WORK", Color: "#4C6EF5", Emoji: "💼", Sections: []string{"Cần làm", "Đang làm"}},
		{Name: "Học tập", Color: "", Emoji: "📚", Sections: []string{}},
	}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("got %+v\nwant %+v", got, want)
	}
}
