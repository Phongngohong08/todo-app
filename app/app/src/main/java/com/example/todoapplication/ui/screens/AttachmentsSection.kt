package com.example.todoapplication.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.todoapplication.data.repository.Attachment
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.ui.components.recognizeText
import com.example.todoapplication.ui.components.rememberDocumentScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.res.stringResource
import com.example.todoapplication.R
import com.example.todoapplication.i18n.str

/**
 * Mục "📎 Đính kèm" của màn chi tiết: ảnh / trang tài liệu quét (ML Kit), chọn ảnh bằng Photo Picker của hệ thống
 * (không cần quyền đọc bộ nhớ), bấm để mở bằng app xem ảnh (FileProvider), giữ để nhận chữ hoặc xóa.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AttachmentsSection(
    attachments: List<Attachment>,
    enabled: Boolean,
    onAdd: (List<Uri>) -> Unit,
    onDelete: (Attachment) -> Unit,
    onTextRecognized: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuFor by remember { mutableStateOf<Attachment?>(null) }
    var recognizing by remember { mutableStateOf(false) }

    val scan = rememberDocumentScanner(pageLimit = 10) { pages -> onAdd(pages) }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        if (uris.isNotEmpty()) onAdd(uris)
    }

    if (!enabled) {
        Text(stringResource(R.string.attach_save_first), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    if (attachments.isNotEmpty()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
            items(attachments, key = { it.id }) { att ->
                Box {
                    AttachmentThumb(
                        att,
                        Modifier.combinedClickable(
                            onClick = {
                                val uri = ServiceLocator.attachmentRepository.contentUri(att)
                                val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, att.mimeType)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                try {
                                    context.startActivity(intent)
                                } catch (_: ActivityNotFoundException) {
                                    Toast.makeText(context, str(R.string.attach_no_app), Toast.LENGTH_SHORT).show()
                                }
                            },
                            onLongClick = { menuFor = att }
                        )
                    )
                    DropdownMenu(expanded = menuFor?.id == att.id, onDismissRequest = { menuFor = null }) {
                        if (att.isImage) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.attach_recognize_text)) }, onClick = {
                                menuFor = null
                                recognizing = true
                                scope.launch {
                                    val text = recognizeText(context, Uri.fromFile(att.file))
                                    recognizing = false
                                    if (text.isBlank()) Toast.makeText(context, str(R.string.attach_no_text), Toast.LENGTH_SHORT).show()
                                    else onTextRecognized(text.trim())
                                }
                            })
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.attach_delete), color = MaterialTheme.colorScheme.error) },
                            onClick = { menuFor = null; onDelete(att) }
                        )
                    }
                }
            }
        }
    }
    if (recognizing) {
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = scan) {
            Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.attach_scan), fontSize = 13.sp)
        }
        OutlinedButton(onClick = {
            pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }) {
            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.attach_pick_photo), fontSize = 13.sp)
        }
    }
    if (attachments.isNotEmpty()) {
        Text(
            stringResource(R.string.attach_hint),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/** Ảnh thu nhỏ — giải mã có lấy mẫu (inSampleSize) trên luồng IO để không tốn bộ nhớ với ảnh chụp lớn. */
@Composable
private fun AttachmentThumb(att: Attachment, modifier: Modifier) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, att.file) {
        value = if (att.isImage) withContext(Dispatchers.IO) { decodeSampled(att.file, 240) } else null
    }
    Box(
        modifier.size(84.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val b = bitmap
        if (b != null) Image(b, contentDescription = stringResource(R.string.attach_image), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Default.Description, contentDescription = stringResource(R.string.attach_file), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun decodeSampled(file: File, targetPx: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) sample *= 2
    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()
