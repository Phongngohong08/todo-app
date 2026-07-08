package com.example.todoapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.todoapplication.data.model.ParsedTask
import com.example.todoapplication.data.repository.QuickAddDraft
import com.example.todoapplication.ui.navigation.Screen

/** Một mẫu nhiệm vụ gợi ý sẵn. */
data class TaskTemplate(
    val emoji: String,
    val title: String,
    val category: String,
    val recurrence: String = "NONE"
)

data class TemplateGroup(val name: String, val items: List<TaskTemplate>)

/** Danh sách mẫu nhiệm vụ theo nhóm — giống màn "Mẫu nhiệm vụ" của app tham khảo. */
val TEMPLATE_GROUPS = listOf(
    TemplateGroup(
        "Sức khỏe",
        listOf(
            TaskTemplate("💧", "Uống nước", "PERSONAL", "DAILY"),
            TaskTemplate("🦷", "Đánh răng", "PERSONAL", "DAILY"),
            TaskTemplate("🚿", "Tắm", "PERSONAL", "DAILY"),
            TaskTemplate("😴", "Đi ngủ sớm", "PERSONAL", "DAILY"),
            TaskTemplate("🌅", "Dậy sớm", "PERSONAL", "DAILY"),
            TaskTemplate("💊", "Uống thuốc", "PERSONAL", "DAILY"),
            TaskTemplate("☕", "Nghỉ ngơi một lát", "PERSONAL"),
            TaskTemplate("🍎", "Ăn trái cây", "PERSONAL"),
            TaskTemplate("🏃", "Tập thể dục", "PERSONAL", "DAILY")
        )
    ),
    TemplateGroup(
        "Cuộc sống",
        listOf(
            TaskTemplate("🛒", "Đi mua sắm", "OTHER"),
            TaskTemplate("🧹", "Dọn dẹp nhà", "OTHER", "WEEKLY"),
            TaskTemplate("🍳", "Nấu ăn", "OTHER"),
            TaskTemplate("📞", "Gọi điện gia đình", "PERSONAL"),
            TaskTemplate("🐶", "Cho thú cưng ăn", "OTHER", "DAILY"),
            TaskTemplate("🎂", "Sinh nhật", "PERSONAL", "MONTHLY")
        )
    ),
    TemplateGroup(
        "Công việc",
        listOf(
            TaskTemplate("📧", "Gửi email", "WORK"),
            TaskTemplate("📝", "Viết báo cáo", "WORK"),
            TaskTemplate("🤝", "Họp nhóm", "WORK"),
            TaskTemplate("📅", "Lập kế hoạch ngày", "WORK", "DAILY")
        )
    ),
    TemplateGroup(
        "Học tập",
        listOf(
            TaskTemplate("📖", "Đọc sách", "WORK"),
            TaskTemplate("🌐", "Học ngoại ngữ", "WORK", "DAILY"),
            TaskTemplate("✏️", "Làm bài tập", "WORK")
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
/** [TẦNG UI · MÀN HÌNH] Mẫu nhiệm vụ — danh sách mẫu tĩnh (TEMPLATE_GROUPS); chọn 1 mẫu để tạo nhanh task. */
fun TemplatesScreen(navController: NavController) {
    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text("Mẫu nhiệm vụ", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TEMPLATE_GROUPS.forEach { group ->
                item(key = "h_${group.name}") {
                    Text(
                        group.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                group.items.forEach { t ->
                    item(key = "${group.name}_${t.title}") {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth().clickable {
                            QuickAddDraft.set(
                                ParsedTask(title = t.title, category = t.category)
                            )
                            // Lưu kèm recurrence qua draft mở rộng không có -> mở chi tiết để người dùng chỉnh giờ/lặp
                            navController.navigate(Screen.TaskDetail.createRoute("new"))
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(t.emoji, fontSize = 20.sp)
                            Spacer(Modifier.width(14.dp))
                            Text(t.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            if (t.recurrence != "NONE") {
                                Icon(Icons.Default.Refresh, contentDescription = "Lặp lại", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                                Spacer(Modifier.width(8.dp))
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    }
                }
            }
        }
    }
}
