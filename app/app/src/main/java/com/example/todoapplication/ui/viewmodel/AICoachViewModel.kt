package com.example.todoapplication.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.todoapplication.data.model.CoachAction
import com.example.todoapplication.data.repository.ChatRepository
import com.example.todoapplication.data.repository.CoachActionApplier
import com.example.todoapplication.data.repository.aiFailureMessage
import com.example.todoapplication.di.ServiceLocator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

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
    val isThinking: Boolean = false,         // true khi đang chờ AI trả lời → hiện "..."
    /** Hành động AI vừa đề xuất (kèm câu trả lời mới nhất) — hiện thành nút xác nhận. */
    val actions: List<CoachAction> = emptyList(),
    /** Nhãn các hành động đã áp dụng (để đổi nút thành "✓ Đã áp dụng"). */
    val applied: Set<String> = emptySet()
)

/**
 * [TẦNG VIEWMODEL] Màn AI Coach (chat).
 *
 * Lịch sử chat nằm trong Room (bản đệm của server) nên mở lại màn — hay mở lại app — vẫn thấy cuộc trò chuyện,
 * và giao diện luôn khớp với những gì AI thực sự "nhớ" (server gửi lịch sử đó cho AI ở mỗi câu hỏi).
 */
class AICoachViewModel(
    private val repo: ChatRepository,
    private val applier: CoachActionApplier
) : ViewModel() {

    private val isThinking = MutableStateFlow(false)
    /** Lỗi của lần gửi gần nhất — chỉ hiển thị, không lưu vào lịch sử. */
    private val lastError = MutableStateFlow<String?>(null)
    private val actions = MutableStateFlow<List<CoachAction>>(emptyList())
    private val applied = MutableStateFlow<Set<String>>(emptySet())

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val uiState: StateFlow<AICoachUiState> =
        combine(repo.observeMessages(), isThinking, lastError, actions, applied) { stored, thinking, error, acts, done ->
            val bubbles = stored.map { ChatUIModel(it.content, isUser = it.role == "user", isPending = it.isPending) }
            AICoachUiState(
                // Lời chào luôn đứng đầu, không phải một tin nhắn thật
                messages = listOf(ChatUIModel(COACH_GREETING, isUser = false)) + bubbles +
                    listOfNotNull(error?.let { ChatUIModel(it, isUser = false) }),
                isThinking = thinking,
                actions = acts,
                applied = done
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
        actions.value = emptyList() // đề xuất cũ không còn đúng ngữ cảnh câu hỏi mới
        applied.value = emptySet()
        viewModelScope.launch {
            repo.send(trimmed, OffsetDateTime.now().withNano(0).toString()).onSuccess { response ->
                actions.value = response.actions.orEmpty()
            }.onFailure { error ->
                lastError.update {
                    error.aiFailureMessage("Tôi gặp sự cố kết nối tới máy chủ AI. Vui lòng thử lại sau.")
                }
            }
            isThinking.value = false
        }
    }

    /** Người dùng bấm "Áp dụng" trên một đề xuất của AI. */
    fun applyAction(action: CoachAction) {
        if (action.label in applied.value) return
        viewModelScope.launch {
            val msg = applier.apply(action)
            if (msg != null) applied.update { it + action.label }
            _messages.emit(msg ?: "Không áp dụng được đề xuất này (việc có thể đã bị xóa)")
        }
    }

    companion object {
        // Factory: "công thức" tạo ViewModel này, lấy sẵn repository từ ServiceLocator (DI thủ công).
        val Factory = viewModelFactory {
            initializer { AICoachViewModel(ServiceLocator.chatRepository, CoachActionApplier(ServiceLocator.taskRepository)) }
        }
    }
}
