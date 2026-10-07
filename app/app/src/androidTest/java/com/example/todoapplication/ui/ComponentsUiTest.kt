package com.example.todoapplication.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.todoapplication.domain.HabitDay
import com.example.todoapplication.domain.HabitSummary
import com.example.todoapplication.domain.model.Habit
import com.example.todoapplication.domain.model.Task
import com.example.todoapplication.ui.screens.HabitCard
import com.example.todoapplication.ui.screens.QuickCreateSheet
import com.example.todoapplication.ui.screens.SelectionBar
import com.example.todoapplication.ui.screens.TaskCard
import com.example.todoapplication.ui.theme.TodoApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Test giao diện Compose cho các thành phần dùng chung: dựng composable riêng lẻ (không cần mạng / database),
 * thao tác như người dùng và kiểm tra callback + cây ngữ nghĩa (semantics) mà TalkBack đọc.
 */
@RunWith(AndroidJUnit4::class)
class ComponentsUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val task = Task(id = "t1", title = "Nộp báo cáo quý", priority = "HIGH", tags = listOf("gấp"))

    @Test
    fun taskCard_showsTitleAndTags_andExposesAccessibilityActions() {
        var completed = 0
        var postponed = 0
        compose.setContent {
            TodoApplicationTheme {
                TaskCard(
                    task = task,
                    onCardClick = {},
                    onToggleComplete = { completed++ },
                    onDeleteClick = {},
                    onPostpone = { postponed++ }
                )
            }
        }
        compose.onNodeWithText("Nộp báo cáo quý", substring = true).assertIsDisplayed()
        compose.onNodeWithText("@gấp", substring = true).assertIsDisplayed()

        // TalkBack: menu hành động thay cho vuốt
        val card = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions) and hasText("Nộp báo cáo quý", substring = true))
        val actions = card.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertTrue(actions.any { it.label == "Hoàn thành" })
        actions.first { it.label == "Hoãn sang ngày khác" }.action()
        actions.first { it.label == "Hoàn thành" }.action()
        compose.runOnIdle {
            assertEquals(1, postponed)
            assertEquals(1, completed)
        }
    }

    @Test
    fun selectionBar_showsCountAndRunsBulkComplete() {
        var completed = false
        compose.setContent {
            TodoApplicationTheme {
                SelectionBar(
                    count = 3, onClose = {}, onSelectAll = {}, onComplete = { completed = true },
                    onMyDay = {}, onPostpone = {}, onPriority = {}, onDelete = {}
                )
            }
        }
        compose.onNodeWithText("Đã chọn 3").assertIsDisplayed()
        compose.onNodeWithContentDescription("Xong").performClick()
        compose.runOnIdle { assertTrue(completed) }
    }

    @Test
    fun habitCard_daysAreReadableAndToggleable() {
        val today = LocalDate.of(2026, 10, 7)
        var toggled: LocalDate? = null
        val summary = HabitSummary(
            habit = Habit(id = "h", name = "Uống nước", emoji = "💧"),
            doneToday = false, dueToday = true, streak = 2, weekDone = 2, weekTarget = 7, rate30 = 0.5f,
            lastDays = (6 downTo 0).map { HabitDay(today.minusDays(it.toLong()), scheduled = true, done = it in 1..2) }
        )
        compose.setContent { TodoApplicationTheme { HabitCard(summary, onToggle = { toggled = it }, onEdit = {}) } }

        compose.onNodeWithText("🔥 2 ngày").assertIsDisplayed()
        compose.onNode(hasContentDescription("T4 7/10: chưa làm")).performClick()
        compose.runOnIdle { assertEquals(today, toggled) }
        compose.onNode(hasContentDescription("T3 6/10: đã làm")).assertIsDisplayed()
    }

    @Test
    fun quickCreate_parsesTagsAndPriorityWhileTyping() {
        compose.setContent {
            TodoApplicationTheme {
                QuickCreateSheet(
                    categories = listOf("PERSONAL", "WORK", "OTHER"),
                    onDismiss = {}, onCreate = {}, onMoreDetails = {}, onAiAdd = {}, onTemplates = {}
                )
            }
        }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)).performTextInput("Mua rau !cao @nhà")
        compose.onNodeWithText("Hiểu là:").assertIsDisplayed()
        compose.onNodeWithText("@nhà").assertIsDisplayed()
        compose.onNodeWithText("🚩 Cao").assertIsDisplayed()
    }
}
