package com.example.todoapplication.data.repository

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.todoapplication.data.local.AppDatabase
import com.example.todoapplication.data.local.AttachmentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Một tệp đính kèm, kèm đường dẫn tệp thật trên máy. */
data class Attachment(val id: String, val taskId: String, val file: File, val mimeType: String, val createdAt: Long) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
}

/**
 * [TẦNG DATA · REPOSITORY] Tệp đính kèm của việc (ảnh / tài liệu quét): chép vào bộ nhớ RIÊNG của app
 * (filesDir/attachments — app khác không đọc được), metadata trong Room, mở ra ngoài bằng FileProvider
 * (content:// có quyền đọc tạm thời) thay vì đưa đường dẫn file:// (bị cấm từ Android 7).
 * Chỉ lưu trên máy, không đồng bộ lên server.
 */
class AttachmentRepository(private val context: Context, db: AppDatabase) {
    private val dao = db.attachmentDao()
    private val dir: File get() = File(context.filesDir, DIR).apply { mkdirs() }

    fun observe(taskId: String): Flow<List<Attachment>> =
        dao.observeForTask(taskId).map { list -> list.map { it.toAttachment() } }

    /** Chép nội dung [source] (ảnh từ Photo Picker, trang quét…) vào bộ nhớ app rồi gắn vào việc. */
    suspend fun add(taskId: String, source: Uri, mimeHint: String? = null): Attachment? = withContext(Dispatchers.IO) {
        val mime = mimeHint ?: context.contentResolver.getType(source) ?: "image/jpeg"
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
        val id = UUID.randomUUID().toString()
        val file = File(dir, "$id.$ext")
        val copied = runCatching {
            context.contentResolver.openInputStream(source)?.use { input -> file.outputStream().use { input.copyTo(it) } } != null
        }.getOrDefault(false)
        if (!copied) {
            file.delete()
            return@withContext null
        }
        val entity = AttachmentEntity(id, taskId, file.name, mime, System.currentTimeMillis())
        dao.insert(entity)
        entity.toAttachment()
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.get(id)?.let { File(dir, it.fileName).delete() }
        dao.delete(id)
    }

    /** content:// để app khác (trình xem ảnh, PDF) mở được tệp — kèm FLAG_GRANT_READ_URI_PERMISSION. */
    fun contentUri(attachment: Attachment): Uri =
        FileProvider.getUriForFile(context, authority(context), attachment.file)

    /** Dọn tệp mồ côi (việc đã bị xóa hẳn → hàng trong Room tự xóa theo CASCADE nhưng tệp còn). */
    suspend fun cleanupOrphans() = withContext(Dispatchers.IO) {
        val known = dao.allFileNames().toHashSet()
        dir.listFiles()?.filter { it.name !in known }?.forEach { it.delete() }
    }

    private fun AttachmentEntity.toAttachment() = Attachment(id, taskId, File(dir, fileName), mimeType, createdAt)

    companion object {
        private const val DIR = "attachments"
        fun authority(context: Context) = context.packageName + ".fileprovider"
    }
}
