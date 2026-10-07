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
import com.example.todoapplication.domain.model.TaskDraft
import com.example.todoapplication.data.repository.QuickAddDraft
import com.example.todoapplication.ui.navigation.Screen
import androidx.compose.ui.res.stringResource
import com.example.todoapplication.R
import com.example.todoapplication.i18n.str

/** Một mẫu nhiệm vụ gợi ý sẵn. */
data class TaskTemplate(
    val emoji: String,
    val title: String,
    val category: String,
    val recurrence: String = "NONE"
)

data class TemplateGroup(val name: String, val items: List<TaskTemplate>)

/** Danh sách mẫu nhiệm vụ theo nhóm — giống màn "Mẫu nhiệm vụ" của app tham khảo. */
fun templateGroups(): List<TemplateGroup> = listOf(
    TemplateGroup(
        str(R.string.tpl_group_health),
        listOf(
            TaskTemplate("💧", str(R.string.tpl_drink_water), "PERSONAL", "DAILY"),
            TaskTemplate("🦷", str(R.string.tpl_brush_teeth), "PERSONAL", "DAILY"),
            TaskTemplate("🚿", str(R.string.tpl_shower), "PERSONAL", "DAILY"),
            TaskTemplate("😴", str(R.string.tpl_sleep_early), "PERSONAL", "DAILY"),
            TaskTemplate("🌅", str(R.string.tpl_wake_early), "PERSONAL", "DAILY"),
            TaskTemplate("💊", str(R.string.tpl_take_medicine), "PERSONAL", "DAILY"),
            TaskTemplate("☕", str(R.string.tpl_take_break), "PERSONAL"),
            TaskTemplate("🍎", str(R.string.tpl_eat_fruit), "PERSONAL"),
            TaskTemplate("🏃", str(R.string.tpl_exercise), "PERSONAL", "DAILY")
        )
    ),
    TemplateGroup(
        str(R.string.tpl_group_life),
        listOf(
            TaskTemplate("🛒", str(R.string.tpl_shopping), "OTHER"),
            TaskTemplate("🧹", str(R.string.tpl_clean_house), "OTHER", "WEEKLY"),
            TaskTemplate("🍳", str(R.string.tpl_cook), "OTHER"),
            TaskTemplate("📞", str(R.string.tpl_call_family), "PERSONAL"),
            TaskTemplate("🐶", str(R.string.tpl_feed_pet), "OTHER", "DAILY"),
            TaskTemplate("🎂", str(R.string.tpl_birthday), "PERSONAL", "MONTHLY")
        )
    ),
    TemplateGroup(
        str(R.string.tpl_group_work),
        listOf(
            TaskTemplate("📧", str(R.string.tpl_send_email), "WORK"),
            TaskTemplate("📝", str(R.string.tpl_write_report), "WORK"),
            TaskTemplate("🤝", str(R.string.tpl_team_meeting), "WORK"),
            TaskTemplate("📅", str(R.string.tpl_plan_day), "WORK", "DAILY")
        )
    ),
    TemplateGroup(
        str(R.string.tpl_group_study),
        listOf(
            TaskTemplate("📖", str(R.string.tpl_read_book), "WORK"),
            TaskTemplate("🌐", str(R.string.tpl_learn_language), "WORK", "DAILY"),
            TaskTemplate("✏️", str(R.string.tpl_homework), "WORK")
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(stringResource(R.string.templates_title), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        // LazyColumn = danh sách cuộn chỉ dựng item đang thấy (hiệu năng cao). Bên trong dùng item{} để
        // phát từng dòng: mỗi nhóm 1 tiêu đề + các mẫu. key giúp Compose nhận diện item khi danh sách đổi.
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            templateGroups().forEach { group ->
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
                                QuickAddDraft.Prefill(TaskDraft(title = t.title, category = t.category))
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
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.templates_repeats), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
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
