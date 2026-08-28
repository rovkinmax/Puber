package com.kino.puber.baselineprofile

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice

/**
 * Coordinates benchmark-process fixtures with the target application's
 * instrumentation-only network journal.
 *
 * All calls are deliberately made outside measured/profiled blocks. The
 * target package may be force-stopped or reinstalled by Macrobenchmark while
 * this control plane and its MockWebServer remain alive.
 */
internal object BaselineProfileTestControl {

    fun createBackend(arguments: Bundle): BaselineMockBackend {
        val port = arguments.getString(ARG_MOCK_PORT)
            ?.toIntOrNull()
            ?: error("$ARG_MOCK_PORT instrumentation argument is required")
        return BaselineMockBackend(
            port = port,
            fixtures = BaselineFixtures.from(
                InstrumentationRegistry.getInstrumentation().context,
            ),
        )
    }

    fun prepare(
        targetPackageName: String,
        backend: BaselineMockBackend,
        scenario: BaselineScenario,
    ) {
        backend.reset(scenario)
        clearTargetJournal(targetPackageName)
        backend.awaitReady()
    }

    fun awaitStartupReady(backend: BaselineMockBackend) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        backend.awaitStartupHomeRequest()
        device.waitForIdle(UI_IDLE_TIMEOUT_MS)
    }

    fun verify(
        targetPackageName: String,
        backend: BaselineMockBackend,
    ) {
        forceStopTarget(targetPackageName)
        awaitQuiescence(backend)
        val deadline = System.nanoTime() + VERIFICATION_TIMEOUT_NS
        var lastVerification = backend.verify()
        while (System.nanoTime() < deadline) {
            val targetJournal = broadcast(
                targetPackageName = targetPackageName,
                action = ACTION_VERIFY,
            )
            check(targetJournal.contains("result=-1")) {
                "Target egress journal is not empty: $targetJournal"
            }

            lastVerification = backend.verify()
            if (lastVerification.unknownRequests.isNotEmpty()) {
                break
            }
            if (lastVerification.isSuccessful) return
            Thread.sleep(VERIFICATION_POLL_MS)
        }
        check(lastVerification.isSuccessful) {
            buildString {
                append("Baseline mock verification failed for ")
                append(lastVerification.scenario)
                append(": unknown=")
                append(lastVerification.unknownRequests)
                append(", matched=")
                append(lastVerification.matchedRoutes)
                append(", missing=")
                append(lastVerification.missingRequiredRoutes)
            }
        }
    }

    private fun awaitQuiescence(backend: BaselineMockBackend) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        var previous = backend.requestJournal
        var stableSince = System.nanoTime()
        val deadline = System.nanoTime() + QUIESCENCE_TIMEOUT_NS
        while (System.nanoTime() < deadline) {
            device.waitForIdle(QUIESCENCE_POLL_MS)
            val current = backend.requestJournal
            val now = System.nanoTime()
            if (current != previous) {
                previous = current
                stableSince = now
            } else if (now - stableSince >= QUIESCENCE_STABLE_NS) {
                return
            }
        }
        error("Target requests did not quiesce within ${QUIESCENCE_TIMEOUT_MS}ms")
    }

    fun clearTargetJournal(targetPackageName: String) {
        val result = broadcast(
            targetPackageName = targetPackageName,
            action = ACTION_CLEAR,
        )
        check(result.contains("result=-1")) {
            "Failed to clear target egress journal: $result"
        }
    }

    private fun forceStopTarget(targetPackageName: String) {
        try {
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .executeShellCommand("am force-stop $targetPackageName")
        } catch (error: java.io.IOException) {
            throw IllegalStateException("Failed to stop target before verification", error)
        }
    }

    private fun broadcast(
        targetPackageName: String,
        action: String,
    ): String {
        return try {
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .executeShellCommand(
                    "am broadcast --receiver-foreground --include-stopped-packages " +
                        "-n $targetPackageName/$CONTROL_RECEIVER " +
                        "-a $action",
                )
        } catch (error: java.io.IOException) {
            throw IllegalStateException("Failed to call target control receiver", error)
        }
    }

    private const val ARG_MOCK_PORT = "baselineMockPort"
    private const val CONTROL_RECEIVER = "com.kino.puber.profile.BaselineProfileControlReceiver"
    private const val ACTION_CLEAR = "com.kino.puber.profile.CLEAR"
    private const val ACTION_VERIFY = "com.kino.puber.profile.VERIFY"
    private const val UI_IDLE_TIMEOUT_MS = 1_000L
    private const val QUIESCENCE_POLL_MS = 100L
    private const val QUIESCENCE_STABLE_MS = 500L
    private const val QUIESCENCE_TIMEOUT_MS = 5_000L
    private const val QUIESCENCE_STABLE_NS = QUIESCENCE_STABLE_MS * 1_000_000L
    private const val QUIESCENCE_TIMEOUT_NS = QUIESCENCE_TIMEOUT_MS * 1_000_000L
    private const val VERIFICATION_POLL_MS = 100L
    private const val VERIFICATION_TIMEOUT_NS = 5_000_000_000L
}

internal object BaselineProfileRuleFilter {

    fun include(rule: String): Boolean =
        INSTRUMENTATION_PACKAGE !in rule

    private const val INSTRUMENTATION_PACKAGE = "com/kino/puber/profile/"
}
