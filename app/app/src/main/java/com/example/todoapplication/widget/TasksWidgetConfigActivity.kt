package com.example.todoapplication.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import com.example.todoapplication.i18n.AppLanguage
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.ui.screens.listTitle
import com.example.todoapplication.ui.theme.TodoApplicationTheme
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.example.todoapplication.R

/**
 * Màn cấu hình khi đặt (hoặc giữ để sửa) widget danh sách việc: chọn nguồn — Tất cả / Ngày của tôi / một danh sách.
 * Lựa chọn lưu vào state của RIÊNG widget đó, nên đặt được nhiều widget cho nhiều danh sách khác nhau.
 */
class TasksWidgetConfigActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguage.wrap(newBase))

    private val appWidgetId by lazy {
        intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bấm quay lại = hủy đặt widget
        setResult(RESULT_CANCELED, resultIntent())
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            TodoApplicationTheme {
                val lists by ServiceLocator.categoryRepository.observeLists().collectAsState(initial = emptyList())
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
                        Text(stringResource(R.string.widget_config_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            item { SourceRow(stringResource(R.string.widget_source_all)) { pick(WidgetSource.ALL) } }
                            item { SourceRow(stringResource(R.string.today_my_day)) { pick(WidgetSource.MY_DAY) } }
                            items(lists, key = { it.name }) { info ->
                                SourceRow(listTitle(info).ifBlank { info.name }) { pick(WidgetSource.list(info.name)) }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SourceRow(label: String, onClick: () -> Unit) {
        ListItem(
            headlineContent = { Text(label) },
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
        )
    }

    private fun pick(source: String) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@TasksWidgetConfigActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@TasksWidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply { this[WidgetSource.KEY] = source }
            }
            TasksWidget().update(this@TasksWidgetConfigActivity, glanceId)
            setResult(RESULT_OK, resultIntent())
            finish()
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}
