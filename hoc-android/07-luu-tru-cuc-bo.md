# Bài 07 — Lưu trữ cục bộ (Room & SharedPreferences)

App cần nhớ dữ liệu *trên máy* để: xem task khi mất mạng, lưu token đăng nhập, ghi cài đặt. Android có ba mức lưu trữ; app này dùng hai:

| Cần lưu gì | Công cụ | Ví dụ trong app |
|---|---|---|
| Dữ liệu có cấu trúc, nhiều bản ghi, truy vấn | **Room** (SQLite ORM) | cache task, subtask |
| Cặp key–value nhỏ | **SharedPreferences** | token, cài đặt, danh mục |
| Key–value *nhạy cảm* (mã hóa) | **EncryptedSharedPreferences** | JWT + refresh token |

## Phần A — Room: SQLite có ORM

Room là thư viện ORM chính chủ của Google, bọc SQLite. Nếu bạn từng dùng GORM/sqlx ở Go, cảm giác rất quen: bạn khai báo **Entity** (bảng), **DAO** (truy vấn), và **Database**; Room **sinh code** hiện thực lúc build (nhờ `ksp`, bài 00).

### 1. Entity = một bảng
[TaskCacheEntity.kt](../app/app/src/main/java/com/example/todoapplication/data/local/TaskCacheEntity.kt):
```kotlin
@Entity(tableName = "task_cache")
data class TaskCacheEntity(
    @PrimaryKey val id: String,
    val title: String,
    val status: String,
    ...
    val cachedAt: Long          // mốc thời gian cache (epoch millis)
)
```
- `@Entity(tableName = "task_cache")` → tạo bảng `task_cache`, mỗi field là một cột.
- `@PrimaryKey` → khóa chính. Giống struct tag ORM ở Go.

Lưu ý thiết kế: `TaskCacheEntity` **tách biệt** với `Task` (DTO mạng ở [Models.kt](../app/app/src/main/java/com/example/todoapplication/data/model/Models.kt)). Đây là chủ ý tốt: model **DB** và model **API** là hai thứ khác nhau, tiến hóa độc lập. Việc chuyển đổi nằm ở các hàm `toEntity()` / `toTask()` trong [TaskCacheRepository.kt:29](../app/app/src/main/java/com/example/todoapplication/data/repository/TaskCacheRepository.kt#L29).

### 2. DAO = tập truy vấn
[Daos.kt](../app/app/src/main/java/com/example/todoapplication/data/local/Daos.kt):
```kotlin
@Dao
interface TaskCacheDao {
    @Query("SELECT * FROM task_cache ORDER BY cachedAt ASC")
    suspend fun getAll(): List<TaskCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TaskCacheEntity>)

    @Query("DELETE FROM task_cache")
    suspend fun clear()
}
```
- Bạn **chỉ viết interface + câu SQL**; Room sinh phần hiện thực (xem `app/app/build/generated/.../TaskCacheDao_Impl.kt` nếu tò mò).
- **Room kiểm tra SQL *lúc biên dịch*** — gõ sai tên cột là build đỏ ngay, không đợi tới lúc chạy. Rất an toàn.
- `@Insert(onConflict = REPLACE)` = "upsert": trùng khóa chính thì ghi đè.
- `suspend` → truy vấn chạy nền, không chặn UI (bài 01). Room *bắt buộc* điều này để bạn không lỡ tay query trên main thread.
- `SubtaskDao` cho thấy thêm `@Update`, `@Delete`, và query có tham số `:taskId` (bind param, chống SQL injection).

### 3. Database = điểm gom + singleton
[AppDatabase.kt](../app/app/src/main/java/com/example/todoapplication/data/local/AppDatabase.kt):
```kotlin
@Database(entities = [TaskCacheEntity::class, SubtaskEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskCacheDao(): TaskCacheDao
    abstract fun subtaskDao(): SubtaskDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "todo_local.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
```
Nhiều thứ đáng học ở đây:
- **`version = 4`** — số phiên bản schema. Đổi cấu trúc bảng thì tăng version, và cần **migration** (như migration ở [`backend/migrations/`](../backend/migrations/) của bạn).
- **`fallbackToDestructiveMigration()`** — "nếu không có migration thì **xóa sạch DB tạo lại**". Chấp nhận được vì đây chỉ là *cache* (mất thì tải lại từ mạng), **không phải nguồn dữ liệu gốc**. ⚠️ Với dữ liệu quan trọng thật thì KHÔNG bao giờ dùng cái này — phải viết migration.
- **Singleton kiểu double-checked locking** (`INSTANCE ?: synchronized ...`) + `@Volatile` — mở SQLite tốn kém nên chỉ tạo *một* instance toàn app. Đây đúng là mẫu concurrency backend bạn đã biết, viết bằng Kotlin.
- Tạo DB cần **Application context** (sống lâu), không dùng Activity context — nếu không sẽ rò rỉ bộ nhớ.

### 4. Ghép lại: tính năng "xem offline"
Nhớ [TaskRepository.loadTasks](../app/app/src/main/java/com/example/todoapplication/data/repository/TaskRepository.kt#L25) ở bài 06? Đây là mảnh còn lại. [TaskCacheRepository](../app/app/src/main/java/com/example/todoapplication/data/repository/TaskCacheRepository.kt):
```kotlin
object TaskCacheRepository {
    suspend fun cache(context: Context, tasks: List<Task>) {   // gọi khi tải mạng thành công
        val dao = AppDatabase.get(context).taskCacheDao()
        dao.clear()
        dao.insertAll(tasks.map { it.toEntity(now) })
        TasksWidgetProvider.refresh(context)   // cập nhật cả widget màn hình chính
    }
    suspend fun getCached(context: Context): List<Task> =    // gọi khi mất mạng
        AppDatabase.get(context).taskCacheDao().getAll().map { it.toTask() }
}
```
Luồng **offline-first read** hoàn chỉnh:
1. Online: tải từ API → **ghi đè cache** vào Room → hiển thị.
2. Mất mạng: API ném lỗi → đọc `getCached()` từ Room → hiển thị kèm cờ `isOffline = true`.
3. UI thấy `isOffline` → hiện banner "Đang offline". State này chảy từ Repo → ViewModel (`TaskListUiState.isOffline`) → Screen. Vòng tròn khép kín của cả app.

Đây là **case study tuyệt vời** để thấy mọi tầng phối hợp: API + Room + Repository + ViewModel + UI + Widget.

## Phần B — SharedPreferences: key–value nhỏ

Với cài đặt lặt vặt (đã đăng nhập chưa, theme, danh mục), dùng dao búa Room là thừa. **SharedPreferences** là kho key–value đơn giản, lưu trong một file XML nhỏ của app.

### EncryptedSharedPreferences cho token
Token JWT/refresh là **dữ liệu nhạy cảm** — không nên lưu thô. [SessionManager.kt](../app/app/src/main/java/com/example/todoapplication/data/repository/SessionManager.kt) dùng bản **mã hóa**:
```kotlin
class SessionManager(context: Context) {
    private val prefs: SharedPreferences = createEncryptedPrefs(context)

    fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit().apply {
            putString(KEY_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            apply()                       // ghi bất đồng bộ xuống đĩa
        }
    }
    fun getAuthToken(): String? = prefs.getString(KEY_TOKEN, null)
    fun isLoggedIn(): Boolean = getAuthToken() != null
    fun logout() = prefs.edit().clear().apply()
}
```
- **`EncryptedSharedPreferences`** ([dòng 78](../app/app/src/main/java/com/example/todoapplication/data/repository/SessionManager.kt#L78)) mã hóa cả key lẫn value bằng khóa nằm trong **Android Keystore** (vùng phần cứng bảo mật). Kẻ có file cũng không đọc được token.
- **Đọc/ghi:** `getString`, `.edit().putString(...).apply()`. `.apply()` ghi nền (không chặn); `.commit()` ghi đồng bộ (hiếm khi cần).
- **Xử lý hỏng file** ([dòng 73-92](../app/app/src/main/java/com/example/todoapplication/data/repository/SessionManager.kt#L73)): nếu file mã hóa hỏng (mất khóa Keystore sau khi khôi phục backup sang máy khác), thay vì để app **crash lặp**, code **xóa file rồi tạo lại** — hệ quả nhẹ nhàng: người dùng chỉ phải đăng nhập lại. Đây là kiểu "defensive coding" đáng học.

`SessionManager` chính là mắt xích mà interceptor ở bài 06 đọc token ra để gắn header, và MainActivity đọc `isLoggedIn()` để chọn màn khởi đầu. Nó là "phiên đăng nhập" của app.

> 💡 Các "store" khác như [ThemeController](../app/app/src/main/java/com/example/todoapplication/data/repository/ThemeController.kt), [CategoryStore](../app/app/src/main/java/com/example/todoapplication/data/repository/CategoryStore.kt) cũng dựa trên SharedPreferences (bản thường, không mã hóa) — được `init()` một lần trong [TodoApplication](../app/app/src/main/java/com/example/todoapplication/TodoApplication.kt).

## Tự kiểm tra
1. Khi nào chọn Room, khi nào chọn SharedPreferences?
2. Vì sao `TaskCacheEntity` (DB) lại tách khỏi `Task` (API) dù gần giống nhau? Việc chuyển đổi ở đâu?
3. `fallbackToDestructiveMigration()` làm gì? Vì sao ở đây chấp nhận được nhưng với DB "thật" thì không?
4. Vì sao AppDatabase phải là singleton và dùng Application context?
5. Token được bảo vệ thế nào? Nếu file mã hóa hỏng thì app xử lý ra sao?
6. Mô tả lại luồng "xem offline" đi qua những tầng nào.

➡️ Tiếp theo: [Bài 08 — Dependency Injection](./08-dependency-injection.md)
