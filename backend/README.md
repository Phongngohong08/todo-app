# Backend cho Ứng dụng Quản lý Công việc Hỗ trợ bởi AI (AI-Powered To-Do List)

Đây là dịch vụ backend viết bằng ngôn ngữ Golang cho ứng dụng Quản lý Công việc (To-Do List) tích hợp trí tuệ nhân tạo. Dự án được xây dựng theo mô hình **Clean Architecture** và các mô hình thiết kế hướng tên miền **Domain-Driven Design (DDD)**, kết hợp cơ sở dữ liệu quan hệ (PostgreSQL), cơ sở dữ liệu vector (Qdrant) và Google Gemini API nhằm cung cấp thời gian biểu cá nhân hóa và trợ lý AI Coach có bộ nhớ dài hạn.

---

## 🌟 Các Tính năng Nổi bật

1. **Quản lý Công việc (CRUD)**: Tạo mới, xem, cập nhật, xóa và hoàn thành công việc. Trạng thái được rút gọn còn `TODO`/`COMPLETED`.
2. **Phân loại theo Danh mục & Tìm kiếm (Category & Search)**: Mỗi task thuộc một **danh mục** (mặc định `PERSONAL`/`WORK`/`OTHER`, nhưng cho phép **danh mục tự do** do người dùng tự đặt); lọc danh sách theo `?category=` và tìm kiếm `?q=` theo tiêu đề/mô tả.
3. **Task Lặp lại (Recurring)**: Task có thể lặp `DAILY`/`WEEKLY`/`MONTHLY`; lặp tuần có thể chọn **các thứ cụ thể** (`recurrence_days`, vd `"MON,WED,FRI"`). Khi hoàn thành một task lặp (có hạn chót), hệ thống tự sinh lần kế tiếp (id tất định, hạn luôn ở tương lai, tính theo `APP_TIMEZONE`); mở lại việc đã xong sẽ thu hồi lần kế tiếp chưa làm. Mỗi task còn có **`reminder_offset_minutes`** để nhắc trước hạn (do client lập lịch local notification).
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
| `JWT_SECRET` | Khóa ký JWT. **Bắt buộc**, tối thiểu 32 ký tự (`openssl rand -hex 32`); trống/giá trị mẫu thì API từ chối khởi động | *Không có* |
| `ACCESS_TOKEN_TTL` | Thời gian sống của access token (định dạng Go duration, vd `15m`, `1h`) | `15m` |
| `REFRESH_TOKEN_TTL` | Thời gian sống của refresh token (định dạng Go duration, vd `720h`) | `720h` (30 ngày) |
| `APP_TIMEZONE` | Múi giờ người dùng (IANA): giờ chạy job 01:00/04:00, "hôm nay" mặc định, mốc reset hạn mức AI | `Asia/Ho_Chi_Minh` |
| `AUTO_MIGRATE` | Tự áp migration còn thiếu khi khởi động | `true` |
| `AI_RATE_PER_MINUTE` | Số lượt AI mỗi phút cho một người dùng | `6` |
| `AI_DAILY_LIMIT_PER_USER` | Số lượt AI mỗi ngày cho một người dùng (`0` = không giới hạn) | `30` |
| `TRUSTED_PROXIES` | IP/CIDR reverse proxy được tin để đọc IP thật (rate limit theo IP) | `127.0.0.1,::1,172.16.0.0/12` |
| `CORS_ALLOWED_ORIGINS` | Origin trình duyệt được phép gọi API; trống = tắt CORS (app Android không cần) | *(trống)* |
| `GIN_MODE` | Đặt `release` ở production (docker-compose.prod.yml đã đặt sẵn) | `debug` |

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
| `POST` | `/api/v1/auth/logout` | Gửi `{ "refresh_token": "..." }`: thu hồi **mọi** refresh token của tài khoản (đăng xuất khỏi tất cả thiết bị). Luôn trả `204` |

Khi gặp `401` ở bất kỳ endpoint được bảo vệ nào, client nên tự động gọi `/auth/refresh` để lấy access token mới rồi phát lại request; nếu refresh token cũng hết hạn/không hợp lệ thì buộc người dùng đăng nhập lại. (Ứng dụng Android đã hiện thực luồng này tự động qua OkHttp `Authenticator`.)

> **Thu hồi:** mỗi refresh token mang claim `ver` = `users.token_version` lúc phát hành. `/auth/logout` tăng `token_version`, nên mọi refresh token cũ bị từ chối. Access token đã phát vẫn dùng được tới khi hết `ACCESS_TOKEN_TTL` (mặc định 15 phút), vì vậy giữ TTL này ngắn.

### Giới hạn tần suất (HTTP 429 + header `Retry-After`)

| Phạm vi | Giới hạn mặc định |
| :--- | :--- |
| `/auth/login` (theo IP) | 5 lần liền, sau đó 5 lần/phút |
| `/auth/register` (theo IP) | 3 lần liền, sau đó 1 lần/phút |
| `/auth/refresh`, `/auth/logout` (theo IP) | 10 lần liền, sau đó 30 lần/phút |
| Mọi API đã đăng nhập (theo user) | 60 request liền, sau đó 5 request/giây |
| AI: chat, parse-task, lập lịch, phân tích trí nhớ (theo user) | `AI_RATE_PER_MINUTE` lượt/phút và `AI_DAILY_LIMIT_PER_USER` lượt/ngày (reset 0h theo `APP_TIMEZONE`) |
| `/ai/memories/trigger-extraction` (theo user) | Thêm: 2 lần liền, sau đó 1 lần/20 phút |

Lập lịch chỉ trừ lượt AI khi người dùng còn việc chưa xong (không có việc thì trả lịch rỗng, không gọi AI). Bộ đếm nằm trong bộ nhớ tiến trình và reset khi khởi động lại; nếu chạy nhiều instance thì cần chuyển sang Redis.

---

## 📡 REST API (các endpoint chính)

Tất cả endpoint dưới đây (trừ nhóm `/auth`) yêu cầu header `Authorization: Bearer <access_token>`.

### Công việc (Tasks)

| Phương thức | Endpoint | Mô tả |
| :--- | :--- | :--- |
| `POST` | `/api/v1/tasks` | Tạo task. Body: `title`, `description`, `priority` (`LOW`/`MEDIUM`/`HIGH`), `due_date`, **`category`** (chuỗi tự do, mặc định `OTHER`), `recurrence` (`NONE`/`DAILY`/`WEEKLY`/`MONTHLY`), **`recurrence_days`** (vd `"MON,WED,FRI"` khi lặp tuần), **`reminder_offset_minutes`** (số phút nhắc trước hạn) |
| `GET` | `/api/v1/tasks` | Liệt kê task. Query: `status` (`TODO`/`COMPLETED`), `due_date_before`, **`q`** (tìm trong tiêu đề/mô tả), **`category`** (lọc theo danh mục) |
| `GET` | `/api/v1/tasks/sync?since=<server_time>` | **Đồng bộ offline**: trả `{ tasks, deleted_ids, server_time }` — task thay đổi (và bị xóa) sau mốc `since`; bỏ `since` = tải toàn bộ. Client lưu `server_time` làm `since` cho lần sau |
| `GET` | `/api/v1/tasks/{id}` | Chi tiết một task |
| `PUT` | `/api/v1/tasks/{id}` | Ghi **toàn bộ** trạng thái task, **tạo mới nếu id chưa có** (idempotent — id do app sinh khi offline). Body như POST, thêm `status` (`TODO`/`COMPLETED` — chuyển về `TODO` = mở lại), `completed_at`, `sort_order`, `subtasks` (`[{id,title,done,position}]`), `spawned_from`. Trường bỏ trống giữ nguyên giá trị cũ |
| `DELETE` | `/api/v1/tasks/{id}` | Xóa **mềm** (để thiết bị khác biết khi đồng bộ); xóa lại lần nữa vẫn trả thành công |
| `POST` | `/api/v1/tasks/{id}/complete` | Đánh dấu hoàn thành. Task lặp (có `due_date`) sinh lần kế tiếp với **id tất định** `UUIDv3(namespace, id cha)` và hạn luôn ở tương lai — app tính giống hệt nên lần lặp tạo lúc offline không bị nhân đôi |

### AI

| Phương thức | Endpoint | Mô tả |
| :--- | :--- | :--- |
| `POST` | `/api/v1/ai/parse-task` | **Quick Add**: gửi `{ "text": "...", "local_time": "<RFC3339>" }`, nhận về task có cấu trúc (`title`, `description`, `priority`, `due_date`, `category`). **Không** tự lưu task |
| `POST` | `/api/v1/ai/chat` | Trò chuyện với AI Coach |
| `GET` | `/api/v1/ai/chat/history?limit=50` | Lịch sử chat (cũ → mới) để app hiển thị lại cuộc trò chuyện |
| `GET` · `DELETE` | `/api/v1/ai/memories` · `/memories/{id}` | Xem / xóa trí nhớ dài hạn |
| `POST` | `/api/v1/ai/memories/trigger-extraction` | Phân tích thủ công (nhìn lại 30 ngày), trả `{ analyzed, extracted }` |

> Các endpoint khác: `GET/PUT /categories` (danh mục tự tạo, `{ "categories": [...] }`, PUT thay toàn bộ), `GET/PUT /preferences` (giờ dạng `HH:mm`, kết thúc sau bắt đầu, `work_duration_preference` 15–480 phút), `GET /plans/daily?date=YYYY-MM-DD&tz=<IANA>&local_time=HH:mm`, `POST /plans/daily/generate` (cùng query), `GET /stats/summary?tz=<IANA>` (gom biểu đồ 7 ngày theo múi giờ người dùng; thiếu `tz` thì dùng `APP_TIMEZONE`), `GET /healthz`.

> **Lưu ý client-side:** App Android theo kiến trúc offline-first — thống kê, biểu đồ và điểm ưu tiên AI được tính trên máy từ database cục bộ (đồng bộ qua `/tasks/sync`), nên xem được khi offline. `GET /stats/summary` vẫn có cho client khác.

---

## 🚀 Hướng dẫn Cài đặt & Chạy ứng dụng

### 1. Khởi động Cơ sở hạ tầng Database
Khởi chạy PostgreSQL và Qdrant local bằng Docker Compose:
```bash
docker compose up -d
```

### 2. Cấu hình & Migrations
Tạo file `.env` rồi đặt `JWT_SECRET` (API **từ chối khởi động** nếu trống, là giá trị mẫu hoặc ngắn hơn 32 ký tự):
```bash
cp .env.example .env
# Điền JWT_SECRET=<kết quả của: openssl rand -hex 32> và GEMINI_API_KEY
```

Không cần chạy migration bằng tay: các file `migrations/*.up.sql` được nhúng vào binary, và API tự áp các version còn thiếu mỗi lần khởi động (ghi lại trong bảng `schema_migrations`). DB cũ đã áp tay tới `000005` được nhận diện và đánh dấu sẵn. Đặt `AUTO_MIGRATE=false` nếu muốn tự quản lý migration.

> Thêm migration mới: tạo `migrations/000007_<mô_tả>.up.sql` (+ `.down.sql`), version tăng dần, không sửa file đã phát hành.

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
sudo apt install -y docker-compose-v2

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

### Bước 4: Kiểm tra migration & sức khỏe dịch vụ
Migration được API tự áp khi khởi động (không cần `docker cp`/`psql`). Kiểm tra:
```bash
docker compose -f docker-compose.prod.yml logs api | grep -i migration
curl -i http://127.0.0.1:8080/healthz   # {"status":"ok"}
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
