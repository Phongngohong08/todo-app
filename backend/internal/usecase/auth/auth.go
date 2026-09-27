package auth

import (
	"context"
	"errors"
	"strings"
	"time"
	"todo-backend/internal/domain"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
	"golang.org/x/crypto/bcrypt"
)

const (
	tokenTypeAccess  = "access"
	tokenTypeRefresh = "refresh"

	// bcrypt chỉ dùng 72 byte đầu của mật khẩu (Go trả lỗi nếu dài hơn) — chặn sớm thành lỗi 400.
	maxPasswordBytes = 72
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
	Email    string `json:"email" binding:"required,email,max=255"`
	Password string `json:"password" binding:"required,min=6"`
	Name     string `json:"name" binding:"required,max=100"`
}

type LoginInput struct {
	Email    string `json:"email" binding:"required,email,max=255"`
	Password string `json:"password" binding:"required"`
}

type AuthResponse struct {
	Token        string       `json:"token"`         // access token
	RefreshToken string       `json:"refresh_token"` // dùng để lấy access token mới khi hết hạn
	ExpiresIn    int64        `json:"expires_in"`    // access token TTL, tính bằng giây
	User         *domain.User `json:"user"`
}

func (u *AuthUseCase) Register(ctx context.Context, input RegisterInput) (*domain.User, error) {
	if len(input.Password) > maxPasswordBytes {
		return nil, domain.NewValidationError("Mật khẩu quá dài (tối đa 72 byte)")
	}
	name := strings.TrimSpace(input.Name)
	if name == "" {
		return nil, domain.NewValidationError("Tên không được để trống")
	}

	existing, err := u.userRepo.GetByEmail(ctx, input.Email)
	if err != nil {
		return nil, err
	}
	if existing != nil {
		return nil, domain.ErrEmailExists
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
		Name:         name,
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
		return nil, domain.ErrInvalidCredentials
	}

	err = bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(input.Password))
	if err != nil {
		return nil, domain.ErrInvalidCredentials
	}

	return u.buildAuthResponse(user)
}

// Refresh đổi một refresh token hợp lệ lấy cặp access/refresh token mới (sliding expiration).
func (u *AuthUseCase) Refresh(ctx context.Context, refreshToken string) (*AuthResponse, error) {
	user, _, err := u.userFromRefreshToken(ctx, refreshToken)
	if err != nil {
		return nil, err
	}
	return u.buildAuthResponse(user)
}

// Logout thu hồi refresh token.
//   - allDevices = false: chỉ thu hồi token này (đăng xuất thiết bị đang dùng) — theo jti.
//     Token phát trước khi có jti không thu hồi riêng được → rơi về thu hồi tất cả cho chắc.
//   - allDevices = true:  tăng token_version → mọi refresh token đã phát đều vô hiệu.
//
// Token không hợp lệ/đã hết hạn thì coi như đã đăng xuất (idempotent). Access token đang còn hạn
// vẫn dùng được tới khi hết ACCESS_TOKEN_TTL — vì vậy TTL đó nên ngắn.
func (u *AuthUseCase) Logout(ctx context.Context, refreshToken string, allDevices bool) error {
	user, claims, err := u.userFromRefreshToken(ctx, refreshToken)
	if errors.Is(err, domain.ErrInvalidToken) {
		return nil
	}
	if err != nil {
		return err
	}
	if allDevices || claims.jti == "" {
		return u.userRepo.IncrementTokenVersion(ctx, user.ID)
	}
	return u.userRepo.RevokeRefreshToken(ctx, claims.jti, user.ID, claims.expiresAt)
}

// userFromRefreshToken xác thực refresh token (chữ ký, hạn, loại, version, danh sách thu hồi)
// và trả về user sở hữu.
func (u *AuthUseCase) userFromRefreshToken(ctx context.Context, refreshToken string) (*domain.User, *tokenClaims, error) {
	claims, err := u.parseToken(refreshToken)
	if err != nil {
		return nil, nil, domain.ErrInvalidToken
	}
	if claims.tokenType != tokenTypeRefresh {
		return nil, nil, domain.ErrInvalidToken
	}

	user, err := u.userRepo.GetByID(ctx, claims.userID)
	if err != nil {
		return nil, nil, err
	}
	if user == nil {
		return nil, nil, domain.ErrInvalidToken
	}
	// Token phát trước lần "đăng xuất mọi thiết bị" gần nhất → đã bị thu hồi.
	if claims.version != user.TokenVersion {
		return nil, nil, domain.ErrInvalidToken
	}
	// Thiết bị này đã đăng xuất riêng.
	if claims.jti != "" {
		revoked, err := u.userRepo.IsRefreshTokenRevoked(ctx, claims.jti)
		if err != nil {
			return nil, nil, err
		}
		if revoked {
			return nil, nil, domain.ErrInvalidToken
		}
	}
	return user, claims, nil
}

// buildAuthResponse sinh cặp access + refresh token cho user.
func (u *AuthUseCase) buildAuthResponse(user *domain.User) (*AuthResponse, error) {
	accessToken, err := u.generateToken(user.ID, tokenTypeAccess, u.accessTokenTTL, nil)
	if err != nil {
		return nil, err
	}
	refreshToken, err := u.generateToken(user.ID, tokenTypeRefresh, u.refreshTokenTTL, &user.TokenVersion)
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

// generateToken tạo một JWT HS256 với claim sub (userID), typ (loại token), exp và — với refresh token —
// ver (thu hồi mọi thiết bị) + jti (thu hồi riêng thiết bị này).
func (u *AuthUseCase) generateToken(userID, tokenType string, ttl time.Duration, version *int) (string, error) {
	claims := jwt.MapClaims{
		"sub": userID,
		"typ": tokenType,
		"exp": time.Now().Add(ttl).Unix(),
	}
	if version != nil {
		claims["ver"] = *version
	}
	if tokenType == tokenTypeRefresh {
		claims["jti"] = uuid.New().String()
	}
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	return token.SignedString([]byte(u.jwtSecret))
}

type tokenClaims struct {
	userID    string
	tokenType string
	version   int
	jti       string    // chỉ có ở refresh token phát sau migration 000008
	expiresAt time.Time
}

// parseToken xác thực chữ ký + hạn dùng, trả về các claim cần dùng.
func (u *AuthUseCase) parseToken(tokenString string) (*tokenClaims, error) {
	token, err := jwt.Parse(tokenString, func(t *jwt.Token) (any, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, errors.New("unexpected signing method")
		}
		return []byte(u.jwtSecret), nil
	})
	if err != nil {
		return nil, err
	}

	claims, ok := token.Claims.(jwt.MapClaims)
	if !ok || !token.Valid {
		return nil, errors.New("invalid token")
	}

	userID, ok := claims["sub"].(string)
	if !ok {
		return nil, errors.New("invalid subject claim")
	}
	// typ có thể vắng ở token cũ; coi như access để không phá phiên đang đăng nhập
	tokenType, _ := claims["typ"].(string)
	if tokenType == "" {
		tokenType = tokenTypeAccess
	}
	// ver vắng ở refresh token phát trước khi có cơ chế thu hồi → coi là 0 (khớp token_version mặc định)
	version := 0
	if v, ok := claims["ver"].(float64); ok {
		version = int(v)
	}
	jti, _ := claims["jti"].(string)
	var expiresAt time.Time
	if exp, err := claims.GetExpirationTime(); err == nil && exp != nil {
		expiresAt = exp.Time
	}
	return &tokenClaims{userID: userID, tokenType: tokenType, version: version, jti: jti, expiresAt: expiresAt}, nil
}

// VerifyToken dùng cho middleware: chỉ chấp nhận access token.
func (u *AuthUseCase) VerifyToken(tokenString string) (string, error) {
	claims, err := u.parseToken(tokenString)
	if err != nil {
		return "", domain.ErrInvalidToken
	}
	if claims.tokenType != tokenTypeAccess {
		return "", domain.ErrInvalidToken
	}
	return claims.userID, nil
}
