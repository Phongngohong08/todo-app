package com.example.todoapplication.domain

import com.example.todoapplication.domain.model.Recurrence
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * [TẦNG DOMAIN] Phân tích câu tạo nhanh tiếng Việt NGAY TRÊN MÁY (kiểu Todoist): chạy tức thì khi gõ, không cần
 * mạng hay lượt AI. Phần nhận ra được tô màu trong ô nhập và bị bỏ khỏi tiêu đề.
 *
 *   "Nộp báo cáo mai 3h chiều !cao #Công việc"  →  tiêu đề "Nộp báo cáo", ngày mai 15:00, ưu tiên cao, WORK
 *   "Tưới cây mỗi thứ 2 và thứ 5"               →  lặp hằng tuần T2, T5, hạn cả ngày ở lần gần nhất
 *
 * Hiểu được cả khi gõ không dấu ("mai 3h chieu"). Câu phức tạp hơn thì vẫn còn nút phân tích bằng AI.
 */
object QuickAddParser {

    enum class Kind { DATE, TIME, PRIORITY, CATEGORY, RECURRENCE }

    /** Một đoạn được nhận ra trong câu gốc (vị trí để tô màu). */
    data class Token(val range: IntRange, val kind: Kind)

    data class Result(
        val title: String,
        val dueAt: Long?,
        val allDay: Boolean,
        val priority: String?,
        /** Mã danh mục có sẵn, hoặc tên mới người dùng gõ sau "#" (caller tạo danh mục). */
        val category: String?,
        val recurrence: String?,
        val recurrenceDays: String,
        val tokens: List<Token>
    ) {
        val hasAnything: Boolean get() = tokens.isNotEmpty()
    }

    /** Danh mục để khớp "#...": mã lưu trữ + nhãn hiển thị (vd "WORK" / "Công việc"). */
    data class CategoryOption(val code: String, val label: String)

    // ── Mẫu (viết trên chuỗi đã bỏ dấu, chữ thường) ─────────────────────────────

    private const val WD = """(?:thu\s*(?:[2-7]|hai|ba|tu|nam|sau|bay)|t[2-7]|chu\s*nhat|cn)"""
    private val RE_RECUR_DAYS = Regex("""\b(?:moi|hang)\s+($WD(?:\s*(?:,|va|&)\s*$WD)*)\b""")
    private val RE_RECUR_SIMPLE = Regex("""\b(?:moi|hang)\s+(ngay|tuan|thang)\b""")
    private val RE_PART_OF_DAY_DATE = Regex("""\b(sang|trua|chieu|toi)\s+(nay|mai)\b""")
    private val RE_TODAY = Regex("""\bhom\s+nay\b""")
    private val RE_TOMORROW = Regex("""\b(?:ngay\s+)?mai\b""")
    private val RE_DAY_AFTER = Regex("""\bngay\s+(?:kia|mot)\b""")
    private val RE_IN_DAYS = Regex("""\b(?:(\d{1,3})\s+(ngay|tuan)\s+nua|sau\s+(\d{1,3})\s+(ngay|tuan))\b""")
    private val RE_WEEKDAY = Regex("""\b($WD)(\s+tuan\s+(?:sau|toi))?\b""")
    private val RE_NEXT_WEEK = Regex("""\btuan\s+(?:sau|toi)\b""")
    private val RE_WEEKEND = Regex("""\bcuoi\s+tuan\b""")
    private val RE_DATE = Regex("""\b(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?\b""")
    private val RE_TIME = Regex(
        """\b(?:luc\s+)?(\d{1,2})(?:\s*(?:h|g|gio|:)\s*(\d{2}|ruoi)?)(?:\s+(sang|trua|chieu|toi|dem))?(?![\w/])"""
    )
    private val RE_PRIORITY = Regex("""(?:^|\s)(!(?:cao|gap|1|tb|trung\s*binh|vua|2|thap|3)|p[123])(?=\s|$)""")
    private val RE_CATEGORY = Regex("""(?:^|\s)#(\S+)""")

    fun parse(
        text: String,
        now: LocalDateTime,
        zone: ZoneId = ZoneId.systemDefault(),
        categories: List<CategoryOption> = emptyList()
    ): Result {
        val norm = normalize(text)
        val taken = BooleanArray(text.length)
        val tokens = mutableListOf<Token>()

        fun free(r: IntRange) = r.all { !taken[it] }
        fun take(r: IntRange, kind: Kind) {
            r.forEach { taken[it] = true }
            tokens += Token(r, kind)
        }
        /** Match đầu tiên chưa bị đoạn khác chiếm (và qua [accept]). */
        fun Regex.firstFree(accept: (MatchResult) -> Boolean = { true }): MatchResult? =
            findAll(norm).firstOrNull { free(it.range) && accept(it) }

        var date: LocalDate? = null
        var time: LocalTime? = null
        var recurrence: String? = null
        var recurrenceDays = ""
        var priority: String? = null
        var category: String? = null
        val today = now.toLocalDate()

        // 1. Lặp lại ("mỗi thứ 2 và thứ 5", "hằng ngày")
        RE_RECUR_DAYS.firstFree()?.let { m ->
            val days = Regex(WD).findAll(m.groupValues[1]).mapNotNull { weekday(it.value) }.toSortedSet()
            if (days.isNotEmpty()) {
                recurrence = Recurrence.WEEKLY
                recurrenceDays = days.joinToString(",") { CODES.getValue(it) }
                take(m.range, Kind.RECURRENCE)
            }
        }
        if (recurrence == null) RE_RECUR_SIMPLE.firstFree()?.let { m ->
            recurrence = when (m.groupValues[1]) {
                "ngay" -> Recurrence.DAILY
                "tuan" -> Recurrence.WEEKLY
                else -> Recurrence.MONTHLY
            }
            take(m.range, Kind.RECURRENCE)
        }

        // 2. Buổi + ngày ("tối nay", "sáng mai") — đặt cả ngày lẫn giờ mặc định của buổi
        RE_PART_OF_DAY_DATE.firstFree()?.let { m ->
            date = if (m.groupValues[2] == "nay") today else today.plusDays(1)
            time = PART_OF_DAY.getValue(m.groupValues[1])
            take(m.range, Kind.DATE)
        }

        // 3. Ngày
        if (date == null) {
            RE_TODAY.firstFree()?.let { date = today; take(it.range, Kind.DATE) }
        }
        if (date == null) {
            RE_DAY_AFTER.firstFree()?.let { date = today.plusDays(2); take(it.range, Kind.DATE) }
        }
        if (date == null) {
            // "Mai" viết hoa giữa câu thường là tên người ("Gọi cho Mai") → không coi là ngày mai
            RE_TOMORROW.firstFree { m -> !(m.value == "mai" && m.range.first > 0 && text[m.range.first].isUpperCase()) }
                ?.let { date = today.plusDays(1); take(it.range, Kind.DATE) }
        }
        if (date == null) {
            RE_IN_DAYS.firstFree()?.let { m ->
                val n = (m.groupValues[1].ifEmpty { m.groupValues[3] }).toLong()
                val unit = m.groupValues[2].ifEmpty { m.groupValues[4] }
                date = if (unit == "tuan") today.plusWeeks(n) else today.plusDays(n)
                take(m.range, Kind.DATE)
            }
        }
        if (date == null) {
            RE_DATE.firstFree()?.let { m -> explicitDate(m, today)?.let { date = it; take(m.range, Kind.DATE) } }
        }
        if (date == null && recurrence == null) {
            RE_WEEKDAY.firstFree()?.let { m ->
                val wd = weekday(m.groupValues[1]) ?: return@let
                val nextWeek = m.groupValues[2].isNotBlank()
                date = if (nextWeek) {
                    today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).with(TemporalAdjusters.nextOrSame(wd))
                } else {
                    today.with(TemporalAdjusters.nextOrSame(wd))
                }
                take(m.range, Kind.DATE)
            }
        }
        if (date == null) {
            RE_WEEKEND.firstFree()?.let {
                date = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                take(it.range, Kind.DATE)
            }
        }
        if (date == null) {
            RE_NEXT_WEEK.firstFree()?.let {
                date = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                take(it.range, Kind.DATE)
            }
        }

        // 4. Giờ
        if (time == null) {
            RE_TIME.firstFree()?.let { m -> clock(m)?.let { time = it; take(m.range, Kind.TIME) } }
        }

        // 5. Ưu tiên
        RE_PRIORITY.firstFree()?.let { m ->
            val g = m.groups[1]!!
            priority = when (g.value.replace(" ", "")) {
                "!cao", "!gap", "!1", "p1" -> "HIGH"
                "!thap", "!3", "p3" -> "LOW"
                else -> "MEDIUM"
            }
            take(g.range, Kind.PRIORITY)
        }

        // 6. Danh mục "#Công việc" — ưu tiên khớp nhãn dài nhất (nhãn có thể chứa dấu cách)
        RE_CATEGORY.firstFree()?.let { m ->
            val hashAt = norm.indexOf('#', m.range.first)
            val rest = norm.substring(hashAt + 1)
            val matched = categories
                .flatMap { c -> listOf(c to normalize(c.label), c to normalize(c.code)) }
                .sortedByDescending { it.second.length }
                .firstOrNull { (_, key) ->
                    key.isNotEmpty() && rest.startsWith(key) && (rest.length == key.length || rest[key.length].isWhitespace())
                }
            if (matched != null) {
                category = matched.first.code
                take(hashAt..hashAt + matched.second.length, Kind.CATEGORY)
            } else {
                val word = m.groups[1]!!
                category = text.substring(word.range).take(50)
                take(hashAt..word.range.last, Kind.CATEGORY)
            }
        }

        // ── Ghép ngày + giờ ──
        var resolvedDate = date
        if (resolvedDate == null && recurrence != null) {
            // Lặp mà không nói ngày → lần đầu là hôm nay, hoặc thứ gần nhất trong các thứ đã chọn
            val days = RecurrenceRules.parseWeekdays(recurrenceDays)
            resolvedDate = if (days.isEmpty()) today
            else (0..6).map { today.plusDays(it.toLong()) }.first { it.dayOfWeek in days }
        }
        if (resolvedDate == null && time != null) {
            // Chỉ có giờ: hôm nay nếu giờ đó chưa qua, không thì ngày mai
            resolvedDate = if (time!!.isAfter(now.toLocalTime())) today else today.plusDays(1)
        }
        val allDay = resolvedDate != null && time == null
        val dueAt = resolvedDate?.let { d ->
            d.atTime(time ?: ALL_DAY_TIME).atZone(zone).toInstant().toEpochMilli()
        }

        // Từ nối ngay trước ngày/giờ ("họp VÀO thứ 6", "LÚC 3h", "hạn NGÀY 15/10") cũng bỏ khỏi tiêu đề
        tokens.filter { it.kind == Kind.DATE || it.kind == Kind.TIME }.forEach { token ->
            var end = token.range.first - 1
            while (end >= 0 && norm[end].isWhitespace()) end--
            var start = end
            while (start >= 0 && norm[start].isLetter()) start--
            val word = if (end >= 0) norm.substring(start + 1, end + 1) else ""
            if (word in CONNECTORS) (start + 1..end).forEach { taken[it] = true }
        }

        val title = buildString {
            text.forEachIndexed { i, c -> append(if (taken[i]) ' ' else c) }
        }.replace(Regex("""\s+"""), " ")
            .trim(' ', ',', '-', '–', '.')

        return Result(
            title = title,
            dueAt = dueAt,
            allDay = allDay,
            priority = priority,
            category = category,
            recurrence = recurrence,
            recurrenceDays = recurrenceDays,
            tokens = tokens.sortedBy { it.range.first }
        )
    }

    // ── Trợ giúp ─────────────────────────────────────────────────────────────

    private val CONNECTORS = setOf("luc", "vao", "ngay", "han", "truoc")

    private val PART_OF_DAY = mapOf(
        "sang" to LocalTime.of(9, 0),
        "trua" to LocalTime.of(12, 0),
        "chieu" to LocalTime.of(15, 0),
        "toi" to LocalTime.of(20, 0)
    )

    private val CODES = mapOf(
        DayOfWeek.MONDAY to "MON", DayOfWeek.TUESDAY to "TUE", DayOfWeek.WEDNESDAY to "WED",
        DayOfWeek.THURSDAY to "THU", DayOfWeek.FRIDAY to "FRI", DayOfWeek.SATURDAY to "SAT", DayOfWeek.SUNDAY to "SUN"
    )

    private fun weekday(raw: String): DayOfWeek? {
        val s = raw.replace(Regex("""\s+"""), "")
        return when (s) {
            "thu2", "thuhai", "t2" -> DayOfWeek.MONDAY
            "thu3", "thuba", "t3" -> DayOfWeek.TUESDAY
            "thu4", "thutu", "t4" -> DayOfWeek.WEDNESDAY
            "thu5", "thunam", "t5" -> DayOfWeek.THURSDAY
            "thu6", "thusau", "t6" -> DayOfWeek.FRIDAY
            "thu7", "thubay", "t7" -> DayOfWeek.SATURDAY
            "chunhat", "cn" -> DayOfWeek.SUNDAY
            else -> null
        }
    }

    /** "dd/mm" hoặc "dd/mm/yyyy"; thiếu năm mà ngày đã qua → năm sau. */
    private fun explicitDate(m: MatchResult, today: LocalDate): LocalDate? {
        val day = m.groupValues[1].toInt()
        val month = m.groupValues[2].toInt()
        val yearRaw = m.groupValues[3]
        return runCatching {
            if (yearRaw.isNotEmpty()) {
                val y = yearRaw.toInt().let { if (it < 100) 2000 + it else it }
                LocalDate.of(y, month, day)
            } else {
                val candidate = LocalDate.of(today.year, month, day)
                if (candidate.isBefore(today)) candidate.plusYears(1) else candidate
            }
        }.getOrNull()
    }

    /**
     * "3h", "15:30", "3h30 chiều", "9 giờ sáng", "8h rưỡi tối". Không ghi buổi: 1–6 giờ hiểu là chiều
     * (ít ai hẹn việc lúc 3 giờ sáng).
     */
    private fun clock(m: MatchResult): LocalTime? {
        var hour = m.groupValues[1].toInt()
        val minute = when (val raw = m.groupValues[2]) {
            "" -> 0
            "ruoi" -> 30
            else -> raw.toInt()
        }
        when (m.groupValues[3]) {
            "chieu", "toi" -> if (hour < 12) hour += 12
            "trua" -> if (hour in 1..5) hour += 12
            "dem" -> if (hour in 6..11) hour += 12
            "sang" -> if (hour == 12) hour = 0
            "" -> if (hour in 1..6) hour += 12
        }
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    /**
     * Bỏ dấu + chữ thường, GIỮ NGUYÊN độ dài (mỗi ký tự → đúng một ký tự) để vị trí match trên chuỗi đã chuẩn hóa
     * dùng được luôn cho chuỗi gốc.
     */
    fun normalize(s: String): String = buildString(s.length) {
        s.forEach { c ->
            val base = when (c) {
                'đ' -> 'd'
                'Đ' -> 'd'
                else -> Normalizer.normalize(c.toString(), Normalizer.Form.NFD).firstOrNull() ?: c
            }
            append(base.lowercaseChar())
        }
    }
}
