package com.example.todoapplication.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE = "com.example.todoapplication"

/**
 * Đo thời gian khởi động lạnh và độ mượt khi cuộn, so sánh có / không có Baseline Profile.
 * Kết quả (ms, phân vị P50/P90) hiện trong Android Studio và trong build/outputs/connected_android_test_additional_output.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartupWithoutProfile() = startup(CompilationMode.None())

    @Test
    fun coldStartupWithBaselineProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        iterations = 5,
        startupMode = StartupMode.COLD
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun scrollTaskList() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 3,
        setupBlock = { pressHome(); startActivityAndWait() }
    ) {
        device.findObject(By.desc("Việc làm"))?.click()
        device.wait(Until.hasObject(By.scrollable(true)), 3_000)
        device.findObject(By.scrollable(true))?.let { list ->
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(Direction.DOWN)
            list.fling(Direction.UP)
        }
    }
}

/**
 * Sinh Baseline Profile: chạy các luồng chính (mở app, chuyển tab) để ghi lại lớp / hàm được dùng.
 * Kết quả chép vào app/src/main/baseline-prof.txt (ProfileInstaller cài lúc cài app → mở nhanh hơn từ lần đầu).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE) {
        pressHome()
        startActivityAndWait()
        listOf("Việc làm", "Lịch", "Tôi", "Hôm nay").forEach { tab ->
            device.findObject(By.desc(tab))?.click()
            device.waitForIdle()
        }
    }
}
