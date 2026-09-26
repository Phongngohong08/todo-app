package com.example.todoapplication.domain

/**
 * [TẦNG DOMAIN] Thứ tự kéo-thả bằng "fractional indexing": mỗi task có một số thực sortOrder, danh sách sắp
 * tăng dần. Thả task vào giữa A và B chỉ cần gán giá trị ở giữa → chỉ MỘT task thay đổi và cần đồng bộ,
 * thay vì đánh số lại cả danh sách.
 */
object SortOrder {

    /** Task mới lên đầu danh sách: giá trị giảm dần theo thời gian tạo. */
    fun forNewTask(nowMillis: Long): Double = -nowMillis.toDouble()

    /**
     * Giá trị cho task được thả giữa [before] (task phía trên, null nếu thả lên đầu)
     * và [after] (task phía dưới, null nếu thả xuống cuối).
     * Trả null khi hai hàng xóm đã quá sát nhau (hết độ chính xác Double) → caller đánh số lại cả danh sách.
     */
    fun between(before: Double?, after: Double?): Double? {
        val value = when {
            before == null && after == null -> 0.0
            before == null -> after!! - 1.0
            after == null -> before + 1.0
            else -> before + (after - before) / 2
        }
        if (before != null && value <= before) return null
        if (after != null && value >= after) return null
        return value
    }

    /** Đánh số lại cách đều cho cả danh sách (hiếm khi cần). */
    fun rebalanced(count: Int): List<Double> = List(count) { it.toDouble() }
}
