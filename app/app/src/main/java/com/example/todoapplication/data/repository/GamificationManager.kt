package com.example.todoapplication.data.repository

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.todoapplication.data.local.AppDatabase
import com.example.todoapplication.data.local.GamificationEntity
import com.example.todoapplication.data.model.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/** Một huy hiệu thành tích. */
data class Badge(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val isUnlocked: (GamificationState) -> Boolean
)

/** Trạng thái gamification hiển thị lên UI. */
data class GamificationState(
    val totalXp: Int = 0,
    val completedCount: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val unlockedBadgeIds: Set<String> = emptySet()
) {
    /** Cấp độ hiện tại (bắt đầu từ 1). */
    val level: Int get() = computeLevel(totalXp).first

    /** XP đã tích trong cấp hiện tại. */
    val xpInLevel: Int get() = computeLevel(totalXp).second

    /** XP cần để lên cấp kế tiếp. */
    val xpForNextLevel: Int get() = computeLevel(totalXp).third

    /** Tỉ lệ tiến độ tới cấp kế (0f..1f). */
    val levelProgress: Float
        get() = if (xpForNextLevel == 0) 0f else xpInLevel.toFloat() / xpForNextLevel.toFloat()

    companion object {
        /** Trả về (level, xpTrongLevel, xpCầnChoLevelKế). Mỗi cấp cần thêm 50 XP so với cấp trước. */
        fun computeLevel(totalXp: Int): Triple<Int, Int, Int> {
            var level = 1
            var need = 100
            var remaining = totalXp
            while (remaining >= need) {
                remaining -= need
                level++
                need += 50
            }
            return Triple(level, remaining, need)
        }
    }
}

/**
 * Quản lý điểm thưởng / chuỗi ngày / huy hiệu — dữ liệu thuần client lưu trong Room.
 * Mirror pattern holder singleton như [ThemeController]; `state` là Compose state nên đổi sẽ recompose.
 */
object GamificationManager {
    var state by mutableStateOf(GamificationState())
        private set

    private val scope = CoroutineScope(Dispatchers.IO)

    val allBadges: List<Badge> = listOf(
        Badge("first_task", "🌱", "Khởi đầu", "Hoàn thành công việc đầu tiên") { it.completedCount >= 1 },
        Badge("ten_tasks", "⭐", "Chăm chỉ", "Hoàn thành 10 công việc") { it.completedCount >= 10 },
        Badge("fifty_tasks", "🏆", "Bậc thầy", "Hoàn thành 50 công việc") { it.completedCount >= 50 },
        Badge("streak_3", "🔥", "Bền bỉ", "Chuỗi 3 ngày liên tiếp") { it.currentStreak >= 3 || it.longestStreak >= 3 },
        Badge("streak_7", "⚡", "Không thể cản", "Chuỗi 7 ngày liên tiếp") { it.currentStreak >= 7 || it.longestStreak >= 7 },
        Badge("level_5", "👑", "Lên đỉnh", "Đạt cấp độ 5") { it.level >= 5 },
        Badge("xp_500", "💎", "Kho báu", "Tích lũy 500 XP") { it.totalXp >= 500 }
    )

    /** Nạp trạng thái từ Room (gọi một lần khi khởi động app). */
    fun init(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            val dao = AppDatabase.get(appContext).gamificationDao()
            val entity = dao.get() ?: GamificationEntity().also { dao.upsert(it) }
            withContext(Dispatchers.Main) {
                state = entity.toState()
            }
        }
    }

    /**
     * Ghi nhận một công việc vừa hoàn thành: cộng XP, cập nhật chuỗi ngày, mở khóa huy hiệu.
     * Trả về danh sách huy hiệu MỚI mở khóa (để hiển thị chúc mừng).
     */
    suspend fun recordCompletion(context: Context, task: Task): List<Badge> {
        val dao = AppDatabase.get(context.applicationContext).gamificationDao()
        val current = dao.get() ?: GamificationEntity()

        // XP theo độ ưu tiên
        val gainedXp = 10 + when (task.priority) {
            "HIGH" -> 15
            "MEDIUM" -> 5
            else -> 0
        }

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val newStreak = when (current.lastCompletionDate) {
            today -> current.currentStreak.coerceAtLeast(1)          // đã hoàn thành hôm nay rồi
            yesterdayOf(today) -> current.currentStreak + 1          // tiếp nối hôm qua
            "" -> 1                                                   // lần đầu
            else -> 1                                                 // đứt chuỗi
        }

        val before = current.toState()
        val updated = current.copy(
            totalXp = current.totalXp + gainedXp,
            completedCount = current.completedCount + 1,
            currentStreak = newStreak,
            longestStreak = maxOf(current.longestStreak, newStreak),
            lastCompletionDate = today
        )

        // Tính huy hiệu mới mở khóa
        val afterState = updated.toState()
        val previouslyUnlocked = allBadges.filter { it.isUnlocked(before) }.map { it.id }.toSet()
        val nowUnlocked = allBadges.filter { it.isUnlocked(afterState) }
        val newlyUnlocked = nowUnlocked.filter { it.id !in previouslyUnlocked }
        val allUnlockedIds = nowUnlocked.map { it.id }

        val saved = updated.copy(unlockedBadges = allUnlockedIds.joinToString(","))
        dao.upsert(saved)

        withContext(Dispatchers.Main) {
            state = saved.toState()
        }
        return newlyUnlocked
    }

    private fun yesterdayOf(today: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val cal = Calendar.getInstance().apply { time = sdf.parse(today)!! }
            cal.add(Calendar.DAY_OF_MONTH, -1)
            sdf.format(cal.time)
        } catch (e: Exception) {
            ""
        }
    }

    private fun GamificationEntity.toState() = GamificationState(
        totalXp = totalXp,
        completedCount = completedCount,
        currentStreak = currentStreak,
        longestStreak = longestStreak,
        unlockedBadgeIds = if (unlockedBadges.isBlank()) emptySet()
                           else unlockedBadges.split(",").toSet()
    )
}
