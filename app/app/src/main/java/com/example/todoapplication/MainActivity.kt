package com.example.todoapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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