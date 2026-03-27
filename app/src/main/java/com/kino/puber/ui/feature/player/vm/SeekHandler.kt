package com.kino.puber.ui.feature.player.vm

internal class SeekHandler(
    private val steps: IntArray = intArrayOf(10, 10, 20, 30, 60, 60),
    private val resetTimeoutMs: Long = 1500L,
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
