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

/** Một bong bóng chat. */
data class ChatUIModel(val text: String, val isUser: Boolean)

const val COACH_GREETING =
    "Xin chào! Tôi là AI Coach của bạn. Tôi có thể giúp bạn sắp xếp kế hoạch, tìm động lực hoặc phân tích thói quen trì hoãn. Hôm nay bạn muốn chia sẻ điều gì?"

val SUGGESTED_PROMPTS = listOf(
    "Giúp tôi sắp xếp hôm nay",
    "Tôi hay trì hoãn, sao khắc phục?",
    "Lời khuyên giữ tập trung",
    "Phân tích thói quen của tôi"
)

/** Lưu lịch sử chat ở cấp tiến trình để giữ qua các lần điều hướng. */
private object ChatHistoryStore {
    var messages: List<ChatUIModel> = listOf(ChatUIModel(COACH_GREETING, isUser = false))
}

data class AICoachUiState(
    val messages: List<ChatUIModel> = ChatHistoryStore.messages,
    val isThinking: Boolean = false
)

class AICoachViewModel(private val repo: AiRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AICoachUiState())
    val uiState: StateFlow<AICoachUiState> = _uiState.asStateFlow()

    private fun append(msg: ChatUIModel) {
        _uiState.update { it.copy(messages = it.messages + msg) }
        ChatHistoryStore.messages = _uiState.value.messages
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || _uiState.value.isThinking) return
        append(ChatUIModel(trimmed, isUser = true))
        _uiState.update { it.copy(isThinking = true) }
        viewModelScope.launch {
            val result = repo.chat(trimmed)
            append(
                ChatUIModel(
                    result.getOrElse { "Tôi gặp sự cố kết nối tới máy chủ AI. Vui lòng thử lại sau." },
                    isUser = false
                )
            )
            _uiState.update { it.copy(isThinking = false) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { AICoachViewModel(ServiceLocator.aiRepository) }
        }
    }
}
