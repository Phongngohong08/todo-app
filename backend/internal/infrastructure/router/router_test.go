package router

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
	"todo-backend/internal/domain"
	"todo-backend/internal/usecase/auth"
	"todo-backend/internal/usecase/task"

	"github.com/gin-gonic/gin"
)

const testSecret = "0123456789abcdef0123456789abcdef"

type memUserRepo struct {
	users map[string]*domain.User
	prefs map[string]*domain.UserPreferences
}

func (m *memUserRepo) Create(ctx context.Context, u *domain.User) error {
	m.users[u.ID] = u
	return nil
}
func (m *memUserRepo) GetByID(ctx context.Context, id string) (*domain.User, error) {
	return m.users[id], nil
}
func (m *memUserRepo) GetByEmail(ctx context.Context, email string) (*domain.User, error) {
	for _, u := range m.users {
		if u.Email == email {
			return u, nil
		}
	}
	return nil, nil
}
func (m *memUserRepo) ListActiveUserIDs(ctx context.Context, since time.Time) ([]string, error) {
	return nil, nil
}
func (m *memUserRepo) IncrementTokenVersion(ctx context.Context, id string) error {
	m.users[id].TokenVersion++
	return nil
}
func (m *memUserRepo) GetPreferences(ctx context.Context, id string) (*domain.UserPreferences, error) {
	return m.prefs[id], nil
}
func (m *memUserRepo) UpdatePreferences(ctx context.Context, p *domain.UserPreferences) error {
	m.prefs[p.UserID] = p
	return nil
}

type testServer struct {
	engine *gin.Engine
	token  string
	users  *memUserRepo
}

func newTestServer(t *testing.T, opts Options) *testServer {
	t.Helper()
	gin.SetMode(gin.TestMode)

	users := &memUserRepo{users: map[string]*domain.User{}, prefs: map[string]*domain.UserPreferences{}}
	authUC := auth.NewAuthUseCase(users, testSecret, 15*time.Minute, time.Hour)
	ctx := context.Background()
	if _, err := authUC.Register(ctx, auth.RegisterInput{Email: "a@b.com", Password: "secret123", Name: "A"}); err != nil {
		t.Fatal(err)
	}
	res, err := authUC.Login(ctx, auth.LoginInput{Email: "a@b.com", Password: "secret123"})
	if err != nil {
		t.Fatal(err)
	}

	rm := NewRouteManager(Dependencies{Auth: authUC, Tasks: task.NewTaskUseCase(nil, time.UTC), UserRepo: users}, opts)
	engine, err := rm.SetupRouter()
	if err != nil {
		t.Fatal(err)
	}
	return &testServer{engine: engine, token: res.Token, users: users}
}

func (s *testServer) do(method, path string, body any, headers map[string]string) *httptest.ResponseRecorder {
	var buf bytes.Buffer
	if body != nil {
		json.NewEncoder(&buf).Encode(body)
	}
	req := httptest.NewRequest(method, path, &buf)
	req.Header.Set("Content-Type", "application/json")
	for k, v := range headers {
		req.Header.Set(k, v)
	}
	w := httptest.NewRecorder()
	s.engine.ServeHTTP(w, req)
	return w
}

func (s *testServer) authed() map[string]string {
	return map[string]string{"Authorization": "Bearer " + s.token}
}

func TestRespondError_StatusMapping(t *testing.T) {
	gin.SetMode(gin.TestMode)
	cases := []struct {
		err  error
		want int
	}{
		{domain.NewValidationError("bad"), http.StatusBadRequest},
		{domain.ErrTaskNotFound, http.StatusNotFound},
		{domain.ErrEmailExists, http.StatusConflict},
		{domain.ErrInvalidCredentials, http.StatusUnauthorized},
		{domain.ErrInvalidToken, http.StatusUnauthorized},
		{fmt.Errorf("wrap: %w", domain.ErrAIRateLimited), http.StatusTooManyRequests},
		{fmt.Errorf("wrap: %w", domain.ErrAIUnavailable), http.StatusServiceUnavailable},
		{fmt.Errorf("gemini: %w", context.DeadlineExceeded), http.StatusGatewayTimeout},
		{errors.New(`pq: relation "users" does not exist`), http.StatusInternalServerError},
	}
	for _, tc := range cases {
		w := httptest.NewRecorder()
		c, _ := gin.CreateTestContext(w)
		c.Request = httptest.NewRequest(http.MethodGet, "/x", nil)
		respondError(c, tc.err)
		if w.Code != tc.want {
			t.Errorf("%v: got %d, want %d", tc.err, w.Code, tc.want)
		}
		if tc.want == http.StatusInternalServerError && bytes.Contains(w.Body.Bytes(), []byte("relation")) {
			t.Error("internal error details must not leak to the client")
		}
	}
}

func TestLogin_RateLimitedPerIP(t *testing.T) {
	s := newTestServer(t, Options{})
	body := map[string]string{"email": "a@b.com", "password": "wrong-password"}

	for i := 0; i < 5; i++ {
		if w := s.do(http.MethodPost, "/api/v1/auth/login", body, nil); w.Code != http.StatusUnauthorized {
			t.Fatalf("attempt %d: got %d, want 401", i+1, w.Code)
		}
	}
	w := s.do(http.MethodPost, "/api/v1/auth/login", body, nil)
	if w.Code != http.StatusTooManyRequests {
		t.Fatalf("6th attempt: got %d, want 429", w.Code)
	}
	if w.Header().Get("Retry-After") == "" {
		t.Fatal("429 response must include Retry-After")
	}
}

func TestRegister_DuplicateEmailReturns409(t *testing.T) {
	s := newTestServer(t, Options{})
	w := s.do(http.MethodPost, "/api/v1/auth/register",
		map[string]string{"email": "a@b.com", "password": "secret123", "name": "B"}, nil)
	if w.Code != http.StatusConflict {
		t.Fatalf("got %d, want 409", w.Code)
	}
}

func TestCORS_DisabledByDefault(t *testing.T) {
	s := newTestServer(t, Options{})
	w := s.do(http.MethodGet, "/healthz", nil, map[string]string{"Origin": "https://evil.example"})
	if got := w.Header().Get("Access-Control-Allow-Origin"); got != "" {
		t.Fatalf("CORS header should be absent, got %q", got)
	}
}

func TestCORS_AllowsOnlyConfiguredOrigins(t *testing.T) {
	s := newTestServer(t, Options{CORSAllowedOrigins: []string{"https://app.example"}})

	w := s.do(http.MethodOptions, "/api/v1/tasks", nil, map[string]string{"Origin": "https://app.example"})
	if w.Code != http.StatusNoContent || w.Header().Get("Access-Control-Allow-Origin") != "https://app.example" {
		t.Fatalf("allowed origin preflight: code %d, header %q", w.Code, w.Header().Get("Access-Control-Allow-Origin"))
	}
	if w.Header().Get("Access-Control-Allow-Credentials") != "" {
		t.Fatal("credentials must not be allowed")
	}

	w = s.do(http.MethodGet, "/healthz", nil, map[string]string{"Origin": "https://evil.example"})
	if w.Header().Get("Access-Control-Allow-Origin") != "" {
		t.Fatal("unlisted origin must not receive CORS headers")
	}
}

func TestProtectedRoutes(t *testing.T) {
	s := newTestServer(t, Options{})

	if w := s.do(http.MethodGet, "/api/v1/tasks/abc", nil, nil); w.Code != http.StatusUnauthorized {
		t.Fatalf("missing token: got %d, want 401", w.Code)
	}
	// id không phải UUID → 404 thay vì lỗi cú pháp SQL (500)
	if w := s.do(http.MethodGet, "/api/v1/tasks/not-a-uuid", nil, s.authed()); w.Code != http.StatusNotFound {
		t.Fatalf("invalid id: got %d, want 404", w.Code)
	}
	if w := s.do(http.MethodGet, "/api/v1/tasks?status=DONE", nil, s.authed()); w.Code != http.StatusBadRequest {
		t.Fatalf("invalid status filter: got %d, want 400", w.Code)
	}
}

func TestUpdatePreferences_Validation(t *testing.T) {
	s := newTestServer(t, Options{})
	put := func(morning, evening string, duration int) int {
		return s.do(http.MethodPut, "/api/v1/preferences", map[string]any{
			"morning_start_time":       morning,
			"evening_end_time":         evening,
			"work_duration_preference": duration,
		}, s.authed()).Code
	}

	if code := put("18:00", "08:00", 60); code != http.StatusBadRequest {
		t.Fatalf("end before start: got %d, want 400", code)
	}
	if code := put("8h", "18:00", 60); code != http.StatusBadRequest {
		t.Fatalf("bad format: got %d, want 400", code)
	}
	if code := put("08:00", "18:00", 5); code != http.StatusBadRequest {
		t.Fatalf("duration too small: got %d, want 400", code)
	}
	if code := put("8:00", "18:00", 60); code != http.StatusOK {
		t.Fatalf("valid prefs: got %d, want 200", code)
	}
	for _, p := range s.users.prefs {
		if p.MorningStartTime != "08:00" {
			t.Fatalf("time should be normalized to HH:mm, got %q", p.MorningStartTime)
		}
	}
}

func TestLogout_RevokesRefreshToken(t *testing.T) {
	s := newTestServer(t, Options{})
	w := s.do(http.MethodPost, "/api/v1/auth/login", map[string]string{"email": "a@b.com", "password": "secret123"}, nil)
	var res auth.AuthResponse
	if err := json.Unmarshal(w.Body.Bytes(), &res); err != nil {
		t.Fatal(err)
	}

	if w := s.do(http.MethodPost, "/api/v1/auth/logout", map[string]string{"refresh_token": res.RefreshToken}, nil); w.Code != http.StatusNoContent {
		t.Fatalf("logout: got %d, want 204", w.Code)
	}
	if w := s.do(http.MethodPost, "/api/v1/auth/refresh", map[string]string{"refresh_token": res.RefreshToken}, nil); w.Code != http.StatusUnauthorized {
		t.Fatalf("refresh after logout: got %d, want 401", w.Code)
	}
}
