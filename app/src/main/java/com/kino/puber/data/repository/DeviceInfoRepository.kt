package com.kino.puber.data.repository

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import com.kino.puber.data.api.KinoPubApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.net.ssl.SSLSocketFactory

internal class DeviceInfoRepository(
    private val context: Context,
    private val apiClient: KinoPubApiClient,
) : IDeviceInfoRepository {
    override fun is4kSupported(): Boolean {
        val display = getPrimaryDisplay(context)
        val modes = display?.supportedModes ?: return false

        return modes.any { mode ->
            mode.physicalWidth >= 3840 && mode.physicalHeight >= 2160
        }
    }

    override fun isHdrSupported(): Boolean {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        val codecs = codecList.codecInfos

        for (codec in codecs) {
            if (!codec.isEncoder) {
                val types = codec.supportedTypes
                for (type in types) {
                    val caps = codec.getCapabilitiesForType(type)
                    for (pl in caps.profileLevels) {
                        if (pl.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 ||
                            pl.profile == MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheSt
                        ) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    override fun isSslSupported(): Boolean {
        return try {
            val factory = SSLSocketFactory.getDefault() as? SSLSocketFactory
            val supported = factory?.supportedCipherSuites
            supported != null && supported.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    override fun isHevcHardwareDecodingSupported(): Boolean {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos

        for (codecInfo in codecList) {
            if (!codecInfo.isEncoder) {
                codecInfo.supportedTypes.forEach { type ->
                    if (type.equals("video/hevc", ignoreCase = true)) {
                        val capabilities = codecInfo.getCapabilitiesForType(type)
                        val isHardwareAccelerated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            codecInfo.isHardwareAccelerated
                        } else {
                            // На API < 29 такой информации нет, считаем что true если не "OMX.google"
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

    override fun saveDeviceInformation(title: String, hardware: String, software: String): Flow<Unit> = flow {
        val result = apiClient.updateDeviceInfo(title, hardware, software)
        emit(result.getOrThrow())
    }

    private fun getPrimaryDisplay(context: Context): Display? {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return displayManager.getDisplay(Display.DEFAULT_DISPLAY)
    }
}