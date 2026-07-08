# 🎤 Truy vết code cho thuyết trình — "Chức năng này làm thế nào?"

Bộ tài liệu này giúp bạn **trả lời tự tin khi bị hỏi "tính năng X làm thế nào, code ở đâu?"** — chỉ đúng file, đúng dòng, nói đúng luồng.

Gồm 2 phần:
1. **Phương pháp truy vết** (đọc mục dưới đây) — cách lần theo *bất kỳ* chức năng nào trong 30 giây.
2. **Thẻ truy vết sẵn cho từng chức năng** (các file bên dưới) — mở ra lúc Q&A là có ngay sơ đồ + dòng code.

## Các thẻ chức năng

| File | Chức năng bao gồm |
|---|---|
| [01 — Xác thực](./01-xac-thuc.md) | Đăng nhập, đăng ký, lưu phiên, tự refresh token, đăng xuất, "auth guard" |
| [02 — Công việc](./02-cong-viec.md) | Danh sách, tạo/sửa/xóa, hoàn thành, đổi ưu tiên, kéo-thả, lọc/tìm/sắp xếp, **xem offline**, bước con (subtask) |
| [03 — Tính năng AI](./03-tinh-nang-ai.md) | Quick Add (AI tách câu), AI Coach chat, Kế hoạch ngày, Trí nhớ dài hạn |
| [04 — Thống kê · Lịch · Cài đặt](./04-thongke-lich-caidat.md) | Thống kê + heatmap năm, Lịch (khai triển lặp), Cài đặt, Mẫu nhiệm vụ, Theme sáng/tối |
| [05 — Nền · Thông báo · Widget](./05-nen-thongbao-widget.md) | Nhắc việc (WorkManager), nút trên thông báo, widget màn hình chính |

> 📌 Mọi đường link trong các thẻ đều bấm được và trỏ tới **file:dòng thật**. Khi thuyết trình, mở sẵn thẻ liên quan để "nhảy" tới code ngay.

---

## Phương pháp truy vết một chức năng (học thuộc phần này)

Nhớ lại kiến trúc ([bài 02](../02-kien-truc-tong-the.md)): mọi chức năng đều chảy theo **một mạch cố định**. Truy vết = đi dọc mạch đó.

```
Người dùng chạm  →  Screen (ui/screens)  →  ViewModel (ui/viewmodel)  →  Repository (data/repository)  →  ApiService / Room
                         nút onClick            hàm nghiệp vụ                nguồn dữ liệu                 HTTP / SQL
                              ▲                       │
                              └──── state (StateFlow) ┘  (UI tự vẽ lại)
```

### Quy trình 4 bước để tìm code cho *bất kỳ* chức năng

**Bước 1 — Xuất phát từ hành động trên màn hình.**
Mở file trong [ui/screens/](../../app/app/src/main/java/com/example/todoapplication/ui/screens/) tương ứng màn đang nói. Tìm nút/thao tác — nó gọi một hàm dạng `xxxViewModel.abc(...)`. Đó là "điểm vào" của chức năng.

**Bước 2 — Sang ViewModel xem nghiệp vụ.**
Mở file cùng tên trong [ui/viewmodel/](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/). Hàm `abc(...)` gần như luôn có khuôn:
```kotlin
fun abc(...) {
    _uiState.update { it.copy(isLoading = true) }   // đổi state
    viewModelScope.launch {                          // chạy nền
        val result = repo.xyz(...)                   // gọi xuống repository
        _uiState.update { it.copy(...) }             // đổ kết quả vào state
        _events.emit(...)                            // (nếu cần) bắn Toast/điều hướng
    }
}
```

**Bước 3 — Xuống Repository xem lấy dữ liệu ở đâu.**
Mở file trong [data/repository/](../../app/app/src/main/java/com/example/todoapplication/data/repository/). Hàm `xyz(...)` gọi `api....()` (mạng) hoặc `dao....()` (DB cục bộ). Đây là nơi trả lời "dữ liệu đến từ đâu": backend hay cache offline.

**Bước 4 — Chạm đáy: API hoặc DB.**
- Mạng: mở [ApiService.kt](../../app/app/src/main/java/com/example/todoapplication/data/api/ApiService.kt) — annotation `@GET/@POST...` cho biết endpoint chính xác gọi tới backend Go.
- Cục bộ: mở [Daos.kt](../../app/app/src/main/java/com/example/todoapplication/data/local/Daos.kt) — câu SQL thật.

> 🔑 **Mẹo vàng:** ba tên hàm ở ba tầng thường *gần giống nhau* (`completeTask` → `completeTask` → `completeTask`). Cứ tìm theo tên là ra. Trong Android Studio: bôi đen tên hàm rồi **Ctrl+B** (Go to Declaration) để nhảy tầng.

### Cách tìm nhanh trong Android Studio khi bị hỏi bất chợt
- **Double Shift** (Search Everywhere) → gõ tên màn/hàm.
- **Ctrl+B** trên một lời gọi hàm → nhảy tới định nghĩa (đi xuống tầng dưới).
- **Alt+F7** (Find Usages) trên một hàm → xem *ai gọi nó* (đi ngược lên tầng trên).
- **Ctrl+Shift+F** (Find in Files) → gõ chuỗi UI tiếng Việt bạn thấy trên màn (vd "Đã xóa công việc") để nhảy thẳng tới đoạn xử lý.

---

## Khung trả lời khi bị hỏi (nói theo 4 nhịp này là chắc điểm)

Khi giám khảo hỏi *"Chức năng hoàn thành công việc làm thế nào?"*, trả lời theo mạch:

1. **Điểm vào (UI):** *"Người dùng bấm nút tick trên thẻ task, gọi `taskListViewModel.completeTask(task)` trong [TaskListScreen](../../app/app/src/main/java/com/example/todoapplication/ui/screens/TaskListScreen.kt)."*
2. **Nghiệp vụ (ViewModel):** *"ViewModel mở coroutine, gọi repository rồi tải lại danh sách để cập nhật state — [TaskListViewModel.kt:82](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskListViewModel.kt#L82)."*
3. **Dữ liệu (Repository → API):** *"Repository gọi `api.completeTask(id)`, tức endpoint `POST tasks/{id}/complete` tới backend — [TaskRepository.kt:63](../../app/app/src/main/java/com/example/todoapplication/data/repository/TaskRepository.kt#L63), [ApiService.kt:47](../../app/app/src/main/java/com/example/todoapplication/data/api/ApiService.kt#L47)."*
4. **Kết quả (state → UI):** *"State đổi, và vì UI quan sát StateFlow nên danh sách tự vẽ lại — không cần lệnh cập nhật thủ công."*

Bốn nhịp: **Vào đâu → Xử lý gì → Dữ liệu từ đâu → Hiển thị lại thế nào.** Áp dụng được cho *mọi* câu hỏi.

### Ba "thần chú" nên chốt cuối câu trả lời (ghi điểm kiến trúc)
- *"UI = f(state)"* — giao diện chỉ là hàm của trạng thái, nên đổi state là UI tự cập nhật.
- *"State xuống, Event lên"* — dữ liệu chảy một chiều; hành động người dùng đi ngược lên ViewModel.
- *"Repository là nguồn dữ liệu duy nhất"* — nên có thể đổi mạng↔cache mà UI không đổi (chính là tính năng offline).

---

## Bảng tra cứu thần tốc (feature → file chính)

| Chức năng | Screen | ViewModel | Repository | Endpoint / DAO |
|---|---|---|---|---|
| Đăng nhập | LoginScreen | [LoginViewModel.login:30](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/AuthViewModels.kt#L30) | [AuthRepository.login:22](../../app/app/src/main/java/com/example/todoapplication/data/repository/Repositories.kt#L22) | `POST auth/login` |
| Danh sách việc | TaskListScreen | [TaskListViewModel.loadTasks:57](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskListViewModel.kt#L57) | [TaskRepository.loadTasks:25](../../app/app/src/main/java/com/example/todoapplication/data/repository/TaskRepository.kt#L25) | `GET tasks` + Room cache |
| Hoàn thành việc | TaskListScreen | [completeTask:82](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskListViewModel.kt#L82) | [completeTask:63](../../app/app/src/main/java/com/example/todoapplication/data/repository/TaskRepository.kt#L63) | `POST tasks/{id}/complete` |
| Tạo/sửa việc | TaskDetailScreen | [TaskDetailViewModel.create:77](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskDetailViewModel.kt#L77) | [createTask:50](../../app/app/src/main/java/com/example/todoapplication/data/repository/TaskRepository.kt#L50) | `POST/PUT tasks` |
| Bước con (subtask) | TaskDetailScreen | [addSubtask:45](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskDetailViewModel.kt#L45) | [SubtaskRepository.add:17](../../app/app/src/main/java/com/example/todoapplication/data/repository/SubtaskRepository.kt#L17) | Room `subtask` |
| Quick Add (AI) | TaskListScreen | [parseQuickAdd:140](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskListViewModel.kt#L140) | [AiRepository.parseTask:58](../../app/app/src/main/java/com/example/todoapplication/data/repository/Repositories.kt#L58) | `POST ai/parse-task` |
| AI Coach | AICoachScreen | [sendMessage:47](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/AICoachViewModel.kt#L47) | [AiRepository.chat:53](../../app/app/src/main/java/com/example/todoapplication/data/repository/Repositories.kt#L53) | `POST ai/chat` |
| Kế hoạch ngày | DailyPlanScreen | [regenerate:44](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/DailyPlanViewModel.kt#L44) | [PlanRepository.generateDaily:47](../../app/app/src/main/java/com/example/todoapplication/data/repository/Repositories.kt#L47) | `POST plans/daily/generate` |
| Thống kê | StatsScreen | [loadSummary:47](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/StatsViewModel.kt#L47) | [StatsRepository.summary:71](../../app/app/src/main/java/com/example/todoapplication/data/repository/Repositories.kt#L71) | `GET stats/summary` |
| Lịch | CalendarScreen | [CalendarViewModel.load:28](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/CalendarViewModel.kt#L28) | [TaskRepository.loadTasks:25](../../app/app/src/main/java/com/example/todoapplication/data/repository/TaskRepository.kt#L25) | `GET tasks` (khai triển lặp cục bộ) |
| Cài đặt | SettingsScreen | [SettingsViewModel.save:54](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/SettingsViewModel.kt#L54) | [PreferencesRepository.update:38](../../app/app/src/main/java/com/example/todoapplication/data/repository/Repositories.kt#L38) | `PUT preferences` |
| Nhắc việc | (tự động) | — | [ReminderScheduler.schedule:21](../../app/app/src/main/java/com/example/todoapplication/data/notifications/ReminderScheduler.kt#L21) | WorkManager |

⬅️ Quay lại [Mục lục giáo trình](../README.md)
