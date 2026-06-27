# Backend cho Ứng dụng Quản lý Công việc Hỗ trợ bởi AI (AI-Powered To-Do List)

Đây là dịch vụ backend viết bằng ngôn ngữ Golang cho ứng dụng Quản lý Công việc (To-Do List) tích hợp trí tuệ nhân tạo. Dự án được xây dựng theo mô hình **Clean Architecture** và các mô hình thiết kế hướng tên miền **Domain-Driven Design (DDD)**, kết hợp cơ sở dữ liệu quan hệ (PostgreSQL), cơ sở dữ liệu vector (Qdrant) và Google Gemini API nhằm cung cấp thời gian biểu cá nhân hóa và trợ lý AI Coach có bộ nhớ dài hạn.

---

## 🌟 Các Tính năng Nổi bật

1. **Quản lý Công việc (CRUD)**: Tạo mới, xem, cập nhật, xóa và hoàn thành công việc. Trạng thái được rút gọn còn `TODO`/`COMPLETED`.
2. **Phân loại theo Danh mục & Tìm kiếm (Category & Search)**: Mỗi task thuộc một **danh mục** (mặc định `PERSONAL`/`WORK`/`OTHER`, nhưng cho phép **danh mục tự do** do người dùng tự đặt); lọc danh sách theo `?category=` và tìm kiếm `?q=` theo tiêu đề/mô tả.
3. **Task Lặp lại (Recurring)**: Task có thể lặp `DAILY`/`WEEKLY`/`MONTHLY`; khi hoàn thành một task lặp (có hạn chót), hệ thống tự sinh occurrence kế tiếp với hạn chót dời theo chu kỳ.
4. **AI Quick Add (Tạo task bằng ngôn ngữ tự nhiên)**: Gửi một câu mô tả tự nhiên, Gemini tách thành task có cấu trúc (tiêu đề, độ ưu tiên, hạn chót, **danh mục**) để người dùng xác nhận trước khi lưu.
5. **Theo dõi Hoạt động (Activity Logging)**: Tự động lưu vết hành vi (tạo task `CREATED`, hoàn thành task `COMPLETED`) làm dữ liệu phân tích thói quen cho AI.
6. **Lập Kế hoạch AI Hàng ngày (Daily AI Planning)**: Tự động chạy ngầm vào lúc `04:00 AM` hàng ngày để tạo lịch trình tối ưu dựa trên danh sách việc chưa hoàn thành, **độ ưu tiên + hạn chót**, cài đặt giờ giấc cá nhân và phân tích thói quen lưu trong bộ nhớ dài hạn.
7. **Trợ lý AI Coach**: Một chatbot tư vấn và tạo động lực cho người dùng. AI Coach sẽ tự động lấy các thông tin về thói quen cũ (ví dụ: thường xuyên hoãn việc viết báo cáo) từ cơ sở dữ liệu Vector để đưa ra lời khuyên thiết thực.
8. **Trích xuất Bộ nhớ Dài hạn (Long-Term Memory Extraction)**: Một tiến trình chạy ngầm vào lúc `01:00 AM` hàng đêm để phân tích lịch sử hoạt động và các tin nhắn chat trong ngày của người dùng, tự động trích xuất các thói quen/hành vi hữu ích (có khử trùng lặp theo ngữ nghĩa), tạo vector nhúng (embeddings) và lưu trữ vào Qdrant.
9. **Thống kê (Statistics)**: Cung cấp báo cáo gọn: số việc **đã hoàn thành**, số việc **đang chờ**, phân bố việc đang chờ **theo danh mục**, và số việc hoàn thành mỗi ngày trong **7 ngày gần nhất**.

> **Phía ứng dụng Android** còn có **Nhắc nhở local (Reminders)** qua WorkManager: tự gửi thông báo khi task đến hạn `due_date` — tính năng client-side, không phụ thuộc backend/FCM.

---

## 🏗️ Kiến trúc Thư mục Dự án

```
/backend
  /cmd
    /api              # Điểm khởi chạy API Gateway RESTful (bao gồm cả scheduler chạy ngầm)
  /internal
    /domain           # Định nghĩa thực thể (Entities), giá trị (Value Objects) và giao diện lưu trữ (Repository Interfaces)
    /usecase          # Hiện thực hóa các nghiệp vụ chính (Auth, Task, Plan, Coach, Memory, QuickAdd)
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
| `JWT_SECRET` | Khóa bí mật dùng để ký mã đăng nhập JWT | *super_secret_key_change_me* |
| `ACCESS_TOKEN_TTL` | Thời gian sống của access token (định dạng Go duration, vd `15m`, `1h`) | `15m` |
| `REFRESH_TOKEN_TTL` | Thời gian sống của refresh token (định dạng Go duration, vd `720h`) | `720h` (30 ngày) |

---

## 🔐 Xác thực (JWT: Access & Refresh Token)

Hệ thống dùng mô hình **access token + refresh token** (JWT HS256, không lưu trạng thái ở server):

- **Access token**: sống ngắn (mặc định `15m`), đính kèm ở header `Authorization: Bearer <token>` cho mọi request được bảo vệ. Mỗi token mang claim `typ` để phân biệt loại; middleware chỉ chấp nhận token `typ=access`.
- **Refresh token**: sống dài (mặc định `720h`), chỉ dùng để lấy cặp token mới khi access token hết hạn.

| Phương thức | Endpoint | Mô tả |
| :--- | :--- | :--- |
| `POST` | `/api/v1/auth/register` | Đăng ký tài khoản mới |
| `POST` | `/api/v1/auth/login` | Đăng nhập, trả về `token`, `refresh_token`, `expires_in` và thông tin `user` |
| `POST` | `/api/v1/auth/refresh` | Gửi `{ "refresh_token": "..." }`, nhận về cặp `token` + `refresh_token` mới (sliding expiration) |

Khi gặp `401` ở bất kỳ endpoint được bảo vệ nào, client nên tự động gọi `/auth/refresh` để lấy access token mới rồi phát lại request; nếu refresh token cũng hết hạn/không hợp lệ thì buộc người dùng đăng nhập lại. (Ứng dụng Android đã hiện thực luồng này tự động qua OkHttp `Authenticator`.)

> **Lưu ý:** refresh token là JWT không lưu DB nên **không thể thu hồi riêng lẻ** trước khi hết hạn. Nếu cần tính năng "đăng xuất tất cả thiết bị"/thu hồi, hãy chuyển sang lưu refresh token (đã hash) trong cơ sở dữ liệu.

---

## 📡 REST API (các endpoint chính)

Tất cả endpoint dưới đây (trừ nhóm `/auth`) yêu cầu header `Authorization: Bearer <access_token>`.

### Công việc (Tasks)

| Phương thức | Endpoint | Mô tả |
| :--- | :--- | :--- |
| `POST` | `/api/v1/tasks` | Tạo task. Body: `title`, `description`, `priority` (`LOW`/`MEDIUM`/`HIGH`), `due_date`, **`category`** (chuỗi tự do, mặc định `OTHER`), `recurrence` (`NONE`/`DAILY`/`WEEKLY`/`MONTHLY`) |
| `GET` | `/api/v1/tasks` | Liệt kê task. Query: `status` (`TODO`/`COMPLETED`), `due_date_before`, **`q`** (tìm trong tiêu đề/mô tả), **`category`** (lọc theo danh mục) |
| `GET` | `/api/v1/tasks/{id}` | Chi tiết một task |
| `PUT` | `/api/v1/tasks/{id}` | Cập nhật task (gồm `category`, `recurrence`) |
| `DELETE` | `/api/v1/tasks/{id}` | Xóa task |
| `POST` | `/api/v1/tasks/{id}/complete` | Đánh dấu hoàn thành. Khi `complete` một task lặp (có `due_date`), backend tự tạo occurrence kế tiếp |

### AI

| Phương thức | Endpoint | Mô tả |
| :--- | :--- | :--- |
| `POST` | `/api/v1/ai/parse-task` | **Quick Add**: gửi `{ "text": "...", "local_time": "<RFC3339>" }`, nhận về task có cấu trúc (`title`, `description`, `priority`, `due_date`, `category`). **Không** tự lưu task |
| `POST` | `/api/v1/ai/chat` | Trò chuyện với AI Coach |
| `GET` · `DELETE` | `/api/v1/ai/memories` · `/memories/{id}` | Xem / xóa trí nhớ dài hạn |
| `POST` | `/api/v1/ai/memories/trigger-extraction` | Phân tích thủ công (nhìn lại 30 ngày), trả `{ analyzed, extracted }` |

> Các endpoint khác: `GET/PUT /preferences`, `GET/POST /plans/daily`, `GET /stats/summary`.

> **Lưu ý client-side:** Biểu đồ năng suất 7 ngày và tính điểm ưu tiên AI trên ứng dụng Android đều tính **client-side** từ dữ liệu đã có (`GET /tasks?status=COMPLETED`) — không cần endpoint riêng.

---

## 🚀 Hướng dẫn Cài đặt & Chạy ứng dụng

### 1. Khởi động Cơ sở hạ tầng Database
Khởi chạy PostgreSQL và Qdrant local bằng Docker Compose:
```bash
docker compose up -d
```

### 2. Khởi tạo Cấu trúc Bảng dữ liệu (Migrations)
Áp dụng **lần lượt** các tệp SQL trong thư mục `migrations/` theo thứ tự số. Ví dụ dùng Docker CLI (không cần cài psql trên máy):
```bash
# 000001 - schema khởi tạo
docker cp migrations/000001_init.up.sql todo-postgres:/tmp/init.sql
docker exec -i todo-postgres psql -U postgres -d todo_db -f /tmp/init.sql

# 000002 - thêm cột tags (JSONB) và recurrence cho tasks
docker cp migrations/000002_add_tags_recurrence.up.sql todo-postgres:/tmp/m2.sql
docker exec -i todo-postgres psql -U postgres -d todo_db -f /tmp/m2.sql

# 000003 - đơn giản hoá task: thay tags -> category, bỏ duration/khung giờ, status còn TODO/COMPLETED
docker cp migrations/000003_simplify_tasks.up.sql todo-postgres:/tmp/m3.sql
docker exec -i todo-postgres psql -U postgres -d todo_db -f /tmp/m3.sql

# 000004 - cho phép danh mục tự do (bỏ ràng buộc CHECK, nới VARCHAR)
docker cp migrations/000004_custom_category.up.sql todo-postgres:/tmp/m4.sql
docker exec -i todo-postgres psql -U postgres -d todo_db -f /tmp/m4.sql
```
> Áp **lần lượt theo thứ tự số**. Migration `000003`/`000004` chuyển schema từ mô hình cũ (tags/duration) sang mô hình đơn giản hoá (category). **Với DB đã có dữ liệu, bắt buộc áp đủ tới `000004` trước khi chạy bản backend mới**, nếu không các truy vấn task sẽ lỗi thiếu/thừa cột.

### 3. Cài đặt các thư viện Go Dependencies
```bash
go mod tidy
```

### 4. Chạy API Gateway Server
```bash
go run ./cmd/api/main.go
```
Dịch vụ REST API sẽ chạy tại `http://localhost:8080/api/v1`.

Scheduler chạy ngầm (trích xuất trí nhớ lúc `01:00`, tạo sẵn lịch trình ngày lúc `04:00`) được khởi động **bên trong chính tiến trình API** dưới dạng goroutine — không cần chạy thêm tiến trình riêng.

---

## 🧪 Chạy Thử nghiệm (Tests)

Để chạy các bộ kiểm thử tự động (Unit Tests) cho lớp nghiệp vụ:
```bash
go test -v ./...
```

---

## 🚢 Triển khai Production trên Ubuntu 22.04

Hệ thống hỗ trợ đóng gói Docker toàn phần cho dịch vụ API (đã bao gồm scheduler chạy ngầm) bằng tệp [Dockerfile](file:///v:/Project/todo/backend/Dockerfile) đa giai đoạn (multi-stage) và tệp [docker-compose.prod.yml](file:///v:/Project/todo/backend/docker-compose.prod.yml).

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
# Khởi chạy tất cả các dịch vụ (PostgreSQL, Qdrant, API) ở chế độ chạy ngầm
docker compose -f docker-compose.prod.yml up -d --build
```

### Bước 4: Khởi tạo dữ liệu cơ sở dữ liệu (Migrations)
Sau khi container Postgres đã chạy, sao chép và thực thi **lần lượt** các file migration:
```bash
docker cp migrations/000001_init.up.sql prod-postgres:/tmp/init.sql
docker exec -i prod-postgres psql -U postgres -d todo_db -f /tmp/init.sql

docker cp migrations/000002_add_tags_recurrence.up.sql prod-postgres:/tmp/m2.sql
docker exec -i prod-postgres psql -U postgres -d todo_db -f /tmp/m2.sql

docker cp migrations/000003_simplify_tasks.up.sql prod-postgres:/tmp/m3.sql
docker exec -i prod-postgres psql -U postgres -d todo_db -f /tmp/m3.sql

docker cp migrations/000004_custom_category.up.sql prod-postgres:/tmp/m4.sql
docker exec -i prod-postgres psql -U postgres -d todo_db -f /tmp/m4.sql
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
