package com.example.todoapplication.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.todoapplication.data.local.AppDatabase
import com.example.todoapplication.data.local.FocusSessionEntity
import com.example.todoapplication.data.local.HabitCheckinEntity
import com.example.todoapplication.data.local.HabitEntity
import com.example.todoapplication.data.local.ListMetaEntity
import com.example.todoapplication.data.local.SubtaskEntity
import com.example.todoapplication.data.local.TaskEntity
import com.example.todoapplication.data.sync.SyncController
import com.example.todoapplication.ui.utils.toIsoString
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Nội dung tệp sao lưu (JSON). Tăng [version] khi đổi cấu trúc. */
data class BackupFile(
    val app: String = "TaskFlow AI",
    val version: Int = 1,
    val exportedAt: String = "",
    val tasks: List<TaskEntity> = emptyList(),
    val subtasks: List<SubtaskEntity> = emptyList(),
    val categories: List<String> = emptyList(),
    val lists: List<ListMetaEntity> = emptyList(),
    val habits: List<HabitEntity> = emptyList(),
    val checkins: List<HabitCheckinEntity> = emptyList(),
    val focusSessions: List<FocusSessionEntity> = emptyList()
)

data class RestoreResult(val tasks: Int, val habits: Int, val lists: Int)

/**
 * [TẦNG DATA · SAO LƯU] Xuất / nhập dữ liệu qua Storage Access Framework: người dùng tự chọn nơi lưu (Drive,
 * thẻ nhớ, Tải xuống…) bằng ACTION_CREATE_DOCUMENT / ACTION_OPEN_DOCUMENT — app không cần quyền bộ nhớ,
 * chỉ ghi / đọc đúng tệp được cấp qua Uri.
 *
 * Khôi phục là GỘP: chỉ thêm việc / thói quen chưa có trên máy (theo id), đánh dấu "chưa đồng bộ" để gửi lên
 * server — không bao giờ ghi đè dữ liệu mới hơn đang có.
 */
class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val sync: SyncController
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun snapshot(): BackupFile = withContext(Dispatchers.IO) {
        BackupFile(
            exportedAt = toIsoString(System.currentTimeMillis()),
            tasks = db.taskDao().getAllAlive(),
            subtasks = db.subtaskDao().getAll(),
            categories = db.categoryDao().getNames(),
            lists = db.listMetaDao().getAll(),
            habits = db.habitDao().getAllAlive(),
            checkins = db.habitDao().getAllDoneCheckins(),
            focusSessions = db.focusDao().getAll()
        )
    }

    /** Ghi bản sao lưu JSON vào [uri] (do người dùng chọn). Trả số việc đã ghi. */
    suspend fun exportJson(uri: Uri): Int = withContext(Dispatchers.IO) {
        val data = snapshot()
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { gson.toJson(data, it) }
            ?: error("Không mở được tệp để ghi")
        data.tasks.size
    }

    /** Xuất danh sách việc ra CSV (mở được bằng Excel / Google Sheets). */
    suspend fun exportCsv(uri: Uri): Int = withContext(Dispatchers.IO) {
        val tasks = db.taskDao().getAllAlive().sortedBy { it.dueAt ?: Long.MAX_VALUE }
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { w ->
            w.write("﻿") // BOM để Excel đọc đúng tiếng Việt
            w.write(BackupCsv.HEADER)
            w.newLine()
            tasks.forEach { w.write(BackupCsv.row(it)); w.newLine() }
        } ?: error("Không mở được tệp để ghi")
        tasks.size
    }

    /** Tệp sao lưu tạm trong cache/exports để gửi qua bảng Chia sẻ (FileProvider). */
    suspend fun exportToCache(): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "taskflow-backup.json")
        file.bufferedWriter().use { gson.toJson(snapshot(), it) }
        file
    }

    suspend fun restore(uri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val data = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
            gson.fromJson(it, BackupFile::class.java)
        } ?: error("Không đọc được tệp")
        require(data.app == "TaskFlow AI" && data.version >= 1) { "Không phải tệp sao lưu của TaskFlow" }
        merge(data)
    }

    private suspend fun merge(data: BackupFile): RestoreResult {
        val now = System.currentTimeMillis()
        var tasks = 0
        var habits = 0
        var lists = 0
        db.withTransaction {
            val taskDao = db.taskDao()
            val existing = taskDao.getAllIds().toHashSet()
            val newTasks = data.tasks.orEmpty().filter { it.id !in existing }
                .map { it.copy(isDirty = true, isDeleted = false, localVersion = it.localVersion + 1, updatedAt = now) }
            if (newTasks.isNotEmpty()) taskDao.upsertAll(newTasks)
            val newIds = newTasks.mapTo(HashSet()) { it.id }
            db.subtaskDao().upsertAll(data.subtasks.orEmpty().filter { it.taskId in newIds })
            tasks = newTasks.size

            val known = db.categoryDao().getNames().toHashSet()
            var position = db.categoryDao().maxPosition() + 1
            data.categories.orEmpty().filter { it !in known }.forEach {
                db.categoryDao().insert(com.example.todoapplication.data.local.CategoryEntity(it, position++))
                lists++
            }
            val metaKnown = db.listMetaDao().getAll().mapTo(HashSet()) { it.name }
            data.lists.orEmpty().filter { it.name !in metaKnown }.forEach { db.listMetaDao().upsert(it) }
            if (lists > 0 || data.lists.orEmpty().any { it.name !in metaKnown }) db.syncStateDao().markCategoriesDirty()

            val habitDao = db.habitDao()
            val newHabits = data.habits.orEmpty().filter { habitDao.getById(it.id) == null }
                .map { it.copy(isDirty = true, isDeleted = false, localVersion = it.localVersion + 1, updatedAt = now) }
            if (newHabits.isNotEmpty()) habitDao.upsertAll(newHabits)
            val habitIds = newHabits.mapTo(HashSet()) { it.id }
            habitDao.upsertCheckins(data.checkins.orEmpty().filter { it.habitId in habitIds }.map { it.copy(isDirty = true) })
            habits = newHabits.size

            db.focusDao().insertAll(data.focusSessions.orEmpty().map { it.copy(isSynced = false) })
        }
        sync.requestSync()
        return RestoreResult(tasks, habits, lists)
    }
}

/** Định dạng CSV (tách riêng để unit test). */
object BackupCsv {
    const val HEADER = "Tiêu đề,Danh sách,Mục,Ưu tiên,Hạn,Cả ngày,Trạng thái,Nhãn,Thời lượng (phút),Mô tả"

    fun row(t: TaskEntity): String = listOf(
        t.title, t.category, t.section, t.priority,
        t.dueAt?.let { toIsoString(it) }.orEmpty(),
        if (t.dueAllDay) "x" else "",
        if (t.status == "COMPLETED") "Xong" else "Chưa xong",
        t.tags.replace(",", " "),
        if (t.estimatedMinutes > 0) t.estimatedMinutes.toString() else "",
        t.description
    ).joinToString(",") { escape(it) }

    /** Bọc trong ngoặc kép khi có dấu phẩy / ngoặc kép / xuống dòng (RFC 4180). */
    fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}
