package com.example.todoapplication.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.todoapplication.domain.SavedFilter
import com.example.todoapplication.domain.SavedFilterCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * DataStore của cài đặt trên máy — một instance cho cả tiến trình (DataStore cấm mở cùng tệp hai lần).
 * Lần đầu chạy bản mới sẽ tự chép dữ liệu cũ từ SharedPreferences "todo_local_prefs" (SharedPreferencesMigration)
 * rồi xóa tệp cũ — người dùng không mất cài đặt khi nâng cấp.
 */
private val Context.localPrefsStore: DataStore<Preferences> by preferencesDataStore(
    name = "local_prefs",
    produceMigrations = { context -> listOf(SharedPreferencesMigration(context, LocalPrefs.LEGACY_NAME)) }
)

/**
 * Cài đặt cục bộ (không nhạy cảm) lưu bằng Jetpack DataStore (Preferences), phát ra StateFlow để UI tự cập nhật.
 *
 *  - Theo tài khoản, bản gốc ở server: mục tiêu ngày + ngày nghỉ (chép về để tính chuỗi khi offline).
 *  - Theo thiết bị: thông báo, nhắc hẹn nâng cao, khóa app, bộ lọc đã lưu, tiện ích hệ thống…
 *
 * DataStore đọc / ghi bất đồng bộ trên luồng IO (không chặn luồng UI như SharedPreferences.commit). Để các nơi
 * gọi đồng bộ (WorkManager, BroadcastReceiver, `.value`) vẫn dùng được, giá trị được nạp MỘT lần lúc khởi tạo
 * rồi giữ trong StateFlow; mỗi lần đổi thì cập nhật StateFlow ngay và ghi xuống DataStore ở nền.
 */
class LocalPrefs(context: Context) {
    private val store = context.applicationContext.localPrefsStore
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Ảnh chụp lúc khởi tạo (đã chạy migration). Chỉ đọc một tệp nhỏ, một lần cho cả tiến trình. */
    private val initial: Preferences = runBlocking(Dispatchers.IO) { store.data.first() }

    private fun <T> state(key: Preferences.Key<T>, default: T) = MutableStateFlow(initial[key] ?: default)

    private fun <T> save(key: Preferences.Key<T>, value: T) {
        io.launch { store.edit { it[key] = value } }
    }

    private val _dailyGoal = state(KEY_DAILY_GOAL, DEFAULT_DAILY_GOAL)
    val dailyGoal: StateFlow<Int> = _dailyGoal.asStateFlow()

    private val _daysOff = state(KEY_DAYS_OFF, "")
    /** "SAT,SUN" */
    val daysOff: StateFlow<String> = _daysOff.asStateFlow()

    private val _digestEnabled = state(KEY_DIGEST_ENABLED, true)
    val digestEnabled: StateFlow<Boolean> = _digestEnabled.asStateFlow()

    private val _digestTime = state(KEY_DIGEST_TIME, DEFAULT_DIGEST_TIME)
    /** "HH:mm" */
    val digestTime: StateFlow<String> = _digestTime.asStateFlow()

    private val _weeklyReviewEnabled = state(KEY_WEEKLY_REVIEW, true)
    val weeklyReviewEnabled: StateFlow<Boolean> = _weeklyReviewEnabled.asStateFlow()

    private val _allDayReminderTime = state(KEY_ALL_DAY_REMINDER, DEFAULT_ALL_DAY_REMINDER)
    /** Giờ nhắc ("HH:mm") cho việc chỉ có ngày — nhắc buổi sáng của ngày đến hạn. */
    val allDayReminderTime: StateFlow<String> = _allDayReminderTime.asStateFlow()

    // ── Nhắc hẹn nâng cao (theo thiết bị) ──

    private val _quietEnabled = state(KEY_QUIET_ENABLED, false)
    /** Giờ yên tĩnh: nhắc việc rơi vào khoảng này được dời tới lúc kết thúc. */
    val quietEnabled: StateFlow<Boolean> = _quietEnabled.asStateFlow()

    private val _quietStart = state(KEY_QUIET_START, DEFAULT_QUIET_START)
    val quietStart: StateFlow<String> = _quietStart.asStateFlow()

    private val _quietEnd = state(KEY_QUIET_END, DEFAULT_QUIET_END)
    val quietEnd: StateFlow<String> = _quietEnd.asStateFlow()

    private val _nagMinutes = state(KEY_NAG_MINUTES, 0)
    /** Nhắc lại mỗi N phút cho tới khi xong (0 = tắt). */
    val nagMinutes: StateFlow<Int> = _nagMinutes.asStateFlow()

    private val _nagHighOnly = state(KEY_NAG_HIGH_ONLY, true)
    /** Chỉ nhắc dai việc ưu tiên cao. */
    val nagHighOnly: StateFlow<Boolean> = _nagHighOnly.asStateFlow()

    private val _alarmHighPriority = state(KEY_ALARM_HIGH, false)
    /** Việc ưu tiên cao đến hạn → báo thức toàn màn hình (đổ chuông tới khi tắt) thay cho thông báo thường. */
    val alarmHighPriority: StateFlow<Boolean> = _alarmHighPriority.asStateFlow()

    private val _appLockEnabled = state(KEY_APP_LOCK, false)
    /** Khóa app bằng vân tay / khuôn mặt / mã khóa màn hình. */
    val appLockEnabled: StateFlow<Boolean> = _appLockEnabled.asStateFlow()

    fun setQuietEnabled(v: Boolean) { _quietEnabled.value = v; save(KEY_QUIET_ENABLED, v) }
    fun setQuietStart(v: String) { _quietStart.value = v; save(KEY_QUIET_START, v) }
    fun setQuietEnd(v: String) { _quietEnd.value = v; save(KEY_QUIET_END, v) }
    fun setNagMinutes(v: Int) { _nagMinutes.value = v; save(KEY_NAG_MINUTES, v) }
    fun setNagHighOnly(v: Boolean) { _nagHighOnly.value = v; save(KEY_NAG_HIGH_ONLY, v) }
    fun setAlarmHighPriority(v: Boolean) { _alarmHighPriority.value = v; save(KEY_ALARM_HIGH, v) }
    fun setAppLockEnabled(v: Boolean) { _appLockEnabled.value = v; save(KEY_APP_LOCK, v) }

    private val _dailyCapacity = state(KEY_DAILY_CAPACITY, DEFAULT_DAILY_CAPACITY)
    /** Số phút làm việc mỗi ngày — lịch tuần cảnh báo ngày có tổng thời lượng ước tính vượt mức này. */
    val dailyCapacityMinutes: StateFlow<Int> = _dailyCapacity.asStateFlow()

    fun setDailyCapacityMinutes(v: Int) {
        val clean = v.coerceIn(60, 16 * 60)
        _dailyCapacity.value = clean
        save(KEY_DAILY_CAPACITY, clean)
    }

    private val _focusDnd = state(KEY_FOCUS_DND, false)
    /** Bật "Không làm phiền" của hệ thống trong lúc tập trung. */
    val focusDnd: StateFlow<Boolean> = _focusDnd.asStateFlow()
    fun setFocusDnd(v: Boolean) { _focusDnd.value = v; save(KEY_FOCUS_DND, v) }

    private val _quickAddNotification = state(KEY_QUICK_ADD_NOTIF, false)
    /** Thông báo cố định "Thêm việc nhanh" trên thanh thông báo. */
    val quickAddNotification: StateFlow<Boolean> = _quickAddNotification.asStateFlow()
    fun setQuickAddNotification(v: Boolean) { _quickAddNotification.value = v; save(KEY_QUICK_ADD_NOTIF, v) }

    private val _showDeviceCalendar = state(KEY_DEVICE_CALENDAR, false)
    /** Hiện sự kiện trong lịch của máy (CalendarContract) ở tab Lịch / Hôm nay và gửi giờ bận cho AI lập lịch. */
    val showDeviceCalendar: StateFlow<Boolean> = _showDeviceCalendar.asStateFlow()
    fun setShowDeviceCalendar(v: Boolean) { _showDeviceCalendar.value = v; save(KEY_DEVICE_CALENDAR, v) }

    private val _savedFilters = MutableStateFlow(SavedFilterCodec.decode(initial[KEY_SAVED_FILTERS].orEmpty()))
    /** Bộ lọc người dùng đã lưu ở tab Việc làm (theo thiết bị). */
    val savedFilters: StateFlow<List<SavedFilter>> = _savedFilters.asStateFlow()

    fun setSavedFilters(filters: List<SavedFilter>) {
        val list = filters.take(SavedFilterCodec.MAX_FILTERS)
        _savedFilters.value = list
        save(KEY_SAVED_FILTERS, SavedFilterCodec.encode(list))
    }

    private val _notificationPermissionAsked = state(KEY_NOTIF_ASKED, false)
    var notificationPermissionAsked: Boolean
        get() = _notificationPermissionAsked.value
        set(value) {
            _notificationPermissionAsked.value = value
            save(KEY_NOTIF_ASKED, value)
        }

    fun setDailyGoal(value: Int) {
        val v = value.coerceIn(1, MAX_DAILY_GOAL)
        _dailyGoal.value = v
        save(KEY_DAILY_GOAL, v)
    }

    fun setDaysOff(value: String) {
        _daysOff.value = value
        save(KEY_DAYS_OFF, value)
    }

    fun setDigestEnabled(value: Boolean) {
        _digestEnabled.value = value
        save(KEY_DIGEST_ENABLED, value)
    }

    fun setDigestTime(value: String) {
        _digestTime.value = value
        save(KEY_DIGEST_TIME, value)
    }

    fun setWeeklyReviewEnabled(value: Boolean) {
        _weeklyReviewEnabled.value = value
        save(KEY_WEEKLY_REVIEW, value)
    }

    fun setAllDayReminderTime(value: String) {
        _allDayReminderTime.value = value
        save(KEY_ALL_DAY_REMINDER, value)
    }

    /** Đăng xuất: bỏ dữ liệu của tài khoản (mục tiêu, ngày nghỉ); giữ cài đặt của thiết bị. */
    fun clearAccountData() {
        _dailyGoal.value = DEFAULT_DAILY_GOAL
        _daysOff.value = ""
        io.launch {
            store.edit {
                it.remove(KEY_DAILY_GOAL)
                it.remove(KEY_DAYS_OFF)
            }
        }
    }

    companion object {
        /** Tên tệp SharedPreferences cũ — chỉ dùng cho migration sang DataStore. */
        const val LEGACY_NAME = "todo_local_prefs"

        // Giữ đúng tên khóa cũ để SharedPreferencesMigration chép sang nguyên vẹn
        private val KEY_DAILY_GOAL = intPreferencesKey("daily_goal")
        private val KEY_DAYS_OFF = stringPreferencesKey("days_off")
        private val KEY_DIGEST_ENABLED = booleanPreferencesKey("digest_enabled")
        private val KEY_DIGEST_TIME = stringPreferencesKey("digest_time")
        private val KEY_WEEKLY_REVIEW = booleanPreferencesKey("weekly_review_enabled")
        private val KEY_ALL_DAY_REMINDER = stringPreferencesKey("all_day_reminder_time")
        private val KEY_NOTIF_ASKED = booleanPreferencesKey("notification_permission_asked")
        private val KEY_QUIET_ENABLED = booleanPreferencesKey("quiet_enabled")
        private val KEY_QUIET_START = stringPreferencesKey("quiet_start")
        private val KEY_QUIET_END = stringPreferencesKey("quiet_end")
        private val KEY_NAG_MINUTES = intPreferencesKey("nag_minutes")
        private val KEY_NAG_HIGH_ONLY = booleanPreferencesKey("nag_high_only")
        private val KEY_ALARM_HIGH = booleanPreferencesKey("alarm_high_priority")
        private val KEY_APP_LOCK = booleanPreferencesKey("app_lock")
        private val KEY_SAVED_FILTERS = stringPreferencesKey("saved_filters")
        private val KEY_DEVICE_CALENDAR = booleanPreferencesKey("show_device_calendar")
        private val KEY_FOCUS_DND = booleanPreferencesKey("focus_dnd")
        private val KEY_QUICK_ADD_NOTIF = booleanPreferencesKey("quick_add_notification")
        private val KEY_DAILY_CAPACITY = intPreferencesKey("daily_capacity_minutes")

        const val DEFAULT_DAILY_CAPACITY = 8 * 60
        const val DEFAULT_QUIET_START = "22:00"
        const val DEFAULT_QUIET_END = "07:00"
        /** Nhắc dai tối đa chừng này lần cho một lượt đến hạn (tránh nhắc vô tận nếu bỏ quên máy). */
        const val MAX_NAGS = 12

        const val DEFAULT_DAILY_GOAL = 3
        const val MAX_DAILY_GOAL = 50
        const val DEFAULT_DIGEST_TIME = "07:30"
        const val DEFAULT_ALL_DAY_REMINDER = "08:00"
    }
}
