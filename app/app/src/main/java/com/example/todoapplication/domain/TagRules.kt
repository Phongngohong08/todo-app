package com.example.todoapplication.domain

/**
 * Chuẩn hóa nhãn — GIỐNG HỆT task.NormalizeTags của backend để máy và server không lệch nhau:
 * bỏ "@"/"#" đầu, chữ thường, khoảng trắng → "-", bỏ dấu phẩy (dấu ngăn khi lưu), bỏ trùng;
 * tối đa [MAX_TAGS] nhãn, mỗi nhãn tối đa [MAX_LENGTH] ký tự.
 */
object TagRules {
    const val MAX_TAGS = 10
    const val MAX_LENGTH = 30

    fun normalize(tag: String): String {
        val t = tag.trim().lowercase().trimStart('@', '#').replace(",", "")
            .split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString("-")
        return if (t.codePointCount(0, t.length) > MAX_LENGTH) {
            t.substring(0, t.offsetByCodePoints(0, MAX_LENGTH))
        } else t
    }

    fun normalize(tags: Iterable<String>): List<String> =
        tags.map(::normalize).filter { it.isNotEmpty() }.distinct().take(MAX_TAGS)

    /** Lưu trong Room: "nhà,gấp". */
    fun encode(tags: List<String>): String = tags.joinToString(",")

    fun decode(stored: String): List<String> = stored.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

/** Lời nhắc thêm: 0..7 ngày trước mốc nhắc, không trùng, tăng dần, tối đa 5 — giống backend. */
object ReminderRules {
    const val MAX_REMINDERS = 5
    const val MAX_MINUTES = 7 * 24 * 60

    fun normalize(minutes: Iterable<Int>): List<Int> =
        minutes.filter { it in 0..MAX_MINUTES }.distinct().sorted().take(MAX_REMINDERS)

    fun encode(minutes: List<Int>): String = minutes.joinToString(",")

    fun decode(stored: String): List<Int> = stored.split(",").mapNotNull { it.trim().toIntOrNull() }

    /**
     * Tách lựa chọn của người dùng thành (lời nhắc chính, các lời nhắc thêm). Lời nhắc chính là cái sát giờ
     * nhất — client cũ chỉ biết reminder_offset_minutes vẫn nhắc đúng lúc quan trọng nhất.
     */
    fun split(selected: Collection<Int>): Pair<Int, List<Int>> {
        val all = normalize(selected).ifEmpty { listOf(0) }
        return all.first() to all.drop(1)
    }
}
