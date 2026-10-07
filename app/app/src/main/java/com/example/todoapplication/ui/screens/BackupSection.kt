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
import androidx.compose.ui.res.stringResource
import com.example.todoapplication.R
import com.example.todoapplication.i18n.str
import com.example.todoapplication.i18n.plural

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
            val msg = runCatching { block() }.getOrElse { str(R.string.backup_failed, it.message ?: str(R.string.error_unknown)) }
            busy = false
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    val createJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { run { plural(R.plurals.backup_done, backup.exportJson(it)) } }
    }
    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { run { plural(R.plurals.backup_csv_done, backup.exportCsv(it)) } }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestore = uri
    }

    Text(stringResource(R.string.backup_intro), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    val stamp = LocalDate.now().toString()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(enabled = !busy, onClick = { createJson.launch("taskflow-$stamp.json") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_to_file)) }
        OutlinedButton(enabled = !busy, onClick = { createCsv.launch("taskflow-$stamp.csv") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_export_csv)) }
        OutlinedButton(enabled = !busy, onClick = {
            run {
                val file = backup.exportToCache()
                val uri = FileProvider.getUriForFile(context, AttachmentRepository.authority(context), file)
                val send = Intent(Intent.ACTION_SEND).setType("application/json")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, str(R.string.backup_send)))
                str(R.string.backup_choose_target)
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_send_long)) }
        TextButton(enabled = !busy, onClick = { openBackup.launch(arrayOf("application/json", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.backup_restore_from_file), fontWeight = FontWeight.SemiBold)
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }

    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = { Text(stringResource(R.string.backup_restore_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = null
                    run { backup.restore(uri).let { str(R.string.backup_restored, it.tasks, it.habits, it.lists) } }
                }) { Text(stringResource(R.string.backup_restore)) }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}
