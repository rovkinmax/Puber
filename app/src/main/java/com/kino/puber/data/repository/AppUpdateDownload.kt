package com.kino.puber.data.repository

import android.content.Context
import android.os.Environment
import com.kino.puber.data.api.KinoPubApiClient
import java.io.File
import java.security.MessageDigest

internal class AppUpdateDownloader(
    context: Context,
    private val apiClient: KinoPubApiClient,
) {

    private val appContext = context.applicationContext

    suspend fun download(
        update: AvailableUpdate,
        onProgress: (AppUpdateDownload.Progress) -> Unit,
    ): AppUpdateDownload {
        val updatesDirectory = getUpdatesDirectory()
            ?: return AppUpdateDownload.Error.StorageUnavailable
        val targetFile = File(updatesDirectory, sanitizeFileName(update.apkAssetName))

        val downloadedFile = apiClient.downloadUpdateAsset(
            url = update.apkDownloadUrl,
            targetFile = targetFile,
            onProgress = { percent -> onProgress(AppUpdateDownload.Progress(percent)) },
        ).getOrElse { error ->
            return AppUpdateDownload.Error.DownloadFailed(error)
        }

        val checksumUrl = update.checksumDownloadUrl
            ?: return AppUpdateDownload.Completed(downloadedFile)

        val checksumContent = apiClient.getUpdateChecksum(checksumUrl).getOrElse { error ->
            downloadedFile.delete()
            return AppUpdateDownload.Error.ChecksumDownloadFailed(error)
        }

        return when (val verification = AppUpdateChecksum.verifySha256(downloadedFile, checksumContent)) {
            AppUpdateChecksumVerification.Match -> AppUpdateDownload.Completed(downloadedFile)
            AppUpdateChecksumVerification.InvalidChecksum -> {
                downloadedFile.delete()
                AppUpdateDownload.Error.InvalidChecksum
            }
            is AppUpdateChecksumVerification.Mismatch -> {
                downloadedFile.delete()
                AppUpdateDownload.Error.ChecksumMismatch(
                    expected = verification.expected,
                    actual = verification.actual,
                )
            }
        }
    }

    private fun getUpdatesDirectory(): File? {
        val downloadsDirectory = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: return null
        return File(downloadsDirectory, UPDATES_DIRECTORY_NAME).apply {
            mkdirs()
        }
    }

    private fun sanitizeFileName(raw: String): String {
        val sanitized = raw.substringAfterLast('/').map { char ->
            if (char.isLetterOrDigit() || char == '.' || char == '_' || char == '-') {
                char
            } else {
                '_'
            }
        }.joinToString(separator = "")

        return sanitized.takeIf { it.endsWith(APK_EXTENSION, ignoreCase = true) }
            ?: DEFAULT_APK_NAME
    }

    private companion object {
        const val UPDATES_DIRECTORY_NAME = "updates"
        const val APK_EXTENSION = ".apk"
        const val DEFAULT_APK_NAME = "update.apk"
    }
}

internal sealed interface AppUpdateDownload {
    data class Progress(val percent: Int) : AppUpdateDownload
    data class Completed(val file: File) : AppUpdateDownload

    sealed interface Error : AppUpdateDownload {
        data object StorageUnavailable : Error
        data class DownloadFailed(val cause: Throwable) : Error
        data class ChecksumDownloadFailed(val cause: Throwable) : Error
        data object InvalidChecksum : Error
        data class ChecksumMismatch(val expected: String, val actual: String) : Error
    }
}

internal object AppUpdateChecksum {

    fun verifySha256(file: File, checksumContent: String): AppUpdateChecksumVerification {
        val expected = parseSha256(checksumContent)
            ?: return AppUpdateChecksumVerification.InvalidChecksum
        val actual = calculateSha256(file)

        return if (expected.equals(actual, ignoreCase = true)) {
            AppUpdateChecksumVerification.Match
        } else {
            AppUpdateChecksumVerification.Mismatch(expected = expected, actual = actual)
        }
    }

    fun parseSha256(checksumContent: String): String? {
        return checksumContent
            .lineSequence()
            .map { line -> line.trim().takeWhile { char -> !char.isWhitespace() }.lowercase() }
            .firstOrNull(::isSha256)
    }

    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(HASH_BUFFER_SIZE)

        file.inputStream().buffered().use { input ->
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) {
                    break
                }
                digest.update(buffer, 0, bytesRead)
            }
        }

        return digest.digest().toHexString()
    }

    private fun isSha256(value: String): Boolean {
        return value.length == SHA256_HEX_LENGTH && value.all { char ->
            char in '0'..'9' || char in 'a'..'f'
        }
    }

    private fun ByteArray.toHexString(): String {
        return buildString(size * 2) {
            this@toHexString.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(HEX_CHARS[value ushr 4])
                append(HEX_CHARS[value and 0x0f])
            }
        }
    }

    private const val HASH_BUFFER_SIZE = 8 * 1024
    private const val SHA256_HEX_LENGTH = 64
    private val HEX_CHARS = "0123456789abcdef".toCharArray()
}

internal sealed interface AppUpdateChecksumVerification {
    data object Match : AppUpdateChecksumVerification
    data object InvalidChecksum : AppUpdateChecksumVerification
    data class Mismatch(val expected: String, val actual: String) : AppUpdateChecksumVerification
}
