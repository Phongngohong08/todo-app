package com.example.todoapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todoapplication.data.repository.ChatRepository
import com.example.todoapplication.data.repository.aiFailureMessage
import com.example.todoapplication.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Một bong bóng chat. isUser=true → tin của người dùng (bên phải); false → câu trả lời của AI (bên trái). */
data class ChatUIModel(val text: String, val isUser: Boolean, val isPending: Boolean = false)

const val COACH_GREETING =
    "Xin chào! Tôi là AI Coach của bạn. Tôi có thể giúp bạn sắp xếp kế hoạch, tìm động lực hoặc phân tích thói quen trì hoãn. Hôm nay bạn muốn chia sẻ điều gì?"

val SUGGESTED_PROMPTS = listOf(
    "Giúp tôi sắp xếp hôm nay",
    "Tôi hay trì hoãn, sao khắc phục?",
    "Lời khuyên giữ tập trung",
    "Phân tích thói quen của tôi"
)

data class AICoachUiState(
    val messages: List<ChatUIModel> = listOf(ChatUIModel(COACH_GREETING, isUser = false)),
    val isThinking: Boolean = false          // true khi đang chờ AI trả lời → hiện "..."
)

/**
 * [TẦNG VIEWMODEL] Màn AI Coach (chat).
 *
 * Lịch sử chat nằm trong Room (bản đệm của server) nên mở lại màn — hay mở lại app — vẫn thấy cuộc trò chuyện,
 * và giao diện luôn khớp với những gì AI thực sự "nhớ" (server gửi lịch sử đó cho AI ở mỗi câu hỏi).
 */
class AICoachViewModel(private val repo: ChatRepository) : ViewModel() {

    private val isThinking = MutableStateFlow(false)
    /** Lỗi của lần gửi gần nhất — chỉ hiển thị, không lưu vào lịch sử. */
    private val lastError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AICoachUiState> =
        combine(repo.observeMessages(), isThinking, lastError) { stored, thinking, error ->
            val bubbles = stored.map { ChatUIModel(it.content, isUser = it.role == "user", isPending = it.isPending) }
            AICoachUiState(
                // Lời chào luôn đứng đầu, không phải một tin nhắn thật
                messages = listOf(ChatUIModel(COACH_GREETING, isUser = false)) + bubbles +
                    listOfNotNull(error?.let { ChatUIModel(it, isUser = false) }),
                isThinking = thinking
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AICoachUiState())

    init {
        // Hiện ngay bản đệm, đồng thời lấy bản mới nhất từ server (lỗi mạng thì giữ bản đệm)
        viewModelScope.launch { repo.refresh() }
    }

    // Người dùng gửi một tin nhắn. Luồng: hiện tin của mình ngay → bật "đang nghĩ" → gọi AI → hiện đáp.
    fun sendMessage(text: String) {
        val trimmed = text.trim()
        // Bỏ qua nếu rỗng, hoặc đang chờ AI trả lời (chặn bấm gửi liên tục).
        if (trimmed.isBlank() || isThinking.value) return
        isThinking.value = true
        lastError.value = null
        viewModelScope.launch {
            repo.send(trimmed).onFailure { error ->
                lastError.update {
                    error.aiFailureMessage("Tôi gặp sự cố kết nối tới máy chủ AI. Vui lòng thử lại sau.")
                }
            }
            isThinking.value = false
        }
    }

    companion object {
        // Factory: "công thức" tạo ViewModel này, lấy sẵn repository từ ServiceLocator (DI thủ công).
        val Factory = viewModelFactory {
            initializer { AICoachViewModel(ServiceLocator.chatRepository) }
        }
    }
}
