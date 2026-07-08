package com.kino.puber.data.repository

internal data class AvailableUpdate(
    val version: AppVersion,
    val tagName: String,
    val title: String,
    val releaseNotes: String?,
    val apkAssetName: String,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long?,
    val checksumDownloadUrl: String?,
    val releasePageUrl: String?,
)
