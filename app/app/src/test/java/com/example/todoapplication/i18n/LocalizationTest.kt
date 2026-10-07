package com.example.todoapplication.i18n

import com.example.todoapplication.R
import com.example.todoapplication.TestStrings
import com.example.todoapplication.domain.HabitLogic
import com.example.todoapplication.domain.Reschedule
import com.example.todoapplication.domain.model.Habit
import com.example.todoapplication.domain.model.HabitFrequency
import com.example.todoapplication.domain.model.Task
import com.example.todoapplication.domain.recommendationReason
import com.example.todoapplication.ui.utils.formatDueLabel
import com.example.todoapplication.ui.utils.formatDuration
import com.example.todoapplication.ui.utils.recurrenceDescription
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Song ngữ: hai file tài nguyên phải có cùng bộ khóa và cùng số tham số định dạng (thiếu khóa ở values-vi → người dùng
 * tiếng Việt thấy chữ tiếng Anh; lệch tham số → crash lúc format), và các nhãn tạo trong code ra đúng tiếng Anh.
 */
class LocalizationTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 7, 8) // thứ Tư

    @After
    fun restoreVietnamese() {
        TestStrings.install("vi")
        Locale.setDefault(Locale.US)
    }

    private fun resources(dir: String): Map<String, String> {
        val file = listOf("src/main/res/$dir/strings.xml", "app/src/main/res/$dir/strings.xml").map(::File).first { it.exists() }
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val out = linkedMapOf<String, String>()
        for (tag in listOf("string", "plurals", "string-array")) {
            val nodes = root.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val e = nodes.item(i) as Element
                out["$tag:" + e.getAttribute("name")] = e.textContent
            }
        }
        return out
    }

    private fun argCount(s: String): Int {
        val positional = Regex("""%(\d+)\$[sd]""").findAll(s).map { it.groupValues[1] }.toSet().size
        return if (positional > 0) positional else Regex("""(?<!%)%[sd]""").findAll(s).count()
    }

    @Test
    fun `english and vietnamese resources have the same keys and format arguments`() {
        val en = resources("values")
        val vi = resources("values-vi")
        assertEquals("Khóa chỉ có ở một ngôn ngữ", en.keys.toSortedSet(), vi.keys.toSortedSet())
        en.filterKeys { it.startsWith("string:") }.forEach { (key, value) ->
            assertEquals("Số tham số của $key", argCount(value), argCount(vi.getValue(key)))
        }
    }

    @Test
    fun `labels built in code read naturally in english`() {
        TestStrings.install("en")
        Locale.setDefault(Locale.US)
        fun at(m: Int, d: Int, h: Int, min: Int = 0, y: Int = 2026) = ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

        assertEquals("Today", formatDueLabel(at(7, 8, 23, 59), allDay = true, today = today, zone = zone))
        assertEquals("Tomorrow 15:00", formatDueLabel(at(7, 9, 15), allDay = false, today = today, zone = zone))
        assertEquals("Fri, Jul 10", formatDueLabel(at(7, 10, 23, 59), allDay = true, today = today, zone = zone))
        assertEquals("Mon, Jan 4, 2027 08:00", formatDueLabel(at(1, 4, 8, y = 2027), allDay = false, today = today, zone = zone))
        assertEquals("1 h 30 min", formatDuration(90))
        assertEquals("Weekly (Mon, Thu)", recurrenceDescription("WEEKLY", "MON,THU", 1, "SCHEDULE"))
        assertEquals("Every 3 days · from completion", recurrenceDescription("DAILY", "", 3, "COMPLETION"))
        assertEquals("Mon, Wed, Fri", HabitLogic.describe(Habit(id = "h", name = "x", frequency = HabitFrequency.WEEKDAYS, weekdays = "MON,WED,FRI")))
        assertEquals("1 week", HabitLogic.streakLabel(Habit(id = "h", name = "x", frequency = HabitFrequency.WEEKLY), 1))
        assertEquals("Next Monday", Reschedule.postponeOptions(today).first { it.date == LocalDate.of(2026, 7, 13) }.label)
    }

    @Test
    fun `plurals pick the singular form in english only`() {
        val now = ZonedDateTime.of(2026, 7, 8, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        val oneDayLate = Task(id = "t", title = "x", priority = "LOW", dueAt = now - 26 * 3_600_000L)
        TestStrings.install("en")
        assertEquals("Overdue by 1 day", recommendationReason(oneDayLate, now, zone))
        assertEquals("Overdue by 2 days", recommendationReason(oneDayLate.copy(dueAt = now - 50 * 3_600_000L), now, zone))
        TestStrings.install("vi")
        assertEquals("Quá hạn 1 ngày", recommendationReason(oneDayLate, now, zone))
        assertEquals(str(R.string.day_today), "Hôm nay")
    }
}
