# Thẻ 03 — Tính năng AI (Quick Add · Coach · Kế hoạch ngày · Trí nhớ)

⬅️ [Về mục lục thuyet trình](./README.md)

> ⚙️ Lưu ý chung: **phần AI thật (gọi Gemini) nằm ở backend Go** [`backend/`](../../backend/). App chỉ gọi các endpoint `ai/*`, `plans/*`. Vì các call này chậm (15–30s), timeout mạng được nới lên 60s — [NetworkClient.kt:46](../../app/app/src/main/java/com/example/todoapplication/data/api/NetworkClient.kt#L46).

---

## 🔹 Quick Add — gõ câu tự nhiên, AI tách thành task

**Một câu:** Người dùng gõ "họp nhóm 3h chiều mai" → gửi `POST ai/parse-task` → backend trả về task có cấu trúc → app mở màn chi tiết điền sẵn để người dùng xác nhận.

**Sơ đồ:**
```
TaskListScreen: thanh Quick Add
   → TaskListViewModel.parseQuickAdd(text, localTime)      TaskListViewModel.kt:140
      → AiRepository.parseTask(text, localTime)            Repositories.kt:58
         → api.parseTask(...)  ── POST ai/parse-task       ApiService.kt:67
   → thành công: phát TaskListEvent.QuickAddReady(parsed)
   → Screen lưu QuickAddDraft + điều hướng TaskDetail("new")   TaskListScreen.kt:102
```

**Điểm code:** [TaskListViewModel.kt:140](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/TaskListViewModel.kt#L140); phần nhận kết quả và điều hướng ở [TaskListScreen.kt:102](../../app/app/src/main/java/com/example/todoapplication/ui/screens/TaskListScreen.kt#L102).

**Có thể bị hỏi:**
- *"Kết quả AI truyền sang màn chi tiết bằng cách nào?"* → Qua một holder tạm `QuickAddDraft` ([QuickAddDraft.kt](../../app/app/src/main/java/com/example/todoapplication/data/repository/QuickAddDraft.kt)) + điều hướng với id `"new"`. (Không nhét object lớn vào route.)
- *"AI parse có tự lưu task không?"* → Không — chỉ *đề xuất*, người dùng xem lại rồi mới bấm lưu. An toàn hơn.

---

## 🔹 AI Coach — trò chuyện tư vấn

**Một câu:** Gửi tin nhắn → `POST ai/chat` → nhận câu trả lời; lịch sử chat giữ trong bộ nhớ tiến trình để không mất khi chuyển màn.

**Sơ đồ:**
```
AICoachScreen (ô nhập + nút gửi)
   → AICoachViewModel.sendMessage(text)          AICoachViewModel.kt:47
      thêm bong bóng "user" + bật isThinking
      → AiRepository.chat(text)                   Repositories.kt:53
         → api.chat(ChatInput)  ── POST ai/chat   ApiService.kt:63
      thêm bong bóng "assistant" (hoặc câu lỗi) + tắt isThinking
```

**Điểm code:** [AICoachViewModel.sendMessage:47](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/AICoachViewModel.kt#L47).

**Có thể bị hỏi:**
- *"Chuyển sang màn khác rồi quay lại, chat có mất không?"* → Không. Lịch sử giữ ở `ChatHistoryStore` cấp tiến trình — [AICoachViewModel.kt:29](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/AICoachViewModel.kt#L29).
- *"Lỗi mạng khi chat thì sao?"* → `result.getOrElse { "...gặp sự cố kết nối..." }` hiển thị như một bong bóng trả lời, không crash — [AICoachViewModel.kt:54](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/AICoachViewModel.kt#L54).
- *"Đang chờ AI mà bấm gửi tiếp?"* → Chặn: `if (... _uiState.value.isThinking) return`.

---

## 🔹 Kế hoạch ngày (Daily Plan) — AI xếp lịch theo khung giờ

**Một câu:** Xem/tạo lịch ngày: `GET plans/daily` để xem, `POST plans/daily/generate` để AI xếp lại; có xử lý riêng khi AI hết lượt (429) hay lịch rỗng.

**Sơ đồ:**
```
DailyPlanScreen (nút Tạo lại)
   → DailyPlanViewModel.loadPlan() / regenerate()      DailyPlanViewModel.kt:34, 44
      → PlanRepository.getDaily / generateDaily         Repositories.kt:44, 47
         → GET plans/daily / POST plans/daily/generate  ApiService.kt:51, 57
      → phân loại kết quả → phát thông báo phù hợp
```

**Điểm nên khoe — xử lý lỗi tinh tế:** [DailyPlanViewModel.kt:52](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/DailyPlanViewModel.kt#L52)
```kotlin
val msg = when {
    error is ApiException && error.isRateLimited ->  "AI đang quá tải hoặc đã hết lượt hôm nay..."
    result.isFailure ->                               "Tạo lịch trình thất bại. Kiểm tra kết nối..."
    result.getOrNull()?.planData.isNullOrEmpty() ->   "AI chưa xếp được khung giờ nào — có thể đã quá giờ..."
    else ->                                           "Đã tạo lịch trình!"
}
```

**Có thể bị hỏi:**
- *"Làm sao biết AI hết lượt (rate limit)?"* → Backend trả HTTP 429; app bọc thành `ApiException.isRateLimited` (xem [NetworkCallExt.kt](../../app/app/src/main/java/com/example/todoapplication/data/repository/NetworkCallExt.kt) / `ApiException`) và hiển thị thông báo riêng thay vì báo "thất bại" chung chung.
- *"Vì sao gửi `local_time`?"* → Để AI xếp lịch từ đúng thời điểm hiện tại của người dùng ([DailyPlanViewModel.kt:47](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/DailyPlanViewModel.kt#L47)).

---

## 🔹 Trí nhớ dài hạn (AI Memories)

**Một câu:** App nhờ backend phân tích hoạt động 30 ngày để rút ra "thói quen", hiển thị/xóa được; nằm trong màn Thống kê.

**Sơ đồ:**
```
StatsScreen (khu Trí nhớ)
   → StatsViewModel.loadMemories / triggerExtraction / deleteMemory   StatsViewModel.kt:89, 97, 120
      → AiRepository.listMemories / triggerExtraction / deleteMemory   Repositories.kt:61, 63, 66
         → GET ai/memories / POST ai/memories/trigger-extraction / DELETE ai/memories/{id}   ApiService.kt:70-77
```

**Có thể bị hỏi:**
- *"Trích xuất xong báo gì?"* → Tùy số lượng: rút ra thói quen mới / không có hoạt động / có phân tích nhưng không ra thói quen — [StatsViewModel.kt:105](../../app/app/src/main/java/com/example/todoapplication/ui/viewmodel/StatsViewModel.kt#L105).

⬅️ [Về mục lục thuyết trình](./README.md)
