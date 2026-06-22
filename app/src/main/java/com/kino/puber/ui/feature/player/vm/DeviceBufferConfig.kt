package com.kino.puber.ui.feature.player.vm

import android.app.ActivityManager
import android.content.Context
import com.kino.puber.ui.feature.player.model.BufferPreset

internal object DeviceBufferConfig {
    private const val MIB = 1024 * 1024
    private const val LOW_MEMORY_HEAP_MB = 128
    private const val MEDIUM_MEMORY_HEAP_MB = 256
    private const val SMALL_BUFFER_BYTES = 16 * MIB
    private const val COMPACT_BUFFER_BYTES = 24 * MIB
    private const val DEFAULT_BUFFER_BYTES = 32 * MIB
    private const val LARGE_BUFFER_BYTES = 64 * MIB

    private const val SHORT_BACK_BUFFER_MS = 5_000
    private const val DEFAULT_BACK_BUFFER_MS = 10_000
    private const val LARGE_BACK_BUFFER_MS = 15_000
    private const val MAX_BACK_BUFFER_MS = 30_000

    data class BufferParams(
        val minBufferMs: Int,
        val maxBufferMs: Int,
        val targetBufferBytes: Int,
        val bufferForPlaybackMs: Int = 2_500,
        val bufferForPlaybackAfterRebufferMs: Int = 5_000,
        val backBufferDurationMs: Int = 0,
        val prioritizeTimeOverSize: Boolean = false,
    )

    fun resolve(context: Context, preset: BufferPreset = BufferPreset.AUTO): BufferParams {
        return when (preset) {
            BufferPreset.AUTO -> resolveAuto(context)
            BufferPreset.SMALL -> BufferParams(
                minBufferMs = 30_000,
                maxBufferMs = 60_000,
                targetBufferBytes = SMALL_BUFFER_BYTES,
                backBufferDurationMs = 0,
                prioritizeTimeOverSize = false,
            )
            BufferPreset.MEDIUM -> BufferParams(
                minBufferMs = 30_000,
                maxBufferMs = 120_000,
                targetBufferBytes = DEFAULT_BUFFER_BYTES,
                backBufferDurationMs = SHORT_BACK_BUFFER_MS,
            )
            BufferPreset.LARGE -> BufferParams(
                minBufferMs = 60_000,
                maxBufferMs = 180_000,
                targetBufferBytes = LARGE_BUFFER_BYTES,
                backBufferDurationMs = LARGE_BACK_BUFFER_MS,
            )
            BufferPreset.MAX -> BufferParams(
                minBufferMs = 60_000,
                maxBufferMs = 300_000,
                targetBufferBytes = maxBufferBytesForDevice(context),
                backBufferDurationMs = MAX_BACK_BUFFER_MS,
            )
        }
    }

    private fun maxBufferBytesForDevice(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val heapLimitMb = am.largeMemoryClass

        return when {
            am.isLowRamDevice || heapLimitMb <= LOW_MEMORY_HEAP_MB -> SMALL_BUFFER_BYTES
            heapLimitMb <= MEDIUM_MEMORY_HEAP_MB -> COMPACT_BUFFER_BYTES
            else -> DEFAULT_BUFFER_BYTES
        }
    }

    private fun resolveAuto(context: Context): BufferParams {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val heapLimitMb = am.largeMemoryClass

        return when {
            am.isLowRamDevice || heapLimitMb <= LOW_MEMORY_HEAP_MB -> BufferParams(
                minBufferMs = 30_000,
                maxBufferMs = 60_000,
                targetBufferBytes = SMALL_BUFFER_BYTES,
                backBufferDurationMs = 0,
                prioritizeTimeOverSize = false,
            )
            heapLimitMb <= MEDIUM_MEMORY_HEAP_MB -> BufferParams(
                minBufferMs = 30_000,
                maxBufferMs = 90_000,
                targetBufferBytes = COMPACT_BUFFER_BYTES,
                backBufferDurationMs = SHORT_BACK_BUFFER_MS,
            )
            else -> BufferParams(
                minBufferMs = 45_000,
                maxBufferMs = 120_000,
                targetBufferBytes = DEFAULT_BUFFER_BYTES,
                backBufferDurationMs = DEFAULT_BACK_BUFFER_MS,
            )
        }
    }
}
