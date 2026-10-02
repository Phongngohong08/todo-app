package com.example.todoapplication.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cài đặt cục bộ (SharedPreferences thường — không nhạy cảm), phát ra StateFlow để UI tự cập nhật.
 *
 *  - Theo tài khoản, bản gốc ở server: mục tiêu ngày + ngày nghỉ (chép về để tính chuỗi khi offline).
 *  - Theo thiết bị: thông báo buổi sáng / tổng kết tuần, giờ nhắc mặc định cho việc "cả ngày",
 *    đã hỏi quyền thông báo chưa.
 */
class LocalPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private val _dailyGoal = MutableStateFlow(prefs.getInt(KEY_DAILY_GOAL, DEFAULT_DAILY_GOAL))
    val dailyGoal: StateFlow<Int> = _dailyGoal.asStateFlow()

    private val _daysOff = MutableStateFlow(prefs.getString(KEY_DAYS_OFF, "").orEmpty())
    /** "SAT,SUN" */
    val daysOff: StateFlow<String> = _daysOff.asStateFlow()

    private val _digestEnabled = MutableStateFlow(prefs.getBoolean(KEY_DIGEST_ENABLED, true))
    val digestEnabled: StateFlow<Boolean> = _digestEnabled.asStateFlow()

    private val _digestTime = MutableStateFlow(prefs.getString(KEY_DIGEST_TIME, DEFAULT_DIGEST_TIME) ?: DEFAULT_DIGEST_TIME)
    /** "HH:mm" */
    val digestTime: StateFlow<String> = _digestTime.asStateFlow()

    private val _weeklyReviewEnabled = MutableStateFlow(prefs.getBoolean(KEY_WEEKLY_REVIEW, true))
    val weeklyReviewEnabled: StateFlow<Boolean> = _weeklyReviewEnabled.asStateFlow()

    private val _allDayReminderTime =
        MutableStateFlow(prefs.getString(KEY_ALL_DAY_REMINDER, DEFAULT_ALL_DAY_REMINDER) ?: DEFAULT_ALL_DAY_REMINDER)
    /** Giờ nhắc ("HH:mm") cho việc chỉ có ngày — nhắc buổi sáng của ngày đến hạn. */
    val allDayReminderTime: StateFlow<String> = _allDayReminderTime.asStateFlow()

    // ── Nhắc hẹn nâng cao (theo thiết bị) ──

    private val _quietEnabled = MutableStateFlow(prefs.getBoolean(KEY_QUIET_ENABLED, false))
    /** Giờ yên tĩnh: nhắc việc rơi vào khoảng này được dời tới lúc kết thúc. */
    val quietEnabled: StateFlow<Boolean> = _quietEnabled.asStateFlow()

    private val _quietStart = MutableStateFlow(prefs.getString(KEY_QUIET_START, DEFAULT_QUIET_START) ?: DEFAULT_QUIET_START)
    val quietStart: StateFlow<String> = _quietStart.asStateFlow()

    private val _quietEnd = MutableStateFlow(prefs.getString(KEY_QUIET_END, DEFAULT_QUIET_END) ?: DEFAULT_QUIET_END)
    val quietEnd: StateFlow<String> = _quietEnd.asStateFlow()

    private val _nagMinutes = MutableStateFlow(prefs.getInt(KEY_NAG_MINUTES, 0))
    /** Nhắc lại mỗi N phút cho tới khi xong (0 = tắt). */
    val nagMinutes: StateFlow<Int> = _nagMinutes.asStateFlow()

    private val _nagHighOnly = MutableStateFlow(prefs.getBoolean(KEY_NAG_HIGH_ONLY, true))
    /** Chỉ nhắc dai việc ưu tiên cao. */
    val nagHighOnly: StateFlow<Boolean> = _nagHighOnly.asStateFlow()

    private val _alarmHighPriority = MutableStateFlow(prefs.getBoolean(KEY_ALARM_HIGH, false))
    /** Việc ưu tiên cao đến hạn → báo thức toàn màn hình (đổ chuông tới khi tắt) thay cho thông báo thường. */
    val alarmHighPriority: StateFlow<Boolean> = _alarmHighPriority.asStateFlow()

    private val _appLockEnabled = MutableStateFlow(prefs.getBoolean(KEY_APP_LOCK, false))
    /** Khóa app bằng vân tay / khuôn mặt / mã khóa màn hình. */
    val appLockEnabled: StateFlow<Boolean> = _appLockEnabled.asStateFlow()

    fun setQuietEnabled(v: Boolean) { _quietEnabled.value = v; prefs.edit().putBoolean(KEY_QUIET_ENABLED, v).apply() }
    fun setQuietStart(v: String) { _quietStart.value = v; prefs.edit().putString(KEY_QUIET_START, v).apply() }
    fun setQuietEnd(v: String) { _quietEnd.value = v; prefs.edit().putString(KEY_QUIET_END, v).apply() }
    fun setNagMinutes(v: Int) { _nagMinutes.value = v; prefs.edit().putInt(KEY_NAG_MINUTES, v).apply() }
    fun setNagHighOnly(v: Boolean) { _nagHighOnly.value = v; prefs.edit().putBoolean(KEY_NAG_HIGH_ONLY, v).apply() }
    fun setAlarmHighPriority(v: Boolean) { _alarmHighPriority.value = v; prefs.edit().putBoolean(KEY_ALARM_HIGH, v).apply() }
    fun setAppLockEnabled(v: Boolean) { _appLockEnabled.value = v; prefs.edit().putBoolean(KEY_APP_LOCK, v).apply() }

    var notificationPermissionAsked: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIF_ASKED, value).apply()

    fun setDailyGoal(value: Int) {
        val v = value.coerceIn(1, MAX_DAILY_GOAL)
        _dailyGoal.value = v
        prefs.edit().putInt(KEY_DAILY_GOAL, v).apply()
    }

    fun setDaysOff(value: String) {
        _daysOff.value = value
        prefs.edit().putString(KEY_DAYS_OFF, value).apply()
    }

    fun setDigestEnabled(value: Boolean) {
        _digestEnabled.value = value
        prefs.edit().putBoolean(KEY_DIGEST_ENABLED, value).apply()
    }

    fun setDigestTime(value: String) {
        _digestTime.value = value
        prefs.edit().putString(KEY_DIGEST_TIME, value).apply()
    }

    fun setWeeklyReviewEnabled(value: Boolean) {
        _weeklyReviewEnabled.value = value
        prefs.edit().putBoolean(KEY_WEEKLY_REVIEW, value).apply()
    }

    fun setAllDayReminderTime(value: String) {
        _allDayReminderTime.value = value
        prefs.edit().putString(KEY_ALL_DAY_REMINDER, value).apply()
    }

    /** Đăng xuất: bỏ dữ liệu của tài khoản (mục tiêu, ngày nghỉ); giữ cài đặt của thiết bị. */
    fun clearAccountData() {
        _dailyGoal.value = DEFAULT_DAILY_GOAL
        _daysOff.value = ""
        prefs.edit().remove(KEY_DAILY_GOAL).remove(KEY_DAYS_OFF).apply()
    }

    companion object {
        private const val NAME = "todo_local_prefs"
        private const val KEY_DAILY_GOAL = "daily_goal"
        private const val KEY_DAYS_OFF = "days_off"
        private const val KEY_DIGEST_ENABLED = "digest_enabled"
        private const val KEY_DIGEST_TIME = "digest_time"
        private const val KEY_WEEKLY_REVIEW = "weekly_review_enabled"
        private const val KEY_ALL_DAY_REMINDER = "all_day_reminder_time"
        private const val KEY_NOTIF_ASKED = "notification_permission_asked"
        private const val KEY_QUIET_ENABLED = "quiet_enabled"
        private const val KEY_QUIET_START = "quiet_start"
        private const val KEY_QUIET_END = "quiet_end"
        private const val KEY_NAG_MINUTES = "nag_minutes"
        private const val KEY_NAG_HIGH_ONLY = "nag_high_only"
        private const val KEY_ALARM_HIGH = "alarm_high_priority"
        private const val KEY_APP_LOCK = "app_lock"

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
