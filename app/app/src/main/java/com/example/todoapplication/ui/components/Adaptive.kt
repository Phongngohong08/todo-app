package com.example.todoapplication.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.example.todoapplication.ui.navigation.Screen

/*
 * [TẦNG UI · GIAO DIỆN THÍCH ỨNG] Theo WindowSizeClass (Material 3):
 *  - Compact (điện thoại dọc): thanh điều hướng dưới.
 *  - Medium (điện thoại ngang, máy gập mở, tablet nhỏ): NavigationRail bên trái.
 *  - Expanded (tablet, máy gập mở ngang): Rail + màn Việc làm chia đôi danh sách | chi tiết.
 * MainActivity tính lớp kích thước từ cửa sổ thật (đổi khi xoay / gập / chia đôi màn hình) và phát qua CompositionLocal.
 */

val LocalWindowWidthClass = compositionLocalOf { WindowWidthSizeClass.Compact }

/** Màn đủ rộng để dùng NavigationRail thay cho thanh dưới. */
@Composable
fun isWideLayout(): Boolean = LocalWindowWidthClass.current != WindowWidthSizeClass.Compact

/** Màn đủ rộng để hiện danh sách và chi tiết cạnh nhau. */
@Composable
fun isTwoPane(): Boolean = LocalWindowWidthClass.current == WindowWidthSizeClass.Expanded

data class TabItem(val route: String, val icon: ImageVector, val label: String)

val APP_TABS = listOf(
    TabItem(Screen.Today.route, Icons.Default.WbSunny, "Hôm nay"),
    TabItem(Screen.TaskList.route, Icons.Default.Checklist, "Việc làm"),
    TabItem(Screen.Calendar.route, Icons.Default.DateRange, "Lịch"),
    TabItem(Screen.Stats.route, Icons.Default.Person, "Tôi")
)

/** Chuyển tab: giữ trạng thái từng tab (saveState / restoreState), không chồng thêm màn. */
fun navigateToTab(navController: NavController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppNavigationRail(navController: NavController, currentRoute: String?) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
        Spacer(Modifier.height(12.dp))
        APP_TABS.forEach { tab ->
            NavigationRailItem(
                selected = currentRoute == tab.route,
                onClick = { if (currentRoute != tab.route) navigateToTab(navController, tab.route) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) }
            )
        }
    }
}
