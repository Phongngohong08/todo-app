package com.example.todoapplication.domain

import com.example.todoapplication.domain.model.FocusRecord
import com.example.todoapplication.domain.model.Habit
import com.example.todoapplication.domain.model.HabitFrequency
import com.example.todoapplication.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class OrganizerLogicTest {

    private val zone: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

    // ── Nhãn & lời nhắc (phải giống backend) ──

    @Test
    fun `tags are normalized like the backend`() {
        assertEquals(listOf("nhà", "đi-chợ", "ab"), TagRules.normalize(listOf(" @Nhà ", "nhà", "#đi chợ", "", "a,b", "  ")))
        assertEquals(TagRules.MAX_TAGS, TagRules.normalize((1..15).map { "t$it" }).size)
        assertEquals(TagRules.MAX_LENGTH, TagRules.normalize("x".repeat(40)).length)
        assertEquals(listOf("a", "b"), TagRules.decode(TagRules.encode(listOf("a", "b"))))
        assertEquals(emptyList<String>(), TagRules.decode(""))
    }

    @Test
    fun `reminders are normalized and split into main plus extras`() {
        assertEquals(listOf(0, 10, 30, 60, 120), ReminderRules.normalize(listOf(1440, 60, -5, 60, 20000, 0, 10, 30, 120)))
        assertEquals(10 to listOf(60, 1440), ReminderRules.split(setOf(1440, 10, 60)))
        assertEquals(0 to emptyList<Int>(), ReminderRules.split(emptySet()))
        assertEquals(listOf(60, 1440), ReminderRules.decode("60,1440"))
    }

    @Test
    fun `task reminder offsets merge main and extras`() {
        val task = Task(id = "t", title = "x", reminderOffsetMinutes = 10, extraReminders = listOf(1440, 10, 60))
        assertEquals(listOf(10, 60, 1440), task.reminderOffsets)
    }

    // ── Thói quen ──

    private val wed = LocalDate.of(2026, 10, 7) // thứ 4

    private fun days(vararg d: LocalDate) = d.map { it.toString() }.toSet()

    @Test
    fun `daily streak counts back from yesterday when today is not done yet`() {
        val habit = Habit(id = "h", name = "Uống nước")
        val done = days(wed.minusDays(1), wed.minusDays(2), wed.minusDays(4))
        assertEquals(2, HabitLogic.streak(habit, done, wed))
        assertEquals(3, HabitLogic.streak(habit, done + wed.toString(), wed))
        assertTrue(HabitLogic.isDueToday(habit, done, wed))
        assertFalse(HabitLogic.isDueToday(habit, done + wed.toString(), wed))
    }

    @Test
    fun `weekday habits skip unscheduled days in the streak`() {
        val habit = Habit(id = "h", name = "Chạy", frequency = HabitFrequency.WEEKDAYS, weekdays = "MON,WED,FRI")
        // Làm T2 (5/10), T6 tuần trước (2/10); hôm nay T4 chưa làm → chuỗi = 2, T3/T5/T7/CN không làm đứt
        val done = days(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 2))
        assertEquals(2, HabitLogic.streak(habit, done, wed))
        assertTrue(HabitLogic.isDueToday(habit, done, wed))
        assertFalse("thứ 3 không phải ngày cần làm", HabitLogic.isDueToday(habit, done, wed.minusDays(1)))
        assertEquals("T2, T4, T6", HabitLogic.describe(habit))
    }

    @Test
    fun `weekly target habits count weeks that met the target`() {
        val habit = Habit(id = "h", name = "Gym", frequency = HabitFrequency.WEEKLY, targetPerWeek = 2)
        val lastWeek = HabitLogic.weekStart(wed).minusWeeks(1)
        val twoWeeksAgo = lastWeek.minusWeeks(1)
        val done = days(lastWeek, lastWeek.plusDays(2), twoWeeksAgo.plusDays(1), twoWeeksAgo.plusDays(3), wed.minusDays(1))
        // Tuần này mới 1/2 → chưa đứt, tính 2 tuần trước đó
        assertEquals(2, HabitLogic.streak(habit, done, wed))
        assertTrue(HabitLogic.isDueToday(habit, done, wed))
        // Đủ 2 lần tuần này → không còn cần làm hôm nay, chuỗi tính cả tuần này
        val full = done + wed.minusDays(2).toString()
        assertFalse(HabitLogic.isDueToday(habit, full, wed))
        assertEquals(3, HabitLogic.streak(habit, full, wed))
        assertEquals("tuần", HabitLogic.streakUnit(habit))
    }

    @Test
    fun `archived habits are never due and completion rate respects creation date`() {
        assertFalse(HabitLogic.isDueToday(Habit(id = "h", name = "x", archived = true), emptySet(), wed))
        val habit = Habit(id = "h", name = "Đọc sách")
        val done = days(wed, wed.minusDays(1))
        // Tạo hôm qua → 2/2 ngày
        assertEquals(1f, HabitLogic.completionRate(habit, done, wed, createdOn = wed.minusDays(1)), 0.001f)
        assertEquals(2f / 30f, HabitLogic.completionRate(habit, done, wed, createdOn = wed.minusDays(100)), 0.001f)
        assertEquals("MON,WED", HabitLogic.normalizeWeekdays("wed, mon, MON, funday"))
    }

    // ── Kanban ──

    @Test
    fun `board columns start with unsectioned and keep tasks of removed sections`() {
        val tasks = listOf(
            Task(id = "a", title = "a", section = "Đang làm"),
            Task(id = "b", title = "b"),
            Task(id = "c", title = "c", section = "Mục đã xóa")
        )
        val cols = BoardLogic.columns(listOf("Cần làm", "Đang làm"), tasks)
        assertEquals(listOf("", "Cần làm", "Đang làm"), cols.map { it.first })
        assertEquals(listOf("b", "c"), cols[0].second.map { it.id })
        assertEquals(listOf("a"), cols[2].second.map { it.id })
    }

    // ── Bộ lọc đã lưu ──

    @Test
    fun `saved filters survive encoding`() {
        val filters = listOf(
            SavedFilter("Việc nhà gấp", SmartFilter.HIGH_PRIORITY, "PERSONAL", "nhà", "dọn"),
            SavedFilter("Tất cả quá hạn", SmartFilter.OVERDUE)
        )
        assertEquals(filters, SavedFilterCodec.decode(SavedFilterCodec.encode(filters)))
        assertEquals(emptyList<SavedFilter>(), SavedFilterCodec.decode(""))
    }

    // ── Tập trung ──

    @Test
    fun `focus stats group the last seven days`() {
        val today = LocalDate.of(2026, 10, 7)
        fun at(day: LocalDate, hour: Int) = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        val records = listOf(
            FocusRecord("1", "t1", "Viết báo cáo", at(today, 9), 25),
            FocusRecord("2", "t1", "Viết báo cáo", at(today.minusDays(1), 9), 25),
            FocusRecord("3", "t2", "Học tiếng Anh", at(today.minusDays(2), 20), 50),
            FocusRecord("4", null, "", at(today.minusDays(10), 9), 25) // ngoài 7 ngày
        )
        val stats = FocusCalculator.compute(records, today, zone)
        assertEquals(25, stats.todayMinutes)
        assertEquals(100, stats.weekMinutes)
        assertEquals(3, stats.weekSessions)
        assertEquals(listOf(0, 0, 0, 0, 50, 25, 25), stats.last7)
        assertEquals(listOf("Học tiếng Anh" to 50, "Viết báo cáo" to 50), stats.topTasks.sortedBy { it.first })
    }

    // ── Tạo nhanh: @nhãn ──

    @Test
    fun `quick add extracts tags and strips them from the title`() {
        val r = QuickAddParser.parse("Mua rau @Nhà @gấp mai", LocalDateTime.of(2026, 10, 7, 9, 0), zone)
        assertEquals("Mua rau", r.title)
        assertEquals(listOf("nhà", "gấp"), r.tags)
        assertTrue(r.tokens.any { it.kind == QuickAddParser.Kind.TAG })
        assertTrue(r.dueAt != null)
    }

    // ── Gợi ý: bước con đến hạn ──

    @Test
    fun `my day suggests tasks whose next step is due`() {
        val today = LocalDate.of(2026, 10, 7)
        val now = today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val tasks = listOf(
            Task(id = "a", title = "Luận văn", nextStepDue = "2026-10-07", nextStepTitle = "Nộp đề cương"),
            Task(id = "b", title = "Báo cáo", nextStepDue = "2026-10-06", nextStepTitle = "Gửi số liệu"),
            Task(id = "c", title = "Sau này", nextStepDue = "2026-10-20", nextStepTitle = "Xa")
        )
        val reasons = myDaySuggestions(tasks, today, now, zone).associate { it.task.id to it.reason }
        assertEquals("Bước đến hạn: Nộp đề cương", reasons["a"])
        assertEquals("Bước quá hạn: Gửi số liệu", reasons["b"])
        assertNull(reasons["c"])
    }
}
