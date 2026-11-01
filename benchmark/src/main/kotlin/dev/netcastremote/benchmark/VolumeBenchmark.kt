package dev.netcastremote.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VolumeBenchmark {
    @get:Rule
    val benchmark = MacrobenchmarkRule()

    @Test
    fun volumeKeyToPost() = benchmark.measureRepeated(
        packageName = "dev.netcastremote",
        metrics = listOf(FrameTimingMetric()),
        iterations = 3,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Partial(),
    ) {
        pressHome()
        startActivityAndWait()
        val up = device.findObject(By.desc("Volume up"))
        if (up != null) {
            up.click()
            device.wait(Until.hasObject(By.desc("Volume up")), 1_000)
        }
    }
}
