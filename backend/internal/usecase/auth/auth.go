package auth

import (
	"context"
	"errors"
	"time"
	"todo-backend/internal/domain"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
	"golang.org/x/crypto/bcrypt"
)

const (
	tokenTypeAccess  = "access"
	tokenTypeRefresh = "refresh"
)

type AuthUseCase struct {
	userRepo        domain.UserRepository
	jwtSecret       string
	accessTokenTTL  time.Duration
	refreshTokenTTL time.Duration
}

func NewAuthUseCase(userRepo domain.UserRepository, jwtSecret string, accessTokenTTL, refreshTokenTTL time.Duration) *AuthUseCase {
	// Fallback an toàn nếu cấu hình TTL bị bỏ trống
	if accessTokenTTL <= 0 {
		accessTokenTTL = 15 * time.Minute
	}
	if refreshTokenTTL <= 0 {
		refreshTokenTTL = 30 * 24 * time.Hour
	}
	return &AuthUseCase{
		userRepo:        userRepo,
		jwtSecret:       jwtSecret,
		accessTokenTTL:  accessTokenTTL,
		refreshTokenTTL: refreshTokenTTL,
	}
}

type RegisterInput struct {
	Email    string `json:"email" binding:"required,email"`
	Password string `json:"password" binding:"required,min=6"`
	Name     string `json:"name" binding:"required"`
}

type LoginInput struct {
	Email    string `json:"email" binding:"required,email"`
	Password string `json:"password" binding:"required"`
}

type AuthResponse struct {
	Token        string       `json:"token"`         // access token
	RefreshToken string       `json:"refresh_token"` // dùng để lấy access token mới khi hết hạn
	ExpiresIn    int64        `json:"expires_in"`    // access token TTL, tính bằng giây
	User         *domain.User `json:"user"`
}

func (u *AuthUseCase) Register(ctx context.Context, input RegisterInput) (*domain.User, error) {
	existing, err := u.userRepo.GetByEmail(ctx, input.Email)
	if err != nil {
		return nil, err
	}
	if existing != nil {
		return nil, errors.New("email already exists")
	}

	hashedPassword, err := bcrypt.GenerateFromPassword([]byte(input.Password), bcrypt.DefaultCost)
	if err != nil {
		return nil, err
	}

	now := time.Now()
	user := &domain.User{
		ID:           uuid.New().String(),
		Email:        input.Email,
		PasswordHash: string(hashedPassword),
		Name:         input.Name,
		CreatedAt:    now,
		UpdatedAt:    now,
	}

	err = u.userRepo.Create(ctx, user)
	if err != nil {
		return nil, err
	}

	return user, nil
}

func (u *AuthUseCase) Login(ctx context.Context, input LoginInput) (*AuthResponse, error) {
	user, err := u.userRepo.GetByEmail(ctx, input.Email)
	if err != nil {
		return nil, err
	}
	if user == nil {
		return nil, errors.New("invalid email or password")
	}

	err = bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(input.Password))
	if err != nil {
		return nil, errors.New("invalid email or password")
	}

	return u.buildAuthResponse(user)
}

// Refresh đổi một refresh token hợp lệ lấy cặp access/refresh token mới (sliding expiration).
func (u *AuthUseCase) Refresh(ctx context.Context, refreshToken string) (*AuthResponse, error) {
	userID, tokenType, err := u.parseToken(refreshToken)
	if err != nil {
		return nil, err
	}
	if tokenType != tokenTypeRefresh {
		return nil, errors.New("not a refresh token")
	}

	user, err := u.userRepo.GetByID(ctx, userID)
	if err != nil {
		return nil, err
	}
	if user == nil {
		return nil, errors.New("user no longer exists")
	}

	return u.buildAuthResponse(user)
}

// buildAuthResponse sinh cặp access + refresh token cho user.
func (u *AuthUseCase) buildAuthResponse(user *domain.User) (*AuthResponse, error) {
	accessToken, err := u.generateToken(user.ID, tokenTypeAccess, u.accessTokenTTL)
	if err != nil {
		return nil, err
	}
	refreshToken, err := u.generateToken(user.ID, tokenTypeRefresh, u.refreshTokenTTL)
	if err != nil {
		return nil, err
	}

	return &AuthResponse{
		Token:        accessToken,
		RefreshToken: refreshToken,
		ExpiresIn:    int64(u.accessTokenTTL.Seconds()),
		User:         user,
	}, nil
}

// generateToken tạo một JWT HS256 với claim sub (userID), typ (loại token) và exp.
func (u *AuthUseCase) generateToken(userID, tokenType string, ttl time.Duration) (string, error) {
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, jwt.MapClaims{
		"sub": userID,
		"typ": tokenType,
		"exp": time.Now().Add(ttl).Unix(),
	})
	return token.SignedString([]byte(u.jwtSecret))
}

// parseToken xác thực chữ ký + hạn dùng, trả về userID và loại token.
func (u *AuthUseCase) parseToken(tokenString string) (string, string, error) {
	token, err := jwt.Parse(tokenString, func(t *jwt.Token) (any, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, errors.New("unexpected signing method")
		}
		return []byte(u.jwtSecret), nil
	})
	if err != nil {
		return "", "", err
	}

	claims, ok := token.Claims.(jwt.MapClaims)
	if !ok || !token.Valid {
		return "", "", errors.New("invalid token")
	}

	userID, ok := claims["sub"].(string)
	if !ok {
		return "", "", errors.New("invalid subject claim")
	}
	// typ có thể vắng ở token cũ; coi như access để không phá phiên đang đăng nhập
	tokenType, _ := claims["typ"].(string)
	if tokenType == "" {
		tokenType = tokenTypeAccess
	}
	return userID, tokenType, nil
}

// VerifyToken dùng cho middleware: chỉ chấp nhận access token.
func (u *AuthUseCase) VerifyToken(tokenString string) (string, error) {
	userID, tokenType, err := u.parseToken(tokenString)
	if err != nil {
		return "", err
	}
	if tokenType != tokenTypeAccess {
		return "", errors.New("not an access token")
	}
	return userID, nil
}
