# Stage 1: Build the binaries
FROM golang:1.26-alpine AS builder

WORKDIR /app

# Install git and certs (if needed)
RUN apk add --no-cache git ca-certificates

# Copy go mod and sum
COPY go.mod go.sum ./
RUN go mod download

# Copy source code
COPY . .

# Build API binary (scheduler chạy ngầm bên trong API)
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-w -s" -o /app/api ./cmd/api

# Stage 2: Final runner image
FROM alpine:latest

WORKDIR /app

# Copy ca-certificates for OpenAI HTTPS requests
COPY --from=builder /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/

# Copy binary from builder
COPY --from=builder /app/api /app/api
COPY --from=builder /app/migrations /app/migrations

# Expose port
EXPOSE 8080

# Default command (will be overridden in docker-compose)
CMD ["/app/api"]
