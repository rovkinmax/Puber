package com.kino.puber.ui.feature.player.vm

import android.app.ActivityManager
import android.content.Context

internal object DeviceBufferConfig {

    data class BufferParams(
        val minBufferMs: Int,
        val maxBufferMs: Int,
        val targetBufferBytes: Int,
        val bufferForPlaybackMs: Int = 2_500,
        val bufferForPlaybackAfterRebufferMs: Int = 5_000,
        val backBufferDurationMs: Int = 30_000,
    )

    fun resolve(context: Context): BufferParams {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val heapLimitMb = am.memoryClass

        return when {
            am.isLowRamDevice || heapLimitMb <= 128 -> BufferParams(
                minBufferMs = 30_000,
                maxBufferMs = 60_000,
                targetBufferBytes = 16 * 1024 * 1024,
                backBufferDurationMs = 15_000,
            )
            heapLimitMb <= 256 -> BufferParams(
                minBufferMs = 30_000,
                maxBufferMs = 120_000,
                targetBufferBytes = 32 * 1024 * 1024,
            )
            else -> BufferParams(
                minBufferMs = 60_000,
                maxBufferMs = 180_000,
                targetBufferBytes = 64 * 1024 * 1024,
            )
        }
    }
}
