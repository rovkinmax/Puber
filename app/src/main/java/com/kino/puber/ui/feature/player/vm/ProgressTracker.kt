package com.kino.puber.ui.feature.player.vm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class ProgressTracker {
    private var syncJob: Job? = null
    var isMarkedWatched = false
        private set

    fun startSync(
        scope: CoroutineScope,
        intervalMs: Long,
        isPlaying: () -> Boolean,
        onSave: () -> Unit,
    ) {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) {
                delay(intervalMs)
                if (isPlaying()) {
                    onSave()
                }
            }
        }
    }

    fun stopSync() {
        syncJob?.cancel()
    }

    fun markAsWatched() {
        isMarkedWatched = true
    }

    fun reset() {
        isMarkedWatched = false
    }
}
