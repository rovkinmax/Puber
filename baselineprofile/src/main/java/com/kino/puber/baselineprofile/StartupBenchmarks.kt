package com.kino.puber.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures cold startup time with and without Baseline Profile.
 * Run after generating the profile to quantify improvement in the dedicated
 * instrumentation application sandbox.
 *
 * Run: ./gradlew :baselineprofile:connectedInstrumentationBenchmarkReleaseAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.kino.puber.baselineprofile.StartupBenchmarks
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class StartupBenchmarks {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val packageName: String
        get() = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: error("targetAppId not found in instrumentation args")

    @Test
    fun startupWithoutProfile() = startup(CompilationMode.None())

    @Test
    fun startupWithProfile() = startup(
        CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.Require,
        ),
    )

    private fun startup(compilationMode: CompilationMode) {
        val backend = BaselineProfileTestControl.createBackend(
            InstrumentationRegistry.getArguments(),
        )

        var journeyPrepared = false
        var benchmarkFailure: Throwable? = null
        try {
            backend.start()
            backend.awaitReady()

            benchmarkRule.measureRepeated(
                packageName = packageName,
                metrics = listOf(StartupTimingMetric()),
                iterations = 5,
                startupMode = StartupMode.COLD,
                compilationMode = compilationMode,
                setupBlock = {
                    if (journeyPrepared) {
                        backend.awaitStartupHomeRequest()
                        BaselineProfileTestControl.verify(
                            targetPackageName = packageName,
                            backend = backend,
                        )
                    }
                    BaselineProfileTestControl.prepare(
                        targetPackageName = packageName,
                        backend = backend,
                        scenario = BaselineScenario.Startup,
                    )
                    journeyPrepared = true
                },
            ) {
                pressHome()
                startActivityAndWait()
            }
        } catch (failure: Throwable) {
            benchmarkFailure = failure
            throw failure
        } finally {
            val verificationFailure = if (journeyPrepared) {
                runCatching {
                    backend.awaitStartupHomeRequest()
                    BaselineProfileTestControl.verify(
                        targetPackageName = packageName,
                        backend = backend,
                    )
                }.exceptionOrNull()
            } else {
                null
            }
            val cleanupFailure = runCatching(backend::close).exceptionOrNull()
            val secondaryFailure = verificationFailure?.also { failure ->
                cleanupFailure?.let(failure::addSuppressed)
            } ?: cleanupFailure

            if (secondaryFailure != null) {
                benchmarkFailure?.addSuppressed(secondaryFailure)
                    ?: throw secondaryFailure
            }
        }
    }
}
