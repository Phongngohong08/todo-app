package com.example.todoapplication

import android.content.Context
import android.content.Intent
import com.example.todoapplication.i18n.AppLanguage
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
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
import com.example.todoapplication.ui.components.APP_TABS
import com.example.todoapplication.ui.components.AppNavigationRail
import com.example.todoapplication.ui.components.LocalSharedTransitionScope
import com.example.todoapplication.ui.components.LocalWindowWidthClass
import com.example.todoapplication.ui.components.WithNavScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [TẦNG UI · ĐIỂM VÀO] Activity duy nhất của app (kiến trúc single-activity).
 * Nhiệm vụ: dựng cây UI bằng Compose và đóng vai "router" (NavHost) chuyển giữa các màn.
 * Cũng là nơi nhận "đường dẫn" từ ngoài app: bấm thông báo, widget, app shortcut, chia sẻ từ app khác.
 */
// FragmentActivity (thay vì ComponentActivity): BiometricPrompt của khóa ứng dụng cần nó
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalSharedTransitionApi::class)
class MainActivity : FragmentActivity() {

    // Ngôn ngữ riêng của app trên Android 10–12 (Android 13+ do LocaleManager của hệ thống lo)
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguage.wrap(newBase))

    /** Yêu cầu điều hướng từ Intent (onCreate / onNewIntent) chờ NavHost xử lý. */
    private val pendingLink = MutableStateFlow<PendingLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Màn khởi động: phải gọi TRƯỚC super.onCreate. Hệ thống hiện nền thương hiệu + icon có animation
        // ngay khi bấm icon app (trước cả khi Compose vẽ khung hình đầu), rồi tự chuyển sang theme chính.
        // Đổi ngôn ngữ → hệ thống tạo lại Activity nhưng GIỮ các ViewModel, kèm chữ chúng đã tạo bằng ngôn ngữ cũ
        // (lý do gợi ý, thông báo…). Bỏ hết ViewModel cũ TRƯỚC super.onCreate để mọi màn tính lại; back stack vẫn giữ.
        val language = resources.configuration.locales.toLanguageTags()
        if (savedInstanceState != null && savedInstanceState.getString(KEY_LANGUAGE) != language) viewModelStore.clear()

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
                    val appLocked by AppLock.locked.collectAsStateWithLifecycle()

                    // Giao diện thích ứng: lớp chiều rộng cửa sổ (đổi khi xoay / gập / chia đôi màn hình)
                    val widthClass = calculateWindowSizeClass(this@MainActivity).widthSizeClass
                    val backStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = backStackEntry?.destination?.route
                    val onTab = APP_TABS.any { it.route == currentRoute }

                    CompositionLocalProvider(LocalWindowWidthClass provides widthClass) {
                    Row(Modifier.fillMaxSize()) {
                    if (widthClass != WindowWidthSizeClass.Compact && onTab) AppNavigationRail(navController, currentRoute)
                    // Hiệu ứng "thẻ việc mở rộng thành màn chi tiết" (shared element) giữa các màn trong NavHost
                    SharedTransitionLayout(Modifier.weight(1f)) {
                    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                    NavHost(navController = navController, startDestination = startDestination) {
                        composable(Screen.Login.route) { LoginScreen(navController) }
                        composable(Screen.Register.route) { RegisterScreen(navController) }

                        composable(Screen.Today.route) { WithNavScope(this) { TodayScreen(navController) } }
                        composable(Screen.TaskList.route) { WithNavScope(this) { TaskListScreen(navController) } }
                        composable(Screen.Calendar.route) { CalendarScreen(navController) }
                        composable(Screen.Stats.route) { StatsScreen(navController) }

                        composable(
                            route = Screen.TaskDetail.route,
                            arguments = listOf(navArgument("taskId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val taskId = backStackEntry.arguments?.getString("taskId") ?: "new"
                            WithNavScope(this) { TaskDetailScreen(navController, taskId) }
                        }
                        composable(
                            route = Screen.AICoach.route,
                            arguments = listOf(navArgument("prompt") { type = NavType.StringType; nullable = true; defaultValue = null })
                        ) { entry ->
                            AICoachScreen(navController, initialPrompt = entry.arguments?.getString("prompt"))
                        }
                        composable(
                            route = Screen.Focus.route,
                            arguments = listOf(
                                navArgument("taskId") { type = NavType.StringType; nullable = true; defaultValue = null },
                                navArgument("start") { type = NavType.BoolType; defaultValue = false }
                            )
                        ) { entry ->
                            FocusScreen(navController, entry.arguments?.getString("taskId"), autoStart = entry.arguments?.getBoolean("start") == true)
                        }
                        composable(Screen.WeeklyReview.route) { WeeklyReviewScreen(navController) }
                        composable(Screen.History.route) { HistoryScreen(navController) }
                        composable(Screen.Trash.route) { TrashScreen(navController) }
                        composable(Screen.Settings.route) { SettingsScreen(navController) }
                        composable(Screen.Templates.route) { TemplatesScreen(navController) }
                        composable(Screen.Matrix.route) { MatrixScreen(navController) }
                        composable(Screen.Habits.route) { HabitsScreen(navController) }
                        composable(Screen.Lists.route) { ListsScreen(navController) }
                        composable(
                            route = Screen.ListBoard.route,
                            arguments = listOf(navArgument("name") { type = NavType.StringType })
                        ) { entry -> ListBoardScreen(navController, entry.arguments?.getString("name").orEmpty()) }
                    }
                    }
                    }
                    }
                    }

                    // Che toàn bộ app khi đang khóa (NavHost vẫn giữ nguyên trạng thái bên dưới)
                    if (appLocked) LockScreen()
                }
            }
        }
    }

    // Khóa ứng dụng: quyết định khóa ngay ở onStart (trước khung hình đầu) để nội dung không lộ ra chớp nhoáng
    override fun onStart() {
        super.onStart()
        AppLock.onStart(enabled = ServiceLocator.sessionManager.isLoggedIn() && ServiceLocator.localPrefs.appLockEnabled.value)
    }

    override fun onStop() {
        super.onStop()
        AppLock.onStop()
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
                AppIntents.OPEN_FOCUS_START -> nav.navigate(Screen.Focus.createRoute(autoStart = true)) { launchSingleTop = true }
                AppIntents.OPEN_HABITS -> nav.navigate(Screen.Habits.route) { launchSingleTop = true }
                else -> AppIntents.taskIdOf(target)?.let { nav.navigate(Screen.TaskDetail.createRoute(it)) }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_LANGUAGE, resources.configuration.locales.toLanguageTags())
    }

    private companion object {
        const val SPLASH_EXIT_MS = 300L
        const val KEY_LANGUAGE = "ui_language"
    }
}
