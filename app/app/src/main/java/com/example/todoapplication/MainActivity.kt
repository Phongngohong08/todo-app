package com.example.todoapplication

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.todoapplication.data.repository.SessionEvents
import com.example.todoapplication.data.repository.SessionManager
import com.example.todoapplication.ui.navigation.Screen
import com.example.todoapplication.ui.screens.*
import com.example.todoapplication.ui.theme.TodoApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TodoApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val sessionManager = SessionManager(this)

                    // Xin quyền thông báo (Android 13+) để gửi nhắc nhở công việc
                    val notifPermissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { /* kết quả không cần xử lý: nếu từ chối thì đơn giản không nhắc */ }
                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    // Khi phiên hết hiệu lực (refresh thất bại), đưa người dùng về màn Login và xóa backstack
                    LaunchedEffect(Unit) {
                        SessionEvents.forcedLogout.collect {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(navController.graph.id) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }

                    // Determine start destination
                    val startDestination = if (sessionManager.isLoggedIn()) {
                        Screen.TaskList.route
                    } else {
                        Screen.Login.route
                    }

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        composable(Screen.Login.route) {
                            LoginScreen(navController)
                        }
                        composable(Screen.Register.route) {
                            RegisterScreen(navController)
                        }
                        composable(Screen.TaskList.route) {
                            TaskListScreen(navController)
                        }
                        composable(
                            route = Screen.TaskDetail.route,
                            arguments = listOf(
                                navArgument("taskId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val taskId = backStackEntry.arguments?.getString("taskId") ?: "new"
                            TaskDetailScreen(navController, taskId)
                        }
                        composable(Screen.DailyPlan.route) {
                            DailyPlanScreen(navController)
                        }
                        composable(Screen.AICoach.route) {
                            AICoachScreen(navController)
                        }
                        composable(Screen.Stats.route) {
                            StatsScreen(navController)
                        }
                        composable(Screen.Settings.route) {
                            SettingsScreen(navController)
                        }
                        composable(Screen.Calendar.route) {
                            CalendarScreen(navController)
                        }
                        composable(Screen.Templates.route) {
                            TemplatesScreen(navController)
                        }
                    }
                }
            }
        }
    }
}