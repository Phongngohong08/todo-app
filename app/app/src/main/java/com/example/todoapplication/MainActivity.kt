package com.example.todoapplication

import android.content.Intent
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.todoapplication.data.repository.SessionEvents
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.ui.navigation.AppIntents
import com.example.todoapplication.ui.navigation.PendingLink
import com.example.todoapplication.ui.navigation.QuickCreateRequest
import com.example.todoapplication.ui.navigation.Screen
import com.example.todoapplication.ui.screens.*
import com.example.todoapplication.ui.theme.TodoApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [TẦNG UI · ĐIỂM VÀO] Activity duy nhất của app (kiến trúc single-activity).
 * Nhiệm vụ: dựng cây UI bằng Compose và đóng vai "router" (NavHost) chuyển giữa các màn.
 * Cũng là nơi nhận "đường dẫn" từ ngoài app: bấm thông báo, widget, app shortcut, chia sẻ từ app khác.
 */
class MainActivity : ComponentActivity() {

    /** Yêu cầu điều hướng từ Intent (onCreate / onNewIntent) chờ NavHost xử lý. */
    private val pendingLink = MutableStateFlow<PendingLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Màn khởi động: phải gọi TRƯỚC super.onCreate. Hệ thống hiện nền thương hiệu + icon có animation
        // ngay khi bấm icon app (trước cả khi Compose vẽ khung hình đầu), rồi tự chuyển sang theme chính.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setOnExitAnimationListener { splash ->
            splash.iconView.animate()
                .scaleX(1.2f).scaleY(1.2f)
                .setDuration(SPLASH_EXIT_MS)
                .start()
            splash.view.animate()
                .alpha(0f)
                .setDuration(SPLASH_EXIT_MS)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { splash.remove() }
                .start()
        }
        // Chỉ xử lý Intent lúc mở lần đầu (không phải khi hệ thống tạo lại Activity sau xoay màn)
        if (savedInstanceState == null) pendingLink.value = PendingLink.from(intent)

        enableEdgeToEdge()
        setContent {
            TodoApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val sessionManager = ServiceLocator.sessionManager
                    // Quyền thông báo KHÔNG xin ở đây nữa: xin đúng lúc người dùng đặt nhắc việc
                    // (xem rememberNotificationPermissionRequest).

                    // Mỗi lần app trở lại màn hình (ON_START): kéo thay đổi từ thiết bị khác, đẩy thay đổi còn tồn đọng
                    LifecycleEventEffect(Lifecycle.Event.ON_START) {
                        if (sessionManager.isLoggedIn()) ServiceLocator.syncController.requestSync()
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

                    // Đường dẫn từ ngoài app → điều hướng (chỉ khi đã đăng nhập)
                    val link by pendingLink.collectAsStateWithLifecycle()
                    LaunchedEffect(link) {
                        val l = link ?: return@LaunchedEffect
                        pendingLink.value = null
                        if (sessionManager.isLoggedIn()) handleLink(navController, l)
                    }

                    // Chọn màn bắt đầu MỘT lần (remember): đăng xuất sau đó đã tự điều hướng về Login,
                    // không để startDestination của NavHost đổi giữa chừng khi recompose.
                    val startDestination = remember {
                        if (sessionManager.isLoggedIn()) Screen.Today.route else Screen.Login.route
                    }

                    // NavHost = "bảng định tuyến": route nào → hiển thị Composable nào (như router ở backend)
                    NavHost(navController = navController, startDestination = startDestination) {
                        composable(Screen.Login.route) { LoginScreen(navController) }
                        composable(Screen.Register.route) { RegisterScreen(navController) }

                        composable(Screen.Today.route) { TodayScreen(navController) }
                        composable(Screen.TaskList.route) { TaskListScreen(navController) }
                        composable(Screen.Calendar.route) { CalendarScreen(navController) }
                        composable(Screen.Stats.route) { StatsScreen(navController) }

                        composable(
                            route = Screen.TaskDetail.route,
                            arguments = listOf(navArgument("taskId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val taskId = backStackEntry.arguments?.getString("taskId") ?: "new"
                            TaskDetailScreen(navController, taskId)
                        }
                        composable(
                            route = Screen.AICoach.route,
                            arguments = listOf(navArgument("prompt") { type = NavType.StringType; nullable = true; defaultValue = null })
                        ) { entry ->
                            AICoachScreen(navController, initialPrompt = entry.arguments?.getString("prompt"))
                        }
                        composable(
                            route = Screen.Focus.route,
                            arguments = listOf(navArgument("taskId") { type = NavType.StringType; nullable = true; defaultValue = null })
                        ) { entry ->
                            FocusScreen(navController, entry.arguments?.getString("taskId"))
                        }
                        composable(Screen.WeeklyReview.route) { WeeklyReviewScreen(navController) }
                        composable(Screen.History.route) { HistoryScreen(navController) }
                        composable(Screen.Trash.route) { TrashScreen(navController) }
                        composable(Screen.Settings.route) { SettingsScreen(navController) }
                        composable(Screen.Templates.route) { TemplatesScreen(navController) }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingLink.value = PendingLink.from(intent)
    }

    private fun handleLink(nav: NavController, link: PendingLink) {
        fun goToday() = nav.navigate(Screen.Today.route) {
            popUpTo(nav.graph.id) { inclusive = true }
            launchSingleTop = true
        }
        when (link) {
            is PendingLink.SharedText -> {
                QuickCreateRequest.pending.value = link.text
                goToday()
            }
            is PendingLink.Open -> when (val target = link.target) {
                AppIntents.OPEN_TODAY -> goToday()
                AppIntents.OPEN_ADD -> {
                    QuickCreateRequest.pending.value = ""
                    goToday()
                }
                AppIntents.OPEN_REVIEW -> nav.navigate(Screen.WeeklyReview.route) { launchSingleTop = true }
                AppIntents.OPEN_FOCUS -> nav.navigate(Screen.Focus.createRoute()) { launchSingleTop = true }
                else -> AppIntents.taskIdOf(target)?.let { nav.navigate(Screen.TaskDetail.createRoute(it)) }
            }
        }
    }

    private companion object {
        const val SPLASH_EXIT_MS = 300L
    }
}
