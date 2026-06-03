package com.example.todoapplication

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.todoapplication.data.api.NetworkClient
import com.example.todoapplication.data.local.AppDatabase
import com.example.todoapplication.data.repository.SessionManager
import com.example.todoapplication.data.repository.TodoRepository
import com.example.todoapplication.ui.navigation.Screen
import com.example.todoapplication.ui.screens.*
import com.example.todoapplication.ui.theme.LocalTodoRepository
import com.example.todoapplication.ui.theme.TodoApplicationTheme
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import com.example.todoapplication.data.model.AlarmItem
import com.example.todoapplication.manager.AlarmScheduler
import com.example.todoapplication.ui.theme.LocalScheduler
import java.time.LocalDateTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkOverlayPermission()
        requestGeneralPermissions()

        setContent {
            val sessionManager = remember { SessionManager(this) }
            val repository = remember {
                TodoRepository(
                    NetworkClient.getApiService(this),
                    taskDao = AppDatabase.getDatabase(this).taskDao(),
                    userDao = AppDatabase.getDatabase(this).userDao(),
                    alarmScheduler = AlarmScheduler(this),
                    sessionManager = sessionManager
                )
            }

            CompositionLocalProvider(
                LocalTodoRepository provides repository ,
            ) {
                TodoApplicationTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val navController = rememberNavController()

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
                        }
                    }
                }
            }

        }
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            startActivity(intent)
            Toast.makeText(this, "Vui lòng cấp quyền 'Hiển thị trên ứng dụng khác' để báo thức hoạt động", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestGeneralPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val launcher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
        launcher.launch(permissions.toTypedArray())
    }
}