package com.example.todoapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todoapplication.data.repository.AiRepository
import com.example.todoapplication.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Một bong bóng chat. isUser=true → tin của người dùng (bên phải); false → câu trả lời của AI (bên trái). */
data class ChatUIModel(val text: String, val isUser: Boolean)

const val COACH_GREETING =
    "Xin chào! Tôi là AI Coach của bạn. Tôi có thể giúp bạn sắp xếp kế hoạch, tìm động lực hoặc phân tích thói quen trì hoãn. Hôm nay bạn muốn chia sẻ điều gì?"

val SUGGESTED_PROMPTS = listOf(
    "Giúp tôi sắp xếp hôm nay",
    "Tôi hay trì hoãn, sao khắc phục?",
    "Lời khuyên giữ tập trung",
    "Phân tích thói quen của tôi"
)

/**
 * Lưu lịch sử chat ở cấp TIẾN TRÌNH (object = singleton toàn app) để giữ qua các lần điều hướng.
 * Lý do: ViewModel bị hủy khi rời màn AI Coach; nếu giữ lịch sử trong ViewModel thì quay lại sẽ mất sạch.
 * Đặt ở đây nên đóng/mở lại màn vẫn thấy nguyên đoạn chat cũ (chỉ mất khi tắt hẳn app).
 */
private object ChatHistoryStore {
    // Khởi tạo sẵn 1 lời chào của AI để khung chat không trống khi mở lần đầu.
    var messages: List<ChatUIModel> = listOf(ChatUIModel(COACH_GREETING, isUser = false))
}

data class AICoachUiState(
    val messages: List<ChatUIModel> = ChatHistoryStore.messages,
    val isThinking: Boolean = false          // true khi đang chờ AI trả lời → hiện "..."
)

/**
 * [TẦNG VIEWMODEL] Màn AI Coach (chat): gửi tin nhắn tới backend AI, nhận câu trả lời.
 * Lịch sử chat giữ ở ChatHistoryStore (cấp tiến trình) để không mất khi chuyển màn.
 */
class AICoachViewModel(private val repo: AiRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AICoachUiState())
    val uiState: StateFlow<AICoachUiState> = _uiState.asStateFlow()

    // Thêm 1 bong bóng vào danh sách: tạo list MỚI (messages + msg) rồi đồng bộ sang store để giữ lịch sử.
    // (Dùng "list mới" thay vì sửa list cũ vì state là bất biến — Compose so sánh để biết cần vẽ lại.)
    private fun append(msg: ChatUIModel) {
        _uiState.update { it.copy(messages = it.messages + msg) }
        ChatHistoryStore.messages = _uiState.value.messages
    }

    // Người dùng gửi một tin nhắn. Luồng: hiện tin của mình ngay → bật "đang nghĩ" → gọi AI → hiện đáp.
    fun sendMessage(text: String) {
        val trimmed = text.trim()
        // Bỏ qua nếu rỗng, hoặc đang chờ AI trả lời (chặn bấm gửi liên tục).
        if (trimmed.isBlank() || _uiState.value.isThinking) return
        append(ChatUIModel(trimmed, isUser = true))          // 1. hiện tin của người dùng NGAY (không đợi mạng)
        _uiState.update { it.copy(isThinking = true) }        // 2. bật cờ "đang nghĩ" → UI hiện dấu "..."
        viewModelScope.launch {                              // 3. mở coroutine gọi mạng (không chặn UI)
            val result = repo.chat(trimmed)                  //    gọi backend AI (bài coach.Chat bên backend)
            append(                                          // 4. hiện câu trả lời của AI...
                ChatUIModel(
                    // getOrElse: nếu lỗi mạng thì thay bằng câu xin lỗi thay vì crash.
                    result.getOrElse { "Tôi gặp sự cố kết nối tới máy chủ AI. Vui lòng thử lại sau." },
                    isUser = false
                )
            )
            _uiState.update { it.copy(isThinking = false) }  // 5. tắt cờ "đang nghĩ"
        }
    }

    companion object {
        // Factory: "công thức" tạo ViewModel này, lấy sẵn repository từ ServiceLocator (DI thủ công).
        // Màn hình gọi viewModel(factory = Factory) để nhận đúng instance đã ghép phụ thuộc.
        val Factory = viewModelFactory {
            initializer { AICoachViewModel(ServiceLocator.aiRepository) }
        }
    }
}
