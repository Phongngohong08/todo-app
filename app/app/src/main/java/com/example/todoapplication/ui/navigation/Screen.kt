package com.example.todoapplication.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object TaskList : Screen("task_list")
    object TaskDetail : Screen("task_detail/{taskId}") {
        fun createRoute(taskId: String) = "task_detail/$taskId"
    }
    object DailyPlan : Screen("daily_plan")
    object AICoach : Screen("ai_coach")
    object Stats : Screen("stats")
    object Settings : Screen("settings")
}
