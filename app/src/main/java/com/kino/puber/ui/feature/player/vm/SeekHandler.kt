package com.kino.puber.ui.feature.player.vm

private const val SHORT_SEEK_STEP_SECONDS = 10
private const val MEDIUM_SEEK_STEP_SECONDS = 20
private const val LONG_SEEK_STEP_SECONDS = 30
private const val MAX_SEEK_STEP_SECONDS = 60
private const val DEFAULT_SEEK_RESET_TIMEOUT_MS = 1_500L
private val DEFAULT_SEEK_STEPS_SECONDS = intArrayOf(
    SHORT_SEEK_STEP_SECONDS,
    SHORT_SEEK_STEP_SECONDS,
    MEDIUM_SEEK_STEP_SECONDS,
    LONG_SEEK_STEP_SECONDS,
    MAX_SEEK_STEP_SECONDS,
    MAX_SEEK_STEP_SECONDS,
)

internal class SeekHandler(
    private val steps: IntArray = DEFAULT_SEEK_STEPS_SECONDS,
    private val resetTimeoutMs: Long = DEFAULT_SEEK_RESET_TIMEOUT_MS,
) {
    private var lastSeekTime = 0L
    private var stepIndex = 0

    fun nextStep(): Int {
        val now = System.currentTimeMillis()
        if (now - lastSeekTime > resetTimeoutMs) {
            stepIndex = 0
        } else if (stepIndex < steps.size - 1) {
            stepIndex++
        }
        lastSeekTime = now
        return steps[stepIndex]
    }

    fun reset() {
        stepIndex = 0
        lastSeekTime = 0L
    }
}
