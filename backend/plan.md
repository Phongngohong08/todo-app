# Project: AI-Powered To-Do List Backend

You are a Senior Software Architect and Senior Golang Backend Engineer.

Your task is to design a complete backend architecture and implementation plan for an AI-powered To-Do List mobile application.

## Project Goal

The application is a traditional To-Do List app enhanced with AI coaching and long-term memory.

Users can:

* Create tasks
* Update tasks
* Complete tasks
* Postpone tasks
* View daily schedules
* Chat with an AI assistant

The AI should not only answer questions but also learn user habits and provide personalized recommendations.

---

# Main Features

## 1. Task Management

Users can:

* Create tasks
* Update tasks
* Delete tasks
* Mark tasks as completed
* Postpone tasks

Each task should contain:

* id
* user_id
* title
* description
* priority
* due_date
* estimated_duration
* preferred_time_start
* preferred_time_end
* status
* created_at
* updated_at

Statuses:

* TODO
* IN_PROGRESS
* COMPLETED
* CANCELLED
* POSTPONED

---

## 2. Task Activity Tracking

The system must track all user actions.

Examples:

* Task created
* Task started
* Task paused
* Task completed
* Task postponed

The purpose is to analyze behavior patterns.

Examples:

* User often postpones report-writing tasks
* User usually completes coding tasks
* User works best in the morning

---

## 3. Daily AI Planning

Every morning the system generates a personalized daily plan.

The plan should consider:

* Unfinished tasks
* Task priority
* Due dates
* User habits
* Historical performance

Example:

08:00 - 09:00 Review blockchain notes

09:00 - 10:00 Finish project report

14:00 - 15:00 Exercise

The plan should be generated automatically by a scheduled job.

---

## 4. AI Coach

Users can chat with an AI assistant.

Examples:

"I don't feel like doing this task."

"What should I work on now?"

"Can I postpone this task until tonight?"

The AI should provide personalized recommendations based on:

* Current tasks
* Previous behavior
* Historical successes
* Historical failures
* User preferences

Example response:

"You postponed similar tasks 4 times in the past and missed the deadline each time. Consider working on it for at least 15 minutes before switching."

---

## 5. Long-Term Memory

The AI must maintain long-term memory.

Examples:

* User prefers coding in the morning
* User frequently postpones reports
* User completes short tasks more successfully
* User often misses deadlines for documentation tasks

Memory should be searchable and reusable.

The AI must retrieve relevant memories before generating responses.

---

# Technical Requirements

## Backend

Language:

* Golang

Architecture:

* Clean Architecture
* Domain Driven Design where appropriate
* REST API

Framework:

* Standard library or Gin

Database:

* PostgreSQL

Vector Database:

* Qdrant

AI Provider:

* Google Gemini API (gemini-2.5-flash & gemini-embedding-001)

---

# Memory Architecture

Use:

PostgreSQL:

* Users
* Tasks
* Task logs
* Daily plans
* Chat history

Qdrant:

* Long-term memories
* User habits
* Behavioral insights

Memory Flow:

1. User performs actions
2. System records events
3. AI extracts meaningful habits
4. Habits are stored in Qdrant
5. AI retrieves relevant memories during conversations

---

# Required Deliverables

Please generate:

## 1. High-Level Architecture

Include:

* Components
* Services
* Databases
* AI integrations

---

## 2. Database Design

Generate:

* ERD
* Tables
* Columns
* Relationships

---

## 3. Domain Design

Define:

* Entities
* Value Objects
* Aggregates

---

## 4. API Design

Design all REST APIs including:

Authentication

Tasks

Daily Plans

AI Chat

User Preferences

Statistics

Memory Management

Provide request and response examples.

---

## 5. AI Architecture

Explain:

* Daily plan generation flow
* Memory extraction flow
* Memory retrieval flow
* AI coaching flow

Include sequence diagrams.

---

## 6. Qdrant Design

Define:

* Collections
* Payload schema
* Vector usage
* Search strategy

---

## 7. Background Jobs

Design:

* Daily planning job
* Memory extraction job
* Statistics aggregation job

---

## 8. Project Structure

Provide a production-ready Golang folder structure.

Example:

/cmd
/internal
/pkg
/api
/migrations
/deployments

---

## 9. Development Roadmap

Split implementation into phases:

Phase 1:
Basic To-Do App

Phase 2:
Statistics & Tracking

Phase 3:
AI Daily Planner

Phase 4:
AI Coach

Phase 5:
Long-Term Memory

For each phase provide estimated effort and dependencies.

---

Important:

Do not start coding immediately.

First produce the complete software architecture, database schema, API specification, AI memory design, and implementation roadmap.

Only after architecture approval should implementation begin.

---

# Cập nhật v2 (Phụ lục — phản ánh hiện trạng đã triển khai)

> Phần trên là đề bài/thiết kế ban đầu. Phụ lục này ghi lại các thay đổi đã được triển khai sau đó so với spec gốc. Xem chi tiết API trong `README.md`.

## Xác thực: Access + Refresh Token
- Chuyển từ JWT 24h đơn lẻ sang **access token ngắn hạn** (mặc định `15m`) + **refresh token dài hạn** (mặc định `720h`), JWT HS256 stateless, có claim `typ` (`access`/`refresh`).
- Thêm endpoint `POST /api/v1/auth/refresh`; middleware chỉ chấp nhận `typ=access`.
- TTL cấu hình qua `ACCESS_TOKEN_TTL`, `REFRESH_TOKEN_TTL`.
- Client (Android) tự refresh khi gặp `401` qua OkHttp `Authenticator`; refresh thất bại → buộc đăng nhập lại.

## Scheduler chạy ngầm
- Gộp scheduler (trích xuất trí nhớ `01:00`, tạo sẵn lịch trình `04:00`) **vào trong tiến trình API** (goroutine) — bỏ tiến trình/worker container riêng.

## Tính năng task mới
1. **Tags & Search**: cột `tags JSONB` (+ GIN index), lọc theo `?tag=` và tìm kiếm `?q=` (ILIKE tiêu đề/mô tả).
2. **Recurring**: cột `recurrence` (`NONE`/`DAILY`/`WEEKLY`/`MONTHLY`); khi `complete` task lặp có hạn chót → tự sinh occurrence kế tiếp.
3. **AI Quick Add**: `POST /api/v1/ai/parse-task` — Gemini tách câu ngôn ngữ tự nhiên thành task có cấu trúc (không tự lưu, client xác nhận trước).

## Trí nhớ dài hạn
- Cửa sổ phân tích thủ công mở rộng lên **30 ngày**; trả về `{ analyzed, extracted }`.
- **Khử trùng lặp theo ngữ nghĩa** trước khi lưu (Qdrant cosine ≥ 0.90 thì bỏ qua).

## Migrations
- `000001_init.up.sql` (schema gốc) → `000002_add_tags_recurrence.up.sql` (tags + recurrence). Áp lần lượt theo thứ tự.

## Phía ứng dụng Android (client-side)
- **Reminders**: WorkManager lập lịch local notification theo `due_date` (không dùng FCM).
- UI: ô tìm kiếm, nhập tag dạng chip, selector lặp lại, bottom sheet AI Quick Add, tự điều hướng về Login khi phiên hết hạn.
