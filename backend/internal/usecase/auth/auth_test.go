package auth

import (
	"context"
	"errors"
	"strings"
	"testing"
	"time"
	"todo-backend/internal/domain"

	"github.com/golang-jwt/jwt/v5"
)

const testSecret = "0123456789abcdef0123456789abcdef"

type mockUserRepo struct {
	byID map[string]*domain.User
}

func newMockUserRepo() *mockUserRepo { return &mockUserRepo{byID: map[string]*domain.User{}} }

func (m *mockUserRepo) Create(ctx context.Context, u *domain.User) error {
	m.byID[u.ID] = u
	return nil
}
func (m *mockUserRepo) GetByID(ctx context.Context, id string) (*domain.User, error) {
	return m.byID[id], nil
}
func (m *mockUserRepo) GetByEmail(ctx context.Context, email string) (*domain.User, error) {
	for _, u := range m.byID {
		if u.Email == email {
			return u, nil
		}
	}
	return nil, nil
}
func (m *mockUserRepo) ListActiveUserIDs(ctx context.Context, since time.Time) ([]string, error) {
	return nil, nil
}
func (m *mockUserRepo) IncrementTokenVersion(ctx context.Context, userID string) error {
	m.byID[userID].TokenVersion++
	return nil
}
func (m *mockUserRepo) GetPreferences(ctx context.Context, userID string) (*domain.UserPreferences, error) {
	return nil, nil
}
func (m *mockUserRepo) UpdatePreferences(ctx context.Context, p *domain.UserPreferences) error {
	return nil
}

func setup(t *testing.T) (*AuthUseCase, *mockUserRepo, *AuthResponse) {
	t.Helper()
	repo := newMockUserRepo()
	uc := NewAuthUseCase(repo, testSecret, 15*time.Minute, time.Hour)
	ctx := context.Background()
	if _, err := uc.Register(ctx, RegisterInput{Email: "a@b.com", Password: "secret123", Name: "A"}); err != nil {
		t.Fatalf("register: %v", err)
	}
	res, err := uc.Login(ctx, LoginInput{Email: "a@b.com", Password: "secret123"})
	if err != nil {
		t.Fatalf("login: %v", err)
	}
	return uc, repo, res
}

func TestRegister_DuplicateEmail(t *testing.T) {
	uc, _, _ := setup(t)
	_, err := uc.Register(context.Background(), RegisterInput{Email: "a@b.com", Password: "secret123", Name: "B"})
	if !errors.Is(err, domain.ErrEmailExists) {
		t.Fatalf("expected ErrEmailExists, got %v", err)
	}
}

func TestRegister_PasswordTooLongForBcrypt(t *testing.T) {
	uc := NewAuthUseCase(newMockUserRepo(), testSecret, 0, 0)
	_, err := uc.Register(context.Background(), RegisterInput{Email: "x@y.com", Password: strings.Repeat("p", 73), Name: "X"})
	var ve *domain.ValidationError
	if !errors.As(err, &ve) {
		t.Fatalf("expected ValidationError, got %v", err)
	}
}

func TestLogin_WrongPassword(t *testing.T) {
	uc, _, _ := setup(t)
	_, err := uc.Login(context.Background(), LoginInput{Email: "a@b.com", Password: "wrong"})
	if !errors.Is(err, domain.ErrInvalidCredentials) {
		t.Fatalf("expected ErrInvalidCredentials, got %v", err)
	}
}

func TestRefresh_ThenLogoutRevokesRefreshToken(t *testing.T) {
	uc, _, res := setup(t)
	ctx := context.Background()

	refreshed, err := uc.Refresh(ctx, res.RefreshToken)
	if err != nil {
		t.Fatalf("refresh before logout should work: %v", err)
	}

	if err := uc.Logout(ctx, refreshed.RefreshToken); err != nil {
		t.Fatalf("logout: %v", err)
	}

	for _, tok := range []string{res.RefreshToken, refreshed.RefreshToken} {
		if _, err := uc.Refresh(ctx, tok); !errors.Is(err, domain.ErrInvalidToken) {
			t.Fatalf("refresh after logout should fail with ErrInvalidToken, got %v", err)
		}
	}

	// Đăng nhập lại phát token mới với version mới → dùng được
	again, err := uc.Login(ctx, LoginInput{Email: "a@b.com", Password: "secret123"})
	if err != nil {
		t.Fatalf("login again: %v", err)
	}
	if _, err := uc.Refresh(ctx, again.RefreshToken); err != nil {
		t.Fatalf("refresh with new token should work: %v", err)
	}
}

func TestLogout_InvalidTokenIsNoop(t *testing.T) {
	uc, _, _ := setup(t)
	if err := uc.Logout(context.Background(), "not-a-jwt"); err != nil {
		t.Fatalf("logout with garbage token should be a no-op, got %v", err)
	}
}

func TestTokenTypesAreNotInterchangeable(t *testing.T) {
	uc, _, res := setup(t)
	if _, err := uc.Refresh(context.Background(), res.Token); !errors.Is(err, domain.ErrInvalidToken) {
		t.Fatalf("access token must not work as refresh token, got %v", err)
	}
	if _, err := uc.VerifyToken(res.RefreshToken); !errors.Is(err, domain.ErrInvalidToken) {
		t.Fatalf("refresh token must not work as access token, got %v", err)
	}
	if _, err := uc.VerifyToken(res.Token); err != nil {
		t.Fatalf("access token should verify: %v", err)
	}
}

func TestRefresh_LegacyTokenWithoutVersionStillValid(t *testing.T) {
	uc, repo, _ := setup(t)
	var userID string
	for id := range repo.byID {
		userID = id
	}
	// Refresh token phát trước khi có claim "ver"
	legacy := jwt.NewWithClaims(jwt.SigningMethodHS256, jwt.MapClaims{
		"sub": userID, "typ": "refresh", "exp": time.Now().Add(time.Hour).Unix(),
	})
	tok, err := legacy.SignedString([]byte(testSecret))
	if err != nil {
		t.Fatal(err)
	}
	if _, err := uc.Refresh(context.Background(), tok); err != nil {
		t.Fatalf("legacy refresh token should remain valid until first logout: %v", err)
	}
}
