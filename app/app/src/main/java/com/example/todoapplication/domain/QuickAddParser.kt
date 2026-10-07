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
 * Hiểu được cả khi gõ không dấu ("mai 3h chieu"), và cả tiếng Anh bất kể ngôn ngữ app đang dùng:
 *   "Submit report tomorrow 3pm !high #Work", "Water plants every monday and thursday", "Call mom next friday".
 * Câu phức tạp hơn thì vẫn còn nút phân tích bằng AI.
 */
object QuickAddParser {

    enum class Kind { DATE, TIME, PRIORITY, CATEGORY, RECURRENCE, TAG }

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
        val tokens: List<Token>,
        /** Nhãn gõ sau "@" (đã chuẩn hóa). */
        val tags: List<String> = emptyList()
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
    private val RE_PRIORITY = Regex(
        """(?:^|\s)(!(?:cao|gap|1|tb|trung\s*binh|vua|2|thap|3|high|urgent|medium|med|low)|p[123])(?=\s|$)"""
    )
    private val RE_CATEGORY = Regex("""(?:^|\s)#(\S+)""")
    private val RE_TAG = Regex("""(?:^|\s)@(\S+)""")

    // ── Tiếng Anh ("tomorrow 3pm", "every monday and thursday", "next friday", "in 2 days", "!high") ──
    // Thứ trong tuần chỉ nhận tên đầy đủ: viết tắt (mon, thu, sat…) trùng chữ tiếng Việt đã bỏ dấu (món, thu, sát…).
    private const val EWD = """(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)"""
    private const val MONTH =
        """(?:january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sept?|oct|nov|dec)"""
    private val RE_EN_RECUR_DAYS = Regex("""\bevery\s+($EWD(?:\s*(?:,|and|&)\s*$EWD)*)\b""")
    private val RE_EN_RECUR_SIMPLE = Regex("""\b(?:every\s*(day|week|month)|(daily|weekly|monthly))\b""")
    private val RE_EN_PART_OF_DAY_DATE = Regex(
        """\b(?:this\s+(morning|afternoon|evening)|(tonight)|tomorrow\s+(morning|afternoon|evening|night))\b"""
    )
    private val RE_EN_TODAY = Regex("""\btoday\b""")
    private val RE_EN_TOMORROW = Regex("""\b(?:tomorrow|tmrw?)\b""")
    private val RE_EN_DAY_AFTER = Regex("""\b(?:the\s+)?day\s+after\s+tomorrow\b""")
    private val RE_EN_IN_DAYS = Regex("""\bin\s+(\d{1,3})\s+(days?|weeks?)\b""")
    private val RE_EN_WEEKDAY = Regex("""\b(next\s+)?($EWD)(\s+next\s+week)?\b""")
    private val RE_EN_NEXT_WEEK = Regex("""\bnext\s+week\b""")
    private val RE_EN_WEEKEND = Regex("""\b(?:this\s+)?weekend\b""")
    private val RE_EN_MONTH_DATE = Regex(
        """\b(?:($MONTH)\.?\s+(\d{1,2})(?:st|nd|rd|th)?|(\d{1,2})(?:st|nd|rd|th)?\s+($MONTH))\b"""
    )
    private val RE_EN_TIME = Regex("""\b(?:at\s+)?(?:(\d{1,2})(?::(\d{2}))?\s*(am|pm)|(noon))\b""")

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
        if (recurrence == null) RE_EN_RECUR_DAYS.firstFree()?.let { m ->
            val days = Regex(EWD).findAll(m.groupValues[1]).mapNotNull { englishWeekday(it.value) }.toSortedSet()
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
        if (recurrence == null) RE_EN_RECUR_SIMPLE.firstFree()?.let { m ->
            recurrence = when (m.groupValues[1].ifEmpty { m.groupValues[2] }) {
                "day", "daily" -> Recurrence.DAILY
                "week", "weekly" -> Recurrence.WEEKLY
                else -> Recurrence.MONTHLY
            }
            take(m.range, Kind.RECURRENCE)
        }

        // 2. Buổi + ngày ("tối nay", "sáng mai", "tonight", "tomorrow morning") — đặt cả ngày lẫn giờ mặc định của buổi
        RE_PART_OF_DAY_DATE.firstFree()?.let { m ->
            date = if (m.groupValues[2] == "nay") today else today.plusDays(1)
            time = PART_OF_DAY.getValue(m.groupValues[1])
            take(m.range, Kind.DATE)
        }
        if (date == null) RE_EN_PART_OF_DAY_DATE.firstFree()?.let { m ->
            val tomorrowPart = m.groupValues[3]
            date = if (tomorrowPart.isEmpty()) today else today.plusDays(1)
            time = PART_OF_DAY_EN.getValue(tomorrowPart.ifEmpty { m.groupValues[1].ifEmpty { "evening" } })
            take(m.range, Kind.DATE)
        }

        // 3. Ngày
        if (date == null) {
            (RE_TODAY.firstFree() ?: RE_EN_TODAY.firstFree())?.let { date = today; take(it.range, Kind.DATE) }
        }
        if (date == null) {
            (RE_DAY_AFTER.firstFree() ?: RE_EN_DAY_AFTER.firstFree())?.let { date = today.plusDays(2); take(it.range, Kind.DATE) }
        }
        if (date == null) {
            // "Mai" viết hoa giữa câu thường là tên người ("Gọi cho Mai") → không coi là ngày mai
            (RE_TOMORROW.firstFree { m -> !(m.value == "mai" && m.range.first > 0 && text[m.range.first].isUpperCase()) }
                ?: RE_EN_TOMORROW.firstFree())
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
            RE_EN_IN_DAYS.firstFree()?.let { m ->
                val n = m.groupValues[1].toLong()
                date = if (m.groupValues[2].startsWith("week")) today.plusWeeks(n) else today.plusDays(n)
                take(m.range, Kind.DATE)
            }
        }
        if (date == null) {
            RE_DATE.firstFree()?.let { m -> explicitDate(m, today)?.let { date = it; take(m.range, Kind.DATE) } }
        }
        if (date == null) {
            // "May 5": chỉ khi gõ đúng chữ không dấu ("máy 5" không phải tháng 5)
            RE_EN_MONTH_DATE.firstFree { m -> text.substring(m.range).lowercase() == m.value }
                ?.let { m -> monthNameDate(m, today)?.let { date = it; take(m.range, Kind.DATE) } }
        }
        if (date == null && recurrence == null) {
            RE_WEEKDAY.firstFree()?.let { m ->
                val wd = weekday(m.groupValues[1]) ?: return@let
                date = nextWeekday(today, wd, nextWeek = m.groupValues[2].isNotBlank())
                take(m.range, Kind.DATE)
            }
        }
        if (date == null && recurrence == null) {
            RE_EN_WEEKDAY.firstFree()?.let { m ->
                val wd = englishWeekday(m.groupValues[2]) ?: return@let
                date = nextWeekday(today, wd, nextWeek = m.groupValues[1].isNotBlank() || m.groupValues[3].isNotBlank())
                take(m.range, Kind.DATE)
            }
        }
        if (date == null) {
            (RE_WEEKEND.firstFree() ?: RE_EN_WEEKEND.firstFree())?.let {
                date = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                take(it.range, Kind.DATE)
            }
        }
        if (date == null) {
            (RE_NEXT_WEEK.firstFree() ?: RE_EN_NEXT_WEEK.firstFree())?.let {
                date = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                take(it.range, Kind.DATE)
            }
        }

        // 4. Giờ ("3pm" xét trước "3h" vì RE_TIME không nhận đuôi am/pm)
        if (time == null) {
            RE_EN_TIME.firstFree()?.let { m -> englishClock(m)?.let { time = it; take(m.range, Kind.TIME) } }
        }
        if (time == null) {
            RE_TIME.firstFree()?.let { m -> clock(m)?.let { time = it; take(m.range, Kind.TIME) } }
        }

        // 5. Ưu tiên
        RE_PRIORITY.firstFree()?.let { m ->
            val g = m.groups[1]!!
            priority = when (g.value.replace(" ", "")) {
                "!cao", "!gap", "!1", "p1", "!high", "!urgent" -> "HIGH"
                "!thap", "!3", "p3", "!low" -> "LOW"
                else -> "MEDIUM"
            }
            take(g.range, Kind.PRIORITY)
        }

        // 6. Danh mục "#Công việc" — ưu tiên khớp nhãn dài nhất (nhãn có thể chứa dấu cách).
        // Danh sách có sẵn khớp được tên ở cả hai ngôn ngữ ("#work" khi app đang tiếng Việt và ngược lại).
        RE_CATEGORY.firstFree()?.let { m ->
            val hashAt = norm.indexOf('#', m.range.first)
            val rest = norm.substring(hashAt + 1)
            val codes = categories.map { it.code }.toSet()
            val matched = (categories + BUILT_IN_ALIASES.filter { it.code in codes })
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

        // 7. Nhãn "@nhà @gấp" (nhiều nhãn được)
        val tags = mutableListOf<String>()
        RE_TAG.findAll(norm).filter { free(it.range) }.toList().forEach { m ->
            val word = m.groups[1]!!
            val tag = TagRules.normalize(text.substring(word.range))
            if (tag.isNotEmpty()) {
                tags += tag
                take(word.range.first - 1..word.range.last, Kind.TAG)
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
            tokens = tokens.sortedBy { it.range.first },
            tags = TagRules.normalize(tags)
        )
    }

    // ── Trợ giúp ─────────────────────────────────────────────────────────────

    private val CONNECTORS = setOf("luc", "vao", "ngay", "han", "truoc", "at", "on", "by", "due", "before")

    private val PART_OF_DAY = mapOf(
        "sang" to LocalTime.of(9, 0),
        "trua" to LocalTime.of(12, 0),
        "chieu" to LocalTime.of(15, 0),
        "toi" to LocalTime.of(20, 0)
    )

    private val PART_OF_DAY_EN = mapOf(
        "morning" to LocalTime.of(9, 0),
        "afternoon" to LocalTime.of(15, 0),
        "evening" to LocalTime.of(20, 0),
        "tonight" to LocalTime.of(20, 0),
        "night" to LocalTime.of(20, 0)
    )

    /** Tên danh sách có sẵn ở cả hai ngôn ngữ, để "#" khớp dù app đang dùng ngôn ngữ nào. */
    private val BUILT_IN_ALIASES = listOf(
        CategoryOption("PERSONAL", "Cá nhân"), CategoryOption("PERSONAL", "Personal"),
        CategoryOption("WORK", "Công việc"), CategoryOption("WORK", "Work"),
        CategoryOption("OTHER", "Hộp thư đến"), CategoryOption("OTHER", "Inbox")
    )

    private fun englishWeekday(name: String): DayOfWeek? =
        DayOfWeek.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Thứ [wd] gần nhất (gồm hôm nay); [nextWeek] ("thứ 2 tuần sau", "next friday") → thứ đó của tuần sau. */
    private fun nextWeekday(today: LocalDate, wd: DayOfWeek, nextWeek: Boolean): LocalDate =
        if (nextWeek) today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).with(TemporalAdjusters.nextOrSame(wd))
        else today.with(TemporalAdjusters.nextOrSame(wd))

    /** "oct 15", "15 october"; ngày đã qua trong năm nay → năm sau. */
    private fun monthNameDate(m: MatchResult, today: LocalDate): LocalDate? {
        val monthName = m.groupValues[1].ifEmpty { m.groupValues[4] }
        val day = m.groupValues[2].ifEmpty { m.groupValues[3] }.toIntOrNull() ?: return null
        val month = java.time.Month.entries.firstOrNull { it.name.lowercase().startsWith(monthName.take(3)) } ?: return null
        return runCatching {
            val candidate = LocalDate.of(today.year, month, day)
            if (candidate.isBefore(today)) candidate.plusYears(1) else candidate
        }.getOrNull()
    }

    /** "3pm", "9:30 am", "12am", "noon". */
    private fun englishClock(m: MatchResult): LocalTime? {
        if (m.groupValues[4] == "noon") return LocalTime.NOON
        var hour = m.groupValues[1].toIntOrNull() ?: return null
        val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
        if (hour !in 1..12 || minute !in 0..59) return null
        if (m.groupValues[3] == "pm" && hour < 12) hour += 12
        if (m.groupValues[3] == "am" && hour == 12) hour = 0
        return LocalTime.of(hour, minute)
    }

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

    /**
     * "dd/mm" hoặc "dd/mm/yyyy"; thiếu năm mà ngày đã qua → năm sau.
     * Không hợp lệ theo dd/mm nhưng hợp lệ theo mm/dd (kiểu Mỹ, "10/15") → hiểu là mm/dd.
     */
    private fun explicitDate(m: MatchResult, today: LocalDate): LocalDate? {
        val a = m.groupValues[1].toInt()
        val b = m.groupValues[2].toInt()
        val yearRaw = m.groupValues[3]
        fun build(day: Int, month: Int): LocalDate? = runCatching {
            if (yearRaw.isNotEmpty()) {
                val y = yearRaw.toInt().let { if (it < 100) 2000 + it else it }
                LocalDate.of(y, month, day)
            } else {
                val candidate = LocalDate.of(today.year, month, day)
                if (candidate.isBefore(today)) candidate.plusYears(1) else candidate
            }
        }.getOrNull()
        return build(day = a, month = b) ?: build(day = b, month = a)
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
