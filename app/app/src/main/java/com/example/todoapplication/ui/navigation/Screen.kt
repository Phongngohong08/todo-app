package com.example.todoapplication.ui.navigation

/**
 * [TẦNG UI · ĐIỀU HƯỚNG] Danh mục tập trung các "route" (đường dẫn) của app.
 * Gom vào một chỗ để tránh gõ chuỗi lung tung; dùng Screen.X.route thay vì "task_list".
 * Route có {tham_số} thì kèm hàm createRoute(...) để dựng đường dẫn cụ thể.
 *
 * Bốn tab chính (thanh dưới): Hôm nay · Việc làm · Lịch · Tôi.
 */
sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")

    // ── Tab ──
    /** "Ngày của tôi" + lịch trình AI trong ngày. */
    object Today : Screen("today")
    object TaskList : Screen("task_list")
    object Calendar : Screen("calendar")
    /** Hồ sơ: mục tiêu, thống kê, tổng kết, lịch sử, thùng rác, cài đặt. */
    object Stats : Screen("stats")

    // ── Màn con ──
    object TaskDetail : Screen("task_detail/{taskId}") {
        fun createRoute(taskId: String) = "task_detail/$taskId"
    }
    object AICoach : Screen("ai_coach?prompt={prompt}") {
        /** [prompt]: câu điền sẵn vào ô chat (vd từ Tổng kết tuần). */
        fun createRoute(prompt: String? = null) =
            if (prompt == null) "ai_coach" else "ai_coach?prompt=${android.net.Uri.encode(prompt)}"
    }
    object Focus : Screen("focus?taskId={taskId}") {
        fun createRoute(taskId: String? = null) = if (taskId == null) "focus" else "focus?taskId=$taskId"
    }
    object WeeklyReview : Screen("weekly_review")
    object History : Screen("history")
    object Trash : Screen("trash")
    object Settings : Screen("settings")
    object Templates : Screen("templates")
}
