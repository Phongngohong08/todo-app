package com.example.todoapplication.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.todoapplication.domain.ScanTextParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.example.todoapplication.R
import com.example.todoapplication.i18n.str

/*
 * [TẦNG UI · CAMERA + ML] Quét tài liệu bằng ML Kit Document Scanner (giao diện chụp của Google Play services:
 * tự bắt mép giấy, khử nghiêng, lọc màu — không cần quyền CAMERA vì app không tự mở camera), rồi nhận chữ
 * bằng ML Kit Text Recognition chạy NGAY TRÊN MÁY (offline, mô hình Latin đọc được tiếng Việt có dấu).
 */

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Nhận chữ trong ảnh [uri] (ML Kit, chạy trên máy). Lỗi → "". */
suspend fun recognizeText(context: Context, uri: Uri): String = suspendCancellableCoroutine { cont ->
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull()
    if (image == null) {
        cont.resume("")
        return@suspendCancellableCoroutine
    }
    recognizer.process(image)
        .addOnSuccessListener { cont.resume(it.text) }
        .addOnFailureListener { cont.resume("") }
        .addOnCompleteListener { recognizer.close() }
}

/**
 * Trả về hàm mở trình quét tài liệu. Xong → [onPages] nhận ảnh từng trang (JPEG, trong cache của app).
 * Máy không có Google Play services / người dùng hủy → báo nhẹ, không crash.
 */
@Composable
fun rememberDocumentScanner(pageLimit: Int = 5, onPages: (List<Uri>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnPages by rememberUpdatedState(onPages)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pages = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages.orEmpty().map { it.imageUri }
            if (pages.isNotEmpty()) currentOnPages(pages)
        }
    }
    return remember(context) {
        {
            val activity = context.findActivity()
            if (activity == null) {
                Toast.makeText(context, str(R.string.scan_cannot_open), Toast.LENGTH_SHORT).show()
            } else {
                val options = GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(true)
                    .setPageLimit(pageLimit)
                    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build()
                GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                    .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
                    .addOnFailureListener {
                        Toast.makeText(context, str(R.string.scan_unsupported), Toast.LENGTH_LONG).show()
                    }
            }
        }
    }
}

/**
 * "Chụp ảnh tạo việc": quét → nhận chữ → hộp xem trước các dòng (bỏ chọn dòng không phải việc) → [onCreate].
 * Trả về hàm bắt đầu quét; hộp thoại do chính composable này vẽ.
 */
@Composable
fun rememberScanToTasks(onCreate: (List<String>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var lines by remember { mutableStateOf<List<String>?>(null) }

    val scan = rememberDocumentScanner { pages ->
        busy = true
        scope.launch {
            val text = pages.map { recognizeText(context, it) }.joinToString("\n")
            busy = false
            val found = ScanTextParser.taskLines(text)
            if (found.isEmpty()) Toast.makeText(context, str(R.string.scan_no_text), Toast.LENGTH_SHORT).show()
            else lines = found
        }
    }

    if (busy) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(stringResource(R.string.scan_recognizing)) },
            text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.scan_on_device)) } }
        )
    }
    lines?.let { found ->
        val checked = remember(found) { mutableStateListOf<Boolean>().apply { addAll(found.map { true }) } }
        AlertDialog(
            onDismissRequest = { lines = null },
            title = { Text(pluralStringResource(R.plurals.scan_create_title, checked.count { it }, checked.count { it })) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.scan_hint), fontSize = 12.sp)
                    found.forEachIndexed { i, line ->
                        Row(Modifier.fillMaxWidth().clickable { checked[i] = !checked[i] }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checked[i], onCheckedChange = { checked[i] = it })
                            Text(line, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = checked.any { it }, onClick = {
                    onCreate(found.filterIndexed { i, _ -> checked[i] })
                    lines = null
                }) { Text(stringResource(R.string.scan_create_tasks)) }
            },
            dismissButton = { TextButton(onClick = { lines = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
    return scan
}
