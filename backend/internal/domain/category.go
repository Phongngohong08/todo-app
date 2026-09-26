package domain

import "context"

// DefaultCategories là các danh mục có sẵn (lưu bằng mã, app hiển thị nhãn tiếng Việt); không lưu vào DB.
var DefaultCategories = []string{string(CategoryPersonal), string(CategoryWork), string(CategoryOther)}

type CategoryRepository interface {
	// List trả về danh mục tự tạo theo thứ tự, gộp cả danh mục đang được task dùng mà chưa có trong danh sách.
	List(ctx context.Context, userID string) ([]string, error)
	// Replace thay toàn bộ danh sách danh mục tự tạo của user.
	Replace(ctx context.Context, userID string, names []string) error
}
