package com.kino.puber.ui.feature.player.vm

import com.kino.puber.core.logger.log

internal const val PROGRESS_SAVE_FAILURE_DIAGNOSTIC = "Playback progress save failed"

/**
 * This boundary deliberately accepts no throwable or media parameters so progress failures
 * cannot add account viewing identity to application logs.
 */
internal object PlayerProgressDiagnostics {
    fun reportSaveFailure() {
        log(PROGRESS_SAVE_FAILURE_DIAGNOSTIC)
    }
}
