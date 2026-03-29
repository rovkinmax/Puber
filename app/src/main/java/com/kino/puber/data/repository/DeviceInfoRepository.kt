package com.kino.puber.data.repository

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import com.kino.puber.R
import com.kino.puber.data.api.KinoPubApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal class DeviceInfoRepository(
    private val context: Context,
    private val apiClient: KinoPubApiClient,
) : IDeviceInfoRepository {
    override fun is4kSupported(): Boolean {
        return isDisplay4kSupported() || is4kHardwareDecoderSupported()
    }

    private fun isDisplay4kSupported(): Boolean {
        val display = getPrimaryDisplay(context)
        val modes = display?.supportedModes ?: return false
        return modes.any { mode ->
            mode.physicalWidth >= 3840 && mode.physicalHeight >= 2160
        }
    }

    private fun is4kHardwareDecoderSupported(): Boolean {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
        for (codec in codecList) {
            if (codec.isEncoder) continue
            if (codec.supportedTypes.none { it.equals("video/hevc", ignoreCase = true) }) continue

            val isHardware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                codec.isHardwareAccelerated
            } else {
                !codec.name.contains("omx.google", ignoreCase = true)
            }
            if (!isHardware) continue

            val caps = codec.getCapabilitiesForType("video/hevc")
            val videoCaps = caps.videoCapabilities ?: continue
            if (videoCaps.isSizeSupported(3840, 2160)) {
                return true
            }
        }
        return false
    }

    override fun isHdrSupported(): Boolean {
        return isDisplayHdrSupported() && isHdrCodecSupported()
    }

    private fun isDisplayHdrSupported(): Boolean {
        val display = getPrimaryDisplay(context) ?: return false
        val hdrTypes = display.hdrCapabilities?.supportedHdrTypes ?: return false
        return hdrTypes.isNotEmpty()
    }

    private fun isHdrCodecSupported(): Boolean {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
        for (codec in codecList) {
            if (codec.isEncoder) continue
            if (codec.supportedTypes.none { it.equals("video/hevc", ignoreCase = true) }) continue

            val caps = codec.getCapabilitiesForType("video/hevc")
            for (pl in caps.profileLevels) {
                if (pl.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 ||
                    pl.profile == MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheSt
                ) {
                    return true
                }
            }
        }
        return false
    }

    override fun isSslSupported(): Boolean = true

    override fun isHevcHardwareDecodingSupported(): Boolean {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos

        for (codecInfo in codecList) {
            if (!codecInfo.isEncoder) {
                codecInfo.supportedTypes.forEach { type ->
                    if (type.equals("video/hevc", ignoreCase = true)) {
                        val isHardwareAccelerated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            codecInfo.isHardwareAccelerated
                        } else {
                            !codecInfo.name.contains("omx.google", ignoreCase = true)
                        }

                        if (isHardwareAccelerated) {
                            return true
                        }
                    }
                }
            }
        }

        return false
    }

    override fun getAndroidVersion(): String =
        "Android ${Build.VERSION.RELEASE ?: "Unknown"}"

    override fun getDeviceBrand(): String = "${Build.MANUFACTURER}"

    override fun getDeviceModel(): String = "${Build.MODEL}"

    override fun getAppName(): String = context.getString(R.string.app_name)

    override fun saveDeviceInformation(title: String, hardware: String, software: String): Flow<Unit> = flow {
        val result = apiClient.updateDeviceInfo(title, hardware, software)
        emit(result.getOrThrow())
    }

    private fun getPrimaryDisplay(context: Context): Display? {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return displayManager.getDisplay(Display.DEFAULT_DISPLAY)
    }
}