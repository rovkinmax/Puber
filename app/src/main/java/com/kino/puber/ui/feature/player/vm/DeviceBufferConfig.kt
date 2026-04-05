package com.kino.puber.ui.feature.player.vm

import android.app.ActivityManager
import android.content.Context

internal object DeviceBufferConfig {

    data class BufferParams(
        val minBufferMs: Int,
        val maxBufferMs: Int,
        val bufferForPlaybackMs: Int = 2_500,
        val bufferForPlaybackAfterRebufferMs: Int = 5_000,
        val backBufferDurationMs: Int = 30_000,
    )

    fun resolve(context: Context): BufferParams {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024 * 1024)

        return when {
            am.isLowRamDevice || totalRamMb < 2048 -> BufferParams(
                minBufferMs = 30_000,
                maxBufferMs = 60_000,
            )
            totalRamMb < 4096 -> BufferParams(
                minBufferMs = 30_000,
                maxBufferMs = 120_000,
            )
            else -> BufferParams(
                minBufferMs = 60_000,
                maxBufferMs = 300_000,
            )
        }
    }
}
