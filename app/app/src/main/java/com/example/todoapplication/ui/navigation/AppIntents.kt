package com.example.todoapplication.ui.navigation

import android.content.Context
import android.content.Intent
import com.example.todoapplication.MainActivity

/**
 * "Đường dẫn" mở app từ bên ngoài (thông báo, widget, app shortcut, chia sẻ từ app khác) — gom một chỗ để
 * mọi nơi dùng cùng một hợp đồng. MainActivity đọc [EXTRA_OPEN] và điều hướng tới màn tương ứng.
 */
object AppIntents {
    const val EXTRA_OPEN = "open"

    const val OPEN_TODAY = "today"
    const val OPEN_ADD = "add"
    const val OPEN_REVIEW = "review"
    const val OPEN_FOCUS = "focus"
    private const val OPEN_TASK_PREFIX = "task:"

    /** Nội dung chia sẻ từ app khác (ACTION_SEND) → điền sẵn thanh tạo nhanh. */
    const val EXTRA_SHARED_TEXT = "shared_text"

    fun openTask(taskId: String) = OPEN_TASK_PREFIX + taskId
    fun taskIdOf(link: String): String? = link.takeIf { it.startsWith(OPEN_TASK_PREFIX) }?.removePrefix(OPEN_TASK_PREFIX)

    fun main(context: Context, open: String? = null): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (open != null) putExtra(EXTRA_OPEN, open)
        }
}

/** Yêu cầu điều hướng đọc từ Intent — MainActivity phát cho NavHost. */
sealed interface PendingLink {
    data class Open(val target: String) : PendingLink
    data class SharedText(val text: String) : PendingLink

    companion object {
        fun from(intent: Intent?): PendingLink? {
            intent ?: return null
            if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
                val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                val combined = listOf(subject, text).filter { it.isNotBlank() }.joinToString(" — ")
                return combined.takeIf { it.isNotBlank() }?.let { SharedText(it.take(1000)) }
            }
            intent.getStringExtra(AppIntents.EXTRA_SHARED_TEXT)?.let { return SharedText(it) }
            return intent.getStringExtra(AppIntents.EXTRA_OPEN)?.let { Open(it) }
        }
    }
}

/**
 * Yêu cầu mở thanh tạo nhanh từ bên ngoài (app shortcut "Thêm việc", chia sẻ từ app khác, nút + trên widget).
 * Giá trị = chữ điền sẵn ("" = mở trống). Màn Hôm nay lấy ra rồi đặt lại null.
 */
object QuickCreateRequest {
    val pending = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
}
