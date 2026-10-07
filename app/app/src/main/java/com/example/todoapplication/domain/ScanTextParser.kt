package com.example.todoapplication.domain

/**
 * Tách chữ nhận được từ ảnh (tờ giấy ghi việc, bảng trắng, danh sách in) thành các dòng có thể là một việc:
 * bỏ ký hiệu đầu dòng (•, -, *, ☐, [ ], 1., a)), bỏ dòng quá ngắn / chỉ có số, bỏ trùng; tối đa [MAX_LINES].
 */
object ScanTextParser {
    const val MAX_LINES = 30
    private const val MAX_LENGTH = 200

    // Một hoặc nhiều ký hiệu chồng nhau ("- [ ] việc", "1. • việc")
    private val BULLET = Regex("""^\s*(?:(?:[-•*·▪●○◦☐☑✓✔]|\[\s?[xX ]?\s?]|\(\s?\)|\d{1,2}[.)]|[a-zA-Z][.)])\s+)+""")
    private val ONLY_SYMBOLS = Regex("""^[\p{P}\p{S}\d\s]+$""")

    fun taskLines(text: String): List<String> =
        text.lines()
            .map { it.replace(BULLET, "").replace(Regex("""\s+"""), " ").trim().take(MAX_LENGTH) }
            .filter { it.length >= 3 && !ONLY_SYMBOLS.matches(it) }
            .distinctBy { it.lowercase() }
            .take(MAX_LINES)
}
