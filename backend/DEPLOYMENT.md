# 🚀 Hướng dẫn Triển khai Production (Docker + Nginx + HTTPS)

Tài liệu này ghi lại **toàn bộ quy trình deploy backend** lên một máy chủ Ubuntu (đã kiểm chứng trên AWS EC2 Ubuntu 24.04 và GCP Ubuntu 22.04), gồm 4 lớp:

```
Internet ──HTTPS(443)──> Nginx (reverse proxy) ──HTTP(127.0.0.1:8080)──> prod-api (Gin)
                                                                              │
                                                          Docker network ─────┼──> prod-postgres (5432)
                                                                              └──> prod-qdrant   (6333)
```

> Domain tham chiếu trong tài liệu: `todo.phongngohong.online`. Thay bằng domain của bạn khi áp dụng.

---

## 0. Yêu cầu trước khi bắt đầu

- Một máy chủ Ubuntu có **IP public** (AWS EC2 / GCP / VPS).
- Một **domain** đã trỏ bản ghi **A** về IP public của máy chủ.
- Mở sẵn cổng **22** (SSH). Cổng **80/443** sẽ mở ở Bước 4.
- Có sẵn `GEMINI_API_KEY` (Google Gemini) cho các tính năng AI.

---

## 1. Cài đặt Docker & Docker Compose

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y git docker.io docker-compose-v2
sudo systemctl enable --now docker

# Cho phép chạy docker không cần sudo (đăng xuất/đăng nhập lại để có hiệu lực)
sudo usermod -aG docker $USER
```

> ⚠️ **Quan trọng:** gói `docker.io` **không** kèm Docker Compose. Bắt buộc cài thêm
> `docker-compose-v2` thì lệnh `docker compose` mới hoạt động. Nếu thiếu, khi chạy
> `docker compose -f ...` sẽ báo lỗi khó hiểu `unknown shorthand flag: 'f'`.

Kiểm tra:
```bash
docker compose version
```

---

## 2. Lấy mã nguồn & cấu hình biến môi trường

```bash
git clone https://github.com/Phongngohong08/todo-app.git
cd todo-app/backend
cp .env.example .env
nano .env    # hoặc: vi .env
```

Các giá trị **bắt buộc** phải chỉnh trong `.env`:

| Biến | Ghi chú |
| :--- | :--- |
| `GEMINI_API_KEY` | Khóa Google Gemini — thiếu thì các tính năng AI lỗi |
| `JWT_SECRET` | Đặt chuỗi ngẫu nhiên dài, an toàn (vd `openssl rand -hex 32`) |
| `DB_PASSWORD` | Mật khẩu Postgres mạnh |
| `DB_HOST=postgres` | **Giữ nguyên** — API kết nối DB qua Docker network nội bộ |
| `QDRANT_HOST=qdrant` | **Giữ nguyên** |

---

## 3. Khởi chạy các container & chạy Migrations

```bash
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps     # kiểm tra postgres/qdrant/api đang chạy
```

**Bắt buộc chạy migrations** (nếu bỏ qua, mọi request sẽ trả `500 relation "users" does not exist`):

```bash
for f in 000001_init 000002_add_tags_recurrence 000003_simplify_tasks \
         000004_custom_category 000005_reminder_and_weekdays; do
  docker cp migrations/${f}.up.sql prod-postgres:/tmp/m.sql
  docker exec -i prod-postgres psql -U postgres -d todo_db -f /tmp/m.sql
done
```

> Áp **đúng thứ tự số** `000001 → 000005`. Container Postgres tên là `prod-postgres`
> (định nghĩa trong `docker-compose.prod.yml`).

Kiểm tra nội bộ (backend nghe ở host `127.0.0.1:8080`):
```bash
curl -i http://127.0.0.1:8080/api/v1/
# Trả về "404 page not found" của Gin = backend CHẠY ĐÚNG (route gốc không tồn tại là bình thường)
```

---

## 4. Nginx Reverse Proxy

Cài nginx:
```bash
sudo apt install -y nginx
```

Tạo file cấu hình site:
```bash
sudo tee /etc/nginx/sites-available/todo.conf > /dev/null <<'EOF'
server {
    listen 80;
    listen [::]:80;
    server_name todo.phongngohong.online;

    # Cho phép body lớn (AI parse-task / chat)
    client_max_body_size 10m;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;

        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # AI Coach / Gemini có thể chậm -> nới timeout
        proxy_connect_timeout 60s;
        proxy_send_timeout    120s;
        proxy_read_timeout    120s;
    }
}
EOF
```

Kích hoạt site, tắt trang mặc định, reload:
```bash
sudo ln -sf /etc/nginx/sites-available/todo.conf /etc/nginx/sites-enabled/todo.conf
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx
```

### 4.1 Mở firewall (nếu không sẽ timeout khi truy cập từ ngoài)

**AWS EC2 — Security Group** (Console → EC2 → Instance → Security → Edit inbound rules):

| Type  | Protocol | Port | Source    |
| :---- | :------- | :--- | :-------- |
| HTTP  | TCP      | 80   | 0.0.0.0/0 |
| HTTPS | TCP      | 443  | 0.0.0.0/0 |

**ufw trên máy** (nếu đang bật):
```bash
sudo ufw status
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
```

Kiểm tra từ Internet (đã thông khi trả về `404 page not found` của Gin):
```bash
curl -i http://todo.phongngohong.online/api/v1/
```

> 🛑 **Nếu vẫn timeout:** kiểm tra IP public của máy có khớp bản ghi A của domain không:
> ```bash
> curl -s http://169.254.169.254/latest/meta-data/public-ipv4   # AWS EC2
> ```
> IP đổi sau mỗi lần reboot (nếu không dùng Elastic IP) là nguyên nhân phổ biến khiến DNS trỏ sai.

---

## 5. Bật HTTPS bằng Let's Encrypt (Certbot)

> App Android gọi `https://` nên **bắt buộc** có HTTPS.

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d todo.phongngohong.online --redirect \
     -m your-email@example.com --agree-tos --no-eff-email
```

Certbot sẽ tự động:
- Xin chứng chỉ (xác thực qua `http://.../.well-known/acme-challenge/...` — **cần cổng 80 đã mở ở Bước 4**),
- Sửa `todo.conf` thêm block `listen 443 ssl`,
- Thêm redirect `301` HTTP → HTTPS,
- Cài **cron tự động gia hạn** (chứng chỉ có hạn 90 ngày).

Kiểm tra gia hạn tự động:
```bash
sudo certbot renew --dry-run
```

---

## 6. Kiểm thử toàn bộ API (smoke test)

```bash
BASE="https://todo.phongngohong.online/api/v1"
EMAIL="smoke_$(date +%s)@example.com"

# 1) Đăng ký
curl -s -o /dev/null -w "register  [%{http_code}]\n" -X POST "$BASE/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"Passw0rd123\",\"name\":\"Smoke\"}"

# 2) Đăng nhập -> lấy token
TOKEN=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"Passw0rd123\"}" \
  | grep -o '"token":"[^"]*"' | head -1 | sed 's/"token":"//;s/"//')

# 3) Tạo task
curl -s -o /dev/null -w "create    [%{http_code}]\n" -X POST "$BASE/tasks" \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"title":"Smoke test","priority":"HIGH","category":"WORK"}'

# 4) AI Quick Add (Gemini)
curl -s -o /dev/null -w "parse-task[%{http_code}]\n" -X POST "$BASE/ai/parse-task" \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"text":"nộp báo cáo thứ 6, ưu tiên cao"}'

# 5) Chặn khi không có token
curl -s -o /dev/null -w "no-auth   [%{http_code}] (kỳ vọng 401)\n" "$BASE/tasks"
```

Kỳ vọng: `201`, `201`, `200`, `401`.

---

## 7. Vận hành thường ngày

```bash
# Xem log realtime
docker compose -f docker-compose.prod.yml logs -f api

# Trạng thái container
docker compose -f docker-compose.prod.yml ps

# Cập nhật code mới
git pull
docker compose -f docker-compose.prod.yml up -d --build

# Dừng / khởi động lại
docker compose -f docker-compose.prod.yml down
docker compose -f docker-compose.prod.yml restart api
```

---

## 8. Ghi chú Bảo mật

- 🔒 **Đừng mở** cổng `5432` (Postgres), `6333`/`6334` (Qdrant) trong Security Group. Chỉ mở `22`, `80`, `443`. Tốt hơn nữa: sửa `docker-compose.prod.yml` để bind các cổng DB về `127.0.0.1` (vd `"127.0.0.1:5432:5432"`) — API vẫn kết nối bình thường qua Docker network nội bộ.
- 🔑 `JWT_SECRET` phải là chuỗi ngẫu nhiên mạnh và **không commit** vào git (`.env` nằm trong `.gitignore`).
- 🔁 Certbot tự gia hạn chứng chỉ; kiểm tra định kỳ bằng `sudo certbot renew --dry-run`.

---

## 9. Sự cố thường gặp (Troubleshooting)

| Triệu chứng | Nguyên nhân | Cách xử lý |
| :--- | :--- | :--- |
| `500 relation "users" does not exist` | Chưa chạy migrations | Chạy lại **Bước 3** (migrations) |
| `unknown shorthand flag: 'f' in -f` | Thiếu Docker Compose v2 | `sudo apt install -y docker-compose-v2` |
| `curl` port 80 timeout / Certbot `Timeout during connect` | Firewall chặn 80/443 hoặc DNS trỏ sai IP | Mở Security Group + `ufw`; đối chiếu IP public với bản ghi A (**Bước 4.1**) |
| `502 Bad Gateway` từ nginx | Container `prod-api` chết | `docker compose -f docker-compose.prod.yml logs api` |
| `404 page not found` ở `/api/v1/` | **Bình thường** — đây là response mặc định của Gin cho route gốc | Không cần xử lý; test bằng endpoint thật (`/api/v1/auth/register`) |
