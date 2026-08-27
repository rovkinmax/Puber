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

    fun verify(
        targetPackageName: String,
        backend: BaselineMockBackend,
    ) {
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

    fun clearTargetJournal(targetPackageName: String) {
        val result = broadcast(
            targetPackageName = targetPackageName,
            action = ACTION_CLEAR,
        )
        check(result.contains("result=-1")) {
            "Failed to clear target egress journal: $result"
        }
    }

    private fun broadcast(
        targetPackageName: String,
        action: String,
    ): String {
        return try {
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .executeShellCommand(
                    "am broadcast --receiver-foreground " +
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
    private const val VERIFICATION_POLL_MS = 100L
    private const val VERIFICATION_TIMEOUT_NS = 5_000_000_000L
}
