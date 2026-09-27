package com.example.todoapplication.domain

import com.example.todoapplication.data.model.PlanSlot
import java.time.LocalTime

/**
 * [TẦNG DOMAIN] Chỉnh tay lịch trình AI trong ngày (như timeline của TickTick / Structured):
 * đổi giờ một khung thì các khung phía sau bị chồng sẽ được DỒN xuống, giữ nguyên thời lượng từng khung.
 */
object PlanLogic {

    private val END_OF_DAY: LocalTime = LocalTime.of(23, 59)

    fun minutes(slot: PlanSlot): Int {
        val s = parse(slot.start) ?: return 0
        val e = parse(slot.end) ?: return 0
        return (e.toSecondOfDay() - s.toSecondOfDay()) / 60
    }

    /**
     * Đặt khung [index] bắt đầu lúc [newStart] (giữ thời lượng), sắp lại theo giờ, rồi dồn các khung sau nếu bị chồng.
     * null nếu không xếp vừa trong ngày (khung cuối vượt 23:59).
     */
    fun moveSlot(slots: List<PlanSlot>, index: Int, newStart: LocalTime): List<PlanSlot>? {
        if (index !in slots.indices) return slots
        val target = slots[index]
        val length = minutes(target).coerceAtLeast(5)
        val newEnd = newStart.plusMinutes(length.toLong())
        if (newEnd.isBefore(newStart) || newEnd.isAfter(END_OF_DAY)) return null

        val moved = target.copy(start = fmt(newStart), end = fmt(newEnd))
        val others = slots.filterIndexed { i, _ -> i != index }
        // Khung được kéo tới đứng trước các khung trùng giờ bắt đầu (người dùng muốn nó ở đó)
        val ordered = (others + moved).sortedWith(compareBy<PlanSlot> { it.start }.thenBy { if (it === moved) 0 else 1 })
        return cascade(ordered)
    }

    /** Dồn khung chồng lên khung trước; null nếu tràn qua cuối ngày. */
    fun cascade(sorted: List<PlanSlot>): List<PlanSlot>? {
        val out = mutableListOf<PlanSlot>()
        var cursor: LocalTime? = null
        for (slot in sorted) {
            val start = parse(slot.start) ?: return null
            val length = minutes(slot).coerceAtLeast(5).toLong()
            val actualStart = if (cursor != null && start.isBefore(cursor)) cursor else start
            val end = actualStart.plusMinutes(length)
            if (end.isBefore(actualStart) || end.isAfter(END_OF_DAY)) return null
            out += slot.copy(start = fmt(actualStart), end = fmt(end))
            cursor = end
        }
        return out
    }

    /**
     * Lịch "đã cũ" khi phạm vi việc cần xếp đổi so với lúc tạo lịch: có việc mới cần làm hôm nay chưa được xếp,
     * hoặc có khung trỏ tới việc đã bị xóa. (Việc đã hoàn thành thì không tính — khung đó chỉ hiện là xong.)
     */
    fun isStale(planTaskIds: Set<String>, pendingInScope: Set<String>, existingIds: Set<String>): Boolean =
        pendingInScope.any { it !in planTaskIds } || planTaskIds.any { it.isNotEmpty() && it !in existingIds }

    private fun parse(s: String): LocalTime? = runCatching { LocalTime.parse(s.padStart(5, '0')) }.getOrNull()
    private fun fmt(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)
}
