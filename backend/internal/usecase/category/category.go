package category

import (
	"context"
	"regexp"
	"strings"
	"todo-backend/internal/domain"
)

const (
	maxCategories = 50
	maxNameLength = 50
)

type CategoryUseCase struct {
	repo     domain.CategoryRepository
	settings domain.ListSettingsRepository
}

func NewCategoryUseCase(repo domain.CategoryRepository, settings domain.ListSettingsRepository) *CategoryUseCase {
	return &CategoryUseCase{repo: repo, settings: settings}
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

const (
	maxListSettings  = 60
	maxSections      = 20
	maxSectionLength = 50
	maxEmojiRunes    = 8
)

var colorPattern = regexp.MustCompile(`^#[0-9A-Fa-f]{6}$`)

// Settings trả về cài đặt hiển thị (màu, emoji, các mục) của các danh sách.
func (u *CategoryUseCase) Settings(ctx context.Context, userID string) ([]domain.ListSettings, error) {
	return u.settings.ListSettings(ctx, userID)
}

// ReplaceSettings chuẩn hóa rồi thay toàn bộ cài đặt danh sách.
func (u *CategoryUseCase) ReplaceSettings(ctx context.Context, userID string, in []domain.ListSettings) ([]domain.ListSettings, error) {
	clean := NormalizeSettings(in)
	if len(clean) > maxListSettings {
		return nil, domain.NewValidationError("Quá nhiều danh sách")
	}
	if err := u.settings.ReplaceSettings(ctx, userID, clean); err != nil {
		return nil, err
	}
	return clean, nil
}

// NormalizeSettings: bỏ tên rỗng/trùng (không phân biệt hoa thường), màu sai định dạng → mặc định,
// emoji quá dài → bỏ, mục trùng/rỗng → bỏ (tối đa 20 mục, mỗi mục 50 ký tự). Danh sách không có gì
// khác mặc định thì không cần lưu.
func NormalizeSettings(in []domain.ListSettings) []domain.ListSettings {
	out := []domain.ListSettings{}
	seen := map[string]bool{}
	for _, s := range in {
		name := strings.TrimSpace(s.Name)
		key := strings.ToLower(name)
		if name == "" || len([]rune(name)) > maxNameLength || seen[key] {
			continue
		}
		seen[key] = true

		color := strings.TrimSpace(s.Color)
		if !colorPattern.MatchString(color) {
			color = ""
		}
		emoji := strings.TrimSpace(s.Emoji)
		if len([]rune(emoji)) > maxEmojiRunes {
			emoji = ""
		}
		sections := []string{}
		seenSection := map[string]bool{}
		for _, sec := range s.Sections {
			sec = strings.TrimSpace(sec)
			if r := []rune(sec); len(r) > maxSectionLength {
				sec = string(r[:maxSectionLength])
			}
			k := strings.ToLower(sec)
			if sec == "" || seenSection[k] {
				continue
			}
			seenSection[k] = true
			sections = append(sections, sec)
			if len(sections) == maxSections {
				break
			}
		}
		if color == "" && emoji == "" && len(sections) == 0 {
			continue
		}
		out = append(out, domain.ListSettings{Name: name, Color: color, Emoji: emoji, Sections: sections})
	}
	return out
}
