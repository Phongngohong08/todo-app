package com.example.todoapplication.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.todoapplication.data.repository.AttachmentRepository
import com.example.todoapplication.di.ServiceLocator
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Mục "💾 Dữ liệu" của Thiết lập — Storage Access Framework: người dùng chọn nơi lưu / tệp cần mở,
 * app không xin quyền bộ nhớ. "Gửi bản sao lưu" dùng FileProvider + bảng Chia sẻ.
 */
@Composable
fun BackupSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backup = ServiceLocator.backupManager
    var busy by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }

    fun run(block: suspend () -> String) {
        busy = true
        scope.launch {
            val msg = runCatching { block() }.getOrElse { "Thất bại: ${it.message ?: "lỗi không rõ"}" }
            busy = false
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    val createJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { run { "Đã sao lưu ${backup.exportJson(it)} việc" } }
    }
    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { run { "Đã xuất ${backup.exportCsv(it)} việc ra CSV" } }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestore = uri
    }

    Text("Sao lưu ra tệp do bạn chọn chỗ lưu (Drive, thẻ nhớ…), khôi phục trên máy mới, hoặc xuất bảng tính.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    val stamp = LocalDate.now().toString()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(enabled = !busy, onClick = { createJson.launch("taskflow-$stamp.json") }, modifier = Modifier.fillMaxWidth()) { Text("Sao lưu ra tệp (JSON)") }
        OutlinedButton(enabled = !busy, onClick = { createCsv.launch("taskflow-$stamp.csv") }, modifier = Modifier.fillMaxWidth()) { Text("Xuất danh sách việc (CSV / Excel)") }
        OutlinedButton(enabled = !busy, onClick = {
            run {
                val file = backup.exportToCache()
                val uri = FileProvider.getUriForFile(context, AttachmentRepository.authority(context), file)
                val send = Intent(Intent.ACTION_SEND).setType("application/json")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, "Gửi bản sao lưu"))
                "Chọn nơi gửi bản sao lưu"
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Gửi bản sao lưu (email, Zalo, Drive…)") }
        TextButton(enabled = !busy, onClick = { openBackup.launch(arrayOf("application/json", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text("Khôi phục từ tệp sao lưu…", fontWeight = FontWeight.SemiBold)
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }

    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Khôi phục dữ liệu?") },
            text = { Text("Chỉ THÊM những việc, danh sách, thói quen chưa có trên máy — không ghi đè dữ liệu hiện tại. Sau đó sẽ đồng bộ lên máy chủ.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = null
                    run { backup.restore(uri).let { "Đã khôi phục ${it.tasks} việc, ${it.habits} thói quen, ${it.lists} danh sách" } }
                }) { Text("Khôi phục") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Hủy") } }
        )
    }
}
