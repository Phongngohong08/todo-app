package com.example.todoapplication.ui.viewmodel

import com.example.todoapplication.data.repository.AuthRepository
import com.example.todoapplication.data.repository.CategoryRepository
import com.example.todoapplication.data.repository.LocalPrefs
import com.example.todoapplication.data.repository.PreferencesRepository
import com.example.todoapplication.data.repository.TaskRepository
import com.example.todoapplication.data.sync.SyncController
import com.example.todoapplication.data.sync.SyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var taskRepository: TaskRepository
    private lateinit var sync: SyncController
    private lateinit var auth: AuthRepository
    private lateinit var prefs: LocalPrefs

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        taskRepository = mock()
        sync = mock()
        auth = mock()
        prefs = mock()
        whenever(prefs.dailyGoal).thenReturn(MutableStateFlow(3))
        whenever(prefs.daysOff).thenReturn(MutableStateFlow("SAT,SUN"))
        whenever(prefs.digestEnabled).thenReturn(MutableStateFlow(true))
        whenever(prefs.digestTime).thenReturn(MutableStateFlow("07:30"))
        whenever(prefs.weeklyReviewEnabled).thenReturn(MutableStateFlow(true))
        whenever(prefs.allDayReminderTime).thenReturn(MutableStateFlow("08:00"))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(): SettingsViewModel {
        val categories: CategoryRepository = mock()
        whenever(categories.observeAll()).thenReturn(flowOf(CategoryRepository.DEFAULTS))
        return SettingsViewModel(mock<PreferencesRepository>(), categories, prefs, taskRepository, sync, auth, rescheduleNotifications = {})
    }

    @Test
    fun `days off are read from local prefs`() {
        assertEquals(setOf("SAT", "SUN"), viewModel().uiState.value.daysOff)
    }

    @Test
    fun `logout from this device goes straight through when nothing is waiting to sync`() = runTest(dispatcher) {
        whenever(taskRepository.pendingSyncCount()).thenReturn(0)
        val vm = viewModel()
        val events = mutableListOf<SettingsEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }

        vm.requestLogout()
        advanceUntilIdle()

        verify(auth).logout(false)
        assertEquals(SettingsEvent.LoggedOut, events.single())
    }

    @Test
    fun `logout asks for confirmation when unsynced changes cannot be pushed`() = runTest(dispatcher) {
        whenever(taskRepository.pendingSyncCount()).thenReturn(3)
        whenever(sync.syncNow()).thenReturn(SyncResult.NetworkError)
        val vm = viewModel()
        val events = mutableListOf<SettingsEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }

        vm.requestLogout(allDevices = true)
        advanceUntilIdle()

        verifyBlocking(sync) { syncNow() }
        verify(auth, never()).logout(true)
        assertEquals(SettingsEvent.ConfirmLogout(3, allDevices = true), events.single())
    }
}
