# Backend cho Ứng dụng Quản lý Công việc Hỗ trợ bởi AI (AI-Powered To-Do List)

Đây là dịch vụ backend viết bằng ngôn ngữ Golang cho ứng dụng Quản lý Công việc (To-Do List) tích hợp trí tuệ nhân tạo. Dự án được xây dựng theo mô hình **Clean Architecture** và các mô hình thiết kế hướng tên miền **Domain-Driven Design (DDD)**, kết hợp cơ sở dữ liệu quan hệ (PostgreSQL), cơ sở dữ liệu vector (Qdrant) và Google Gemini API nhằm cung cấp thời gian biểu cá nhân hóa và trợ lý AI Coach có bộ nhớ dài hạn.

---

## 🌟 Các Tính năng Nổi bật

1. **Quản lý Công việc (CRUD)**: Tạo mới, xem, cập nhật, xóa, bắt đầu, hoàn thành và hoãn các công việc.
2. **Theo dõi Hoạt động (Activity Logging)**: Tự động lưu vết toàn bộ hành vi của người dùng (tạo task, bắt đầu task, hoàn thành task, hoãn task) làm dữ liệu phân tích thói quen.
3. **Lập Kế hoạch AI Hàng ngày (Daily AI Planning)**: Tự động chạy ngầm vào lúc `04:00 AM` hàng ngày để tạo lịch trình tối ưu dựa trên danh sách việc chưa hoàn thành, thứ tự ưu tiên, cài đặt giờ giấc cá nhân và phân tích thói quen lưu trong bộ nhớ dài hạn.
4. **Trợ lý AI Coach**: Một chatbot tư vấn và tạo động lực cho người dùng. AI Coach sẽ tự động lấy các thông tin về thói quen cũ (ví dụ: thường xuyên hoãn việc viết báo cáo) từ cơ sở dữ liệu Vector để đưa ra lời khuyên thiết thực.
5. **Trích xuất Bộ nhớ Dài hạn (Long-Term Memory Extraction)**: Một tiến trình chạy ngầm vào lúc `01:00 AM` hàng đêm để phân tích lịch sử hoạt động và các tin nhắn chat trong ngày của người dùng, tự động trích xuất các thói quen/hành vi hữu ích, tạo vector nhúng (embeddings) và lưu trữ vào Qdrant.
6. **Thống kê & Phân tích Hoãn việc**: Cung cấp báo cáo về tỷ lệ hoàn thành công việc và thống kê chi tiết lý do trì hoãn.

---

## 🏗️ Kiến trúc Thư mục Dự án

```
/backend
  /cmd
    /api              # Điểm khởi chạy API Gateway RESTful
    /worker           # Điểm khởi chạy tiến trình chạy ngầm (scheduler worker)
  /internal
    /domain           # Định nghĩa thực thể (Entities), giá trị (Value Objects) và giao diện lưu trữ (Repository Interfaces)
    /usecase          # Hiện thực hóa các nghiệp vụ chính (Auth, Task, Plan, Coach, Memory)
    /infrastructure   # Triển khai thư viện bên thứ ba, database driver và cấu hình hệ thống
      /db             # Kết nối PostgreSQL và các truy vấn SQL Repository
      /qdrant         # Kết nối Qdrant và các API lưu trữ/tìm kiếm Vector
      /gemini         # Trình kết nối Google Gemini (Embeddings & Chat Completion)
      /router         # Định nghĩa các Gin Endpoint HTTP và Middleware JWT
      /worker         # Thiết lập bộ lập lịch tác vụ chạy ngầm (scheduler)
  /migrations         # Các tệp SQL di trú cơ sở dữ liệu (Database Schema Migrations)
```

---

## ⚙️ Cấu hình & Biến môi trường

Tạo một tệp tin `.env` trong thư mục gốc của dự án (hoặc thiết lập trực tiếp trong hệ điều hành) với các giá trị sau:

| Tên biến | Mô tả | Giá trị mặc định |
| :--- | :--- | :--- |
| `PORT` | Cổng dịch vụ của API Server | `8080` |
| `DB_HOST` | Địa chỉ máy chủ PostgreSQL | `localhost` |
| `DB_PORT` | Cổng máy chủ PostgreSQL | `5432` |
| `DB_USER` | Tên đăng nhập PostgreSQL | `postgres` |
| `DB_PASSWORD`| Mật khẩu PostgreSQL | `postgrespassword` |
| `DB_NAME` | Tên cơ sở dữ liệu PostgreSQL | `todo_db` |
| `QDRANT_HOST` | Địa chỉ máy chủ Vector DB Qdrant | `localhost` |
| `QDRANT_PORT` | Cổng dịch vụ Qdrant | `6333` |
| `GEMINI_API_KEY`| Khóa bí mật Google Gemini (Bắt buộc đối với các tính năng AI) | *Không có* |
| `JWT_SECRET` | Khóa bí mật dùng để mã hóa mã đăng nhập JWT | *super_secret_key_change_me* |

---

## 🚀 Hướng dẫn Cài đặt & Chạy ứng dụng

### 1. Khởi động Cơ sở hạ tầng Database
Khởi chạy PostgreSQL và Qdrant local bằng Docker Compose:
```bash
docker compose up -d
```

### 2. Khởi tạo Cấu trúc Bảng dữ liệu (Migrations)
Áp dụng tệp SQL nằm trong thư mục `migrations/000001_init.up.sql` vào cơ sở dữ liệu PostgreSQL của bạn.
Ví dụ sử dụng Docker CLI (không cần cài psql trên máy):
```bash
docker cp migrations/000001_init.up.sql todo-postgres:/tmp/init.sql
docker exec -i todo-postgres psql -U postgres -d todo_db -f /tmp/init.sql
```

### 3. Cài đặt các thư viện Go Dependencies
```bash
go mod tidy
```

### 4. Chạy API Gateway Server
```bash
go run ./cmd/api/main.go
```
Dịch vụ REST API sẽ chạy tại `http://localhost:8080/api/v1`.

### 5. Chạy Background Worker (Scheduler)
```bash
go run ./cmd/worker/main.go
```
Tiến trình worker sẽ chạy ở chế độ foreground để lên lịch các tác vụ chạy ngầm hàng ngày (tự động lập kế hoạch và phân tích hành vi).

---

## 🧪 Chạy Thử nghiệm (Tests)

Để chạy các bộ kiểm thử tự động (Unit Tests) cho lớp nghiệp vụ:
```bash
go test -v ./...
```

---

## 🚢 Triển khai Production trên Ubuntu 22.04

Hệ thống hỗ trợ đóng gói Docker toàn phần cho cả dịch vụ API và Worker bằng tệp [Dockerfile](file:///v:/Project/todo/backend/Dockerfile) đa giai đoạn (multi-stage) và tệp [docker-compose.prod.yml](file:///v:/Project/todo/backend/docker-compose.prod.yml).

### Bước 1: Cài đặt Docker trên Ubuntu 22.04
Nếu máy chủ chưa có Docker, hãy chạy các lệnh sau để cài đặt:
```bash
# Cập nhật hệ thống
sudo apt update && sudo apt upgrade -y

# Cài đặt Docker
sudo apt install -y docker.io

# Khởi chạy Docker và kích hoạt tự khởi động cùng hệ thống
sudo systemctl enable --now docker

# Thêm user hiện tại vào nhóm docker (để chạy lệnh không cần sudo)
sudo usermod -aG docker $USER
newgrp docker
```

### Bước 2: Chuẩn bị mã nguồn và biến môi trường
1. Sao chép thư mục `/backend` lên máy chủ Ubuntu.
2. Tạo tệp cấu hình `.env` sản xuất trên máy chủ:
   ```bash
   cp .env.example .env
   nano .env
   ```
   *Lưu ý:* Điền chính xác khóa `GEMINI_API_KEY`, cấu hình một `JWT_SECRET` an toàn, và giữ nguyên `DB_HOST=postgres`, `QDRANT_HOST=qdrant` (để các dịch vụ tự động kết nối qua Docker Network nội bộ).

### Bước 3: Khởi chạy các container bằng Docker Compose Production
```bash
# Khởi chạy tất cả các dịch vụ (PostgreSQL, Qdrant, API, Worker) ở chế độ chạy ngầm
docker compose -f docker-compose.prod.yml up -d --build
```

### Bước 4: Khởi tạo dữ liệu cơ sở dữ liệu (Migrations)
Sau khi container Postgres đã chạy, sao chép và thực thi file cấu trúc bảng:
```bash
docker cp migrations/000001_init.up.sql prod-postgres:/tmp/init.sql
docker exec -i prod-postgres psql -U postgres -d todo_db -f /tmp/init.sql
```

### Bước 5: Xem logs và quản lý trạng thái
```bash
# Xem log thời gian thực của toàn bộ hệ thống
docker compose -f docker-compose.prod.yml logs -f

# Kiểm tra các container đang hoạt động
docker compose -f docker-compose.prod.yml ps

# Dừng hệ thống
docker compose -f docker-compose.prod.yml down
```
