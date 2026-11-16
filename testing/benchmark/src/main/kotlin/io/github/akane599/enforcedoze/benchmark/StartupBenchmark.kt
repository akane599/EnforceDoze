// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.akane599.enforcedoze.benchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** SPEC 10.5 optimize: cold start to the first frame (`timeToInitialDisplayMs`) under 500 ms on the emulator. */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStart() = rule.measureRepeated(
        packageName = TARGET,
        metrics = listOf(StartupTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD,
    ) {
        pressHome()
        startActivityAndWait()
    }

    private companion object {
        const val TARGET = "io.github.akane599.enforcedoze"
        const val ITERATIONS = 10
    }
}
