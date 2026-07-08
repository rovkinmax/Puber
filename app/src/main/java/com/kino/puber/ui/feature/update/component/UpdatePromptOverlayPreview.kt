package com.kino.puber.ui.feature.update.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices.TV_1080p
import androidx.compose.ui.tooling.preview.Preview
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.data.repository.AppVersion
import com.kino.puber.data.repository.AvailableUpdate
import com.kino.puber.ui.feature.update.model.UpdatePromptViewState
import java.io.File

private val previewUpdate = AvailableUpdate(
    version = AppVersion(1, 5, 0),
    tagName = "v1.5.0",
    title = "Puber 1.5.0",
    releaseNotes = """
        - Added automatic update checks on app launch.
        - Added release notes before installing a new APK.
        - Improved update download progress and checksum validation.
    """.trimIndent(),
    apkAssetName = "puber-prod-release.apk",
    apkDownloadUrl = "https://github.com/rovkinmax/Puber/releases/download/v1.5.0/puber-prod-release.apk",
    apkSizeBytes = 42_000_000,
    checksumDownloadUrl = "https://github.com/rovkinmax/Puber/releases/download/v1.5.0/puber-prod-release.apk.sha256",
    releasePageUrl = "https://github.com/rovkinmax/Puber/releases/tag/v1.5.0",
)

@Preview(name = "Update prompt - available", device = TV_1080p)
@Composable
private fun AvailablePreview() = UpdatePromptPreviewTheme {
    UpdatePromptOverlay(
        state = UpdatePromptViewState.Available(previewUpdate),
        onAction = {},
    )
}

@Preview(name = "Update prompt - downloading", device = TV_1080p)
@Composable
private fun DownloadingPreview() = UpdatePromptPreviewTheme {
    UpdatePromptOverlay(
        state = UpdatePromptViewState.Downloading(
            update = previewUpdate,
            progressPercent = 42,
        ),
        onAction = {},
    )
}

@Preview(name = "Update prompt - permission required", device = TV_1080p)
@Composable
private fun PermissionRequiredPreview() = UpdatePromptPreviewTheme {
    UpdatePromptOverlay(
        state = UpdatePromptViewState.PermissionRequired(
            update = previewUpdate,
            downloadedFile = File("/tmp/puber-prod-release.apk"),
        ),
        onAction = {},
    )
}

@Preview(name = "Update prompt - download error", device = TV_1080p)
@Composable
private fun DownloadErrorPreview() = UpdatePromptPreviewTheme {
    UpdatePromptOverlay(
        state = UpdatePromptViewState.Error(
            update = previewUpdate,
            downloadedFile = null,
            message = "Unable to download the update. Check the connection and try again.",
            canRetryInstall = false,
        ),
        onAction = {},
    )
}

@Preview(name = "Update prompt - install error", device = TV_1080p)
@Composable
private fun InstallErrorPreview() = UpdatePromptPreviewTheme {
    UpdatePromptOverlay(
        state = UpdatePromptViewState.Error(
            update = previewUpdate,
            downloadedFile = File("/tmp/puber-prod-release.apk"),
            message = "Android installer could not be opened. Try again.",
            canRetryInstall = true,
        ),
        onAction = {},
    )
}

@Composable
private fun UpdatePromptPreviewTheme(content: @Composable () -> Unit) {
    PuberTheme(content = content)
}
