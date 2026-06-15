package com.example.todoapplication.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.example.todoapplication.ui.navigation.Screen

/** Thanh điều hướng floating pill — lấy màu từ theme. */
@Composable
fun AppBottomBar(navController: NavController, activeTab: Int) {
    data class NavItem(val route: String, val icon: ImageVector, val label: String)

    val items = listOf(
        NavItem(Screen.TaskList.route, Icons.Default.List, "Việc làm"),
        NavItem(Screen.DailyPlan.route, Icons.Default.DateRange, "Kế hoạch"),
        NavItem(Screen.AICoach.route, Icons.Default.Send, "AI Coach"),
        NavItem(Screen.Stats.route, Icons.Default.AccountBox, "Thống kê")
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .navigationBarsPadding()
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    val selected = activeTab == index
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        item.icon,
                                        contentDescription = item.label,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        item.label,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    item.icon,
                                    contentDescription = item.label,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
