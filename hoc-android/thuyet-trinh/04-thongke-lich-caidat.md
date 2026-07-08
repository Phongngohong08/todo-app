# Thẻ 04 — Thống kê · Lịch · Cài đặt · Mẫu · Theme

⬅️ [Về mục lục thuyết trình](./README.md)

---

## 🔹 Thống kê (Stats) + bản đồ nhiệt cả năm

**Một câu:** Tải số liệu tổng hợp (`GET stats/summary`) và danh sách việc đã hoàn thành (`GET tasks?status=COMPLETED`), rồi *tự gom* ở client thành biểu đồ tuần + heatmap năm + "ngày hoàn hảo".

**Sơ đồ:**
```
StatsScreen
   → StatsViewModel.loadSummary()                 StatsViewModel.kt:47
      → StatsRepository.summary()  GET stats/summary   Repositories.kt:71 / ApiService.kt:81
      → loadYearly() (gom heatmap)                 StatsViewModel.kt:57
   → StatsViewModel.loadWeekly()                   StatsViewModel.kt:74
      → StatsRepository.completedTasks()  GET tasks?status=COMPLETED   Repositories.kt:74
```

**Điểm nên chỉ — xử lý dữ liệu ngay ở ViewModel:**
- Heatmap năm: gom việc hoàn thành theo từng ngày `"yyyy-MM-dd"`, `perfectDays` = số ngày có ≥1 việc — [StatsViewModel.kt:57](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/StatsViewModel.kt#L57).
- Biểu đồ 7 ngày: đếm việc hoàn thành theo "cách hôm nay bao nhiêu ngày" — [StatsViewModel.kt:74](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/StatsViewModel.kt#L74).

**Có thể bị hỏi:**
- *"Heatmap do backend tính hay app tính?"* → App tự gom từ danh sách việc đã hoàn thành (dùng `updatedAt`), backend chỉ trả dữ liệu thô. Xem vòng lặp gom nhóm [StatsViewModel.kt:62](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/StatsViewModel.kt#L62).
- *"Trí nhớ AI cũng ở màn này?"* → Đúng, xem [Thẻ 03](./03-tinh-nang-ai.md).

---

## 🔹 Lịch (Calendar) — khai triển công việc lặp lại

**Một câu:** Tải tất cả việc rồi *tự khai triển* các việc lặp (DAILY/WEEKLY/MONTHLY) thành từng ngày cụ thể trong 12 tháng tới, gom theo ngày để vẽ lịch.

**Sơ đồ:**
```
CalendarScreen
   → CalendarViewModel.load()                       CalendarViewModel.kt:28
      → TaskRepository.loadTasks(null, null)  GET tasks
      → với mỗi task: sinh các lần xuất hiện theo recurrence   CalendarViewModel.kt:36-52
      → tasksByDay: Map<"yyyy-MM-dd", List<Task>>
```

**Điểm nên chỉ — logic lặp:** [CalendarViewModel.kt:44](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/CalendarViewModel.kt#L44)
```kotlin
when (task.recurrence) {
    "DAILY"   -> occ.add(Calendar.DAY_OF_MONTH, 1)
    "WEEKLY"  -> ... // theo các thứ trong recurrenceDays ("MON,WED,FRI")
    "MONTHLY" -> occ.add(Calendar.MONTH, 1)
    else      -> break  // không lặp: chỉ đúng ngày hạn
}
```
- Đổi `"MON,WED,FRI"` → tập thứ: [CalendarViewModel.parseWeekdaySet:64](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/CalendarViewModel.kt#L64).

**Có thể bị hỏi:**
- *"Việc lặp lưu nhiều bản ghi hay một?"* → Một bản ghi task + quy tắc lặp (`recurrence`, `recurrenceDays`); các lần hiện trên lịch được *sinh ra lúc chạy*, không lưu trùng.
- *"Sao có `guard < 800`?"* → Chặn vòng lặp vô hạn khi sinh lịch — giới hạn an toàn ([CalendarViewModel.kt:41](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/CalendarViewModel.kt#L41)).

---

## 🔹 Cài đặt (Settings) — cấu hình cho AI lập lịch

**Một câu:** Tải/sửa/lưu giờ bắt đầu buổi sáng, giờ kết thúc buổi tối, thời lượng mỗi việc — dùng cho AI xếp Kế hoạch ngày.

**Sơ đồ:**
```
SettingsScreen
   → SettingsViewModel.load()  GET preferences        SettingsViewModel.kt:34 / Repositories.kt:37
   → setMorning/setEvening/setDuration (đổi state)     SettingsViewModel.kt:50-52
   → save()  PUT preferences                           SettingsViewModel.kt:54 / Repositories.kt:38
```
- Endpoint: [ApiService.kt:20](../../app/app/src/main/java/com/example/todoapplication/data/api/ApiService.kt#L20) (`GET preferences`), [:23](../../app/app/src/main/java/com/example/todoapplication/data/api/ApiService.kt#L23) (`PUT preferences`).

**Có thể bị hỏi:**
- *"Ô nhập thời lượng chặn ký tự lạ thế nào?"* → `setDuration` lọc chỉ giữ chữ số: [SettingsViewModel.kt:52](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/SettingsViewModel.kt#L52).

---

## 🔹 Mẫu nhiệm vụ (Templates)

**Một câu:** Danh sách mẫu định sẵn (uống nước, tập thể dục...) theo nhóm; chọn một mẫu là điền sẵn vào màn tạo task.

- Dữ liệu mẫu (tĩnh, ngay trong code): [TemplatesScreen.kt:35](../../app/app/src/main/java/com/example/todoapplication/ui/screens/TemplatesScreen.kt#L35) (`TEMPLATE_GROUPS`).
- Chọn mẫu → dùng `QuickAddDraft` + điều hướng `TaskDetail("new")`, giống luồng Quick Add ([Thẻ 03](./03-tinh-nang-ai.md)).

**Có thể bị hỏi:** *"Mẫu lấy từ server?"* → Không, là danh sách tĩnh khai báo trong app ([TemplatesScreen.kt:35](../../app/app/src/main/java/com/example/todoapplication/ui/screens/TemplatesScreen.kt#L35)).

---

## 🔹 Giao diện Sáng/Tối (Theme)

**Một câu:** Chọn chế độ Sáng/Tối/Theo hệ thống; lưu vào SharedPreferences; đổi `mode` là *cả app recompose* sang màu mới.

**Sơ đồ:**
```
SettingsScreen (chọn chế độ)
   → ThemeController.setMode(context, mode)          ThemeController.kt:30  (lưu + đổi state)
Theme đọc ThemeController.mode để chọn bảng màu       ui/theme/Theme.kt
```
- `mode` là **Compose state** (`mutableStateOf`) → đổi là mọi màn dùng nó tự vẽ lại: [ThemeController.kt:19](../../app/app/src/main/java/com/example/todoapplication/data/repository/ThemeController.kt#L19).
- Nạp lúc khởi động: [TodoApplication.kt:16](../../app/app/src/main/java/com/example/todoapplication/TodoApplication.kt#L16).

**Có thể bị hỏi:**
- *"Đổi theme sao cả app đổi ngay?"* → `mode` là state toàn cục; theme root ([MainActivity.kt:32](../../app/app/src/main/java/com/example/todoapplication/MainActivity.kt#L32)) đọc nó nên khi đổi, Compose recompose toàn bộ — đúng tinh thần "UI = f(state)".
- *"Danh mục (category) lưu ở đâu?"* → [CategoryStore](../../app/app/src/main/java/com/example/todoapplication/data/repository/CategoryStore.kt) trên SharedPreferences, cũng `init()` trong TodoApplication.

⬅️ [Về mục lục thuyết trình](./README.md)
