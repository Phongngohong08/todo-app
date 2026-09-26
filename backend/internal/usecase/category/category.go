package category

import (
	"context"
	"strings"
	"todo-backend/internal/domain"
)

const (
	maxCategories = 50
	maxNameLength = 50
)

type CategoryUseCase struct {
	repo domain.CategoryRepository
}

func NewCategoryUseCase(repo domain.CategoryRepository) *CategoryUseCase {
	return &CategoryUseCase{repo: repo}
}

func (u *CategoryUseCase) List(ctx context.Context, userID string) ([]string, error) {
	return u.repo.List(ctx, userID)
}

// Replace chuẩn hóa rồi thay toàn bộ danh sách: bỏ khoảng trắng, bỏ trùng (không phân biệt hoa thường),
// bỏ các danh mục mặc định (luôn có sẵn nên không cần lưu).
func (u *CategoryUseCase) Replace(ctx context.Context, userID string, names []string) ([]string, error) {
	clean := Normalize(names)
	if len(clean) > maxCategories {
		return nil, domain.NewValidationError("Tối đa 50 danh mục tự tạo")
	}
	for _, n := range clean {
		if len([]rune(n)) > maxNameLength {
			return nil, domain.NewValidationError("Tên danh mục tối đa 50 ký tự")
		}
	}
	if err := u.repo.Replace(ctx, userID, clean); err != nil {
		return nil, err
	}
	return clean, nil
}

func Normalize(names []string) []string {
	seen := make(map[string]bool)
	for _, d := range domain.DefaultCategories {
		seen[strings.ToLower(d)] = true
	}
	out := []string{}
	for _, n := range names {
		n = strings.TrimSpace(n)
		key := strings.ToLower(n)
		if n == "" || seen[key] {
			continue
		}
		seen[key] = true
		out = append(out, n)
	}
	return out
}
