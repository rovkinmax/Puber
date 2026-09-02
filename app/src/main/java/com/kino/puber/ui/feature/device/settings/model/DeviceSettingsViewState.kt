package com.kino.puber.ui.feature.device.settings.model

import androidx.compose.runtime.Immutable
import com.kino.puber.core.model.BookmarkMode
import com.kino.puber.core.model.NavigationMode
import com.kino.puber.core.ui.uikit.model.ApiDomainDialogState
import com.kino.puber.domain.interactor.device.DeviceSettingType

@Immutable
internal data class DeviceSettingsViewState(
    val state: DeviceSettingsState = DeviceSettingsState.Loading,
    val apiDomain: ApiDomainDialogState,
    val isApiDomainDialogOpen: Boolean = false,
)

@Immutable
internal sealed interface DeviceSettingsState {
    object Loading : DeviceSettingsState
    data class Error(val error: String) : DeviceSettingsState
    @Immutable
    data class Success(
        val settings: DeviceSettingsListUi,
        val device: DeviceUi,
        val expandedType: DeviceSettingType? = null,
        val savingOptionId: Int? = null,
        val savingToggleType: DeviceSettingType? = null,
        val skipIntroEnabled: Boolean = true,
        val skipRecapEnabled: Boolean = true,
        val skipCreditsEnabled: Boolean = true,
        val debugOverlayEnabled: Boolean = false,
        val preferSurroundAudio: Boolean = false,
        val watchedIndicatorsEnabled: Boolean = true,
        val discardEmbeddedArtworkMetadata: Boolean = true,
        val hagcPlaybackEnabled: Boolean = false,
        val navigationMode: NavigationMode = NavigationMode.TopTabs,
        val bookmarkMode: BookmarkMode = BookmarkMode.Simple,
        val showCartoonsTab: Boolean = false,
        val showAnimeTab: Boolean = false,
        val showAnime: Boolean = true,
        val autoUpdateCheckEnabled: Boolean = true,
    ) : DeviceSettingsState
}
