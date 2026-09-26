package com.example.todoapplication.data.sync

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.todoapplication.data.local.AppDatabase
import com.example.todoapplication.data.local.CategoryEntity
import com.example.todoapplication.data.repository.TaskRepository
import com.example.todoapplication.domain.RecurrenceRules
import com.example.todoapplication.domain.model.Recurrence
import com.example.todoapplication.domain.model.Subtask
import com.example.todoapplication.domain.model.TaskDraft
import com.example.todoapplication.domain.model.TaskStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Kiểm tra giao thức đồng bộ trên Room thật (in-memory) với server giả — các tình huống offline-first
 * quan trọng: tạo khi offline, mất mạng giữa chừng, thay đổi từ máy khác, hoàn tác xóa, việc lặp, sửa khi đang gửi.
 */
@RunWith(AndroidJUnit4::class)
class SyncEngineTest {

    private lateinit var db: AppDatabase
    private lateinit var server: FakeServer
    private lateinit var repo: TaskRepository
    private lateinit var engine: SyncEngine

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        server = FakeServer()
        repo = TaskRepository(db, effects = { _, _ -> })
        engine = SyncEngine(db, server, currentUserId = { "u1" }, listener = { _, _ -> })
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun taskCreatedOnDeviceIsPushedWithTheSameId() = runBlocking {
        val task = repo.create(TaskDraft(title = "Mua sữa"))
        assertEquals(1, repo.pendingSyncCount())

        assertEquals(SyncResult.Success, engine.sync())

        assertEquals("Mua sữa", server.tasks[task.id]?.title)
        assertEquals(0, repo.pendingSyncCount())
    }

    @Test
    fun networkFailureKeepsChangesUntilTheNextSync() = runBlocking {
        server.offline = true
        val task = repo.create(TaskDraft(title = "Viết báo cáo"))

        assertEquals(SyncResult.NetworkError, engine.sync())
        assertEquals(1, repo.pendingSyncCount())
        assertNotNull("vẫn xem/sửa được trên máy", repo.getTask(task.id))

        server.offline = false
        assertEquals(SyncResult.Success, engine.sync())
        assertEquals(0, repo.pendingSyncCount())
        assertNotNull(server.tasks[task.id])
    }

    @Test
    fun changesFromAnotherDeviceArePulled() = runBlocking {
        engine.sync()
        server.putFromOtherDevice("x-1", "Từ điện thoại khác")
        engine.sync()
        assertEquals("Từ điện thoại khác", repo.getTask("x-1")?.title)

        server.deleteFromOtherDevice("x-1")
        engine.sync()
        assertNull(repo.getTask("x-1"))
    }

    @Test
    fun deleteCanBeUndoneEvenAfterItWasSynced() = runBlocking {
        val task = repo.create(TaskDraft(title = "Đi chợ"))
        engine.sync()

        repo.delete(task.id)
        assertNull(repo.getTask(task.id))
        engine.sync()
        assertTrue(server.isDeleted(task.id))

        // Người dùng bấm "Hoàn tác" sau khi việc xóa đã đồng bộ xong
        assertTrue(repo.restore(task.id))
        engine.sync()
        assertFalse(server.isDeleted(task.id))
        assertNotNull(repo.getTask(task.id))
    }

    @Test
    fun completingRecurringTaskOfflineCreatesTheSameOccurrenceAsTheServer() = runBlocking {
        val due = System.currentTimeMillis() - 3_600_000L
        val task = repo.create(TaskDraft(title = "Uống thuốc", dueAt = due, recurrence = Recurrence.DAILY))
        engine.sync()

        server.offline = true
        repo.setCompleted(task.id, completed = true)
        val childId = RecurrenceRules.nextOccurrenceId(task.id)
        val child = repo.getTask(childId)
        assertNotNull("lần lặp kế tiếp hiện ngay cả khi offline", child)
        assertTrue(child!!.dueAt!! > System.currentTimeMillis())

        server.offline = false
        engine.sync()
        assertEquals(TaskStatus.COMPLETED, server.tasks[task.id]?.status)
        assertEquals(task.id, server.tasks[childId]?.spawnedFrom)

        // Hoàn thành nhầm → mở lại: lần lặp đã sinh bị thu hồi ở cả hai phía
        repo.setCompleted(task.id, completed = false)
        assertNull(repo.getTask(childId))
        engine.sync()
        assertTrue(server.isDeleted(childId))
        assertEquals(TaskStatus.TODO, server.tasks[task.id]?.status)
    }

    @Test
    fun subtasksTravelInsideTheTask() = runBlocking {
        val task = repo.create(
            TaskDraft(title = "Chuẩn bị thuyết trình"),
            subtasks = listOf(Subtask(UUID.randomUUID().toString(), "Làm slide", isDone = false, position = 0))
        )
        repo.addSubtask(task.id, "Tập nói")
        engine.sync()

        assertEquals(listOf("Làm slide", "Tập nói"), server.tasks[task.id]?.subtasks?.map { it.title })
        assertEquals(2, repo.getTask(task.id)?.let { db.subtaskDao().getForTask(it.id).size })
    }

    @Test
    fun editMadeWhileThePushIsInFlightIsNotLost() = runBlocking {
        val task = repo.create(TaskDraft(title = "Bản 1"))
        var edited = false
        server.onSave = { id ->
            if (!edited && id == task.id) {
                edited = true
                repo.update(task.id, TaskDraft(title = "Bản 2")) // người dùng sửa tiếp khi request đang bay
            }
        }

        engine.sync()

        assertEquals("Bản 2", repo.getTask(task.id)?.title)
        assertEquals("bản sửa sau vẫn được gửi ở vòng kế tiếp", "Bản 2", server.tasks[task.id]?.title)
        assertEquals(0, repo.pendingSyncCount())
    }

    @Test
    fun loggingOutDuringSyncDiscardsDownloadedData() = runBlocking {
        var userId: String? = "u1"
        val engine = SyncEngine(db, server, currentUserId = { userId }, listener = { _, _ -> })
        server.putFromOtherDevice("x-2", "Của tài khoản cũ")
        server.onSave = null
        // Đăng xuất xảy ra trước khi kết quả kéo về được ghi
        userId = null
        assertEquals(SyncResult.NotLoggedIn, engine.sync())
        assertNull(repo.getTask("x-2"))
    }

    @Test
    fun customCategoriesAreSynced() = runBlocking {
        db.categoryDao().insert(CategoryEntity("Học tập", 0))
        db.syncStateDao().markCategoriesDirty()

        engine.sync()

        assertEquals(listOf("Học tập"), server.categories)
        assertFalse(db.syncStateDao().get()!!.categoriesDirty)
    }
}
