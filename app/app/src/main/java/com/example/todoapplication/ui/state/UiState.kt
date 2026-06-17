package com.example.todoapplication.ui.state

/**
 * Mô hình hóa trạng thái màn hình theo kiểu sealed: Loading / Success / Error.
 * Giúp UI render dứt khoát theo một nguồn trạng thái duy nhất (single source of truth).
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
