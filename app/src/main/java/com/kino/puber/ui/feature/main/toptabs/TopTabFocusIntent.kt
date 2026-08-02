package com.kino.puber.ui.feature.main.toptabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import com.kino.puber.core.ui.uikit.component.TvDialogFocusRestorer
import com.kino.puber.ui.feature.main.model.MainTab
import com.kino.puber.ui.feature.main.model.MainViewState
import com.kino.puber.ui.feature.main.model.TabType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class TopTabFocusIntent(
    val focusedTab: TabType?,
    val token: Int = 0,
) {
    fun focus(tab: TabType): TopTabFocusIntent {
        return copy(
            focusedTab = tab,
            token = token + 1,
        )
    }

    fun reconcile(
        visibleTabs: List<TabType>,
        selectedTab: TabType,
    ): TopTabFocusIntent {
        if (focusedTab in visibleTabs) return this

        return copy(
            focusedTab = selectedTab.takeIf(visibleTabs::contains)
                ?: visibleTabs.firstOrNull(),
            token = token + 1,
        )
    }

    fun isLatest(token: Int): Boolean = this.token == token
}

internal class TopTabFocusState(
    val focusedTabType: TabType?,
    val focusIntentToken: Int,
    val selectedIndex: Int,
    val focusRequesters: Map<TabType, FocusRequester>,
    val dialogFocusRestorer: TvDialogFocusRestorer,
    val onTabFocused: (TabType) -> Unit,
)

@Composable
internal fun rememberTopTabFocusState(
    state: MainViewState,
    tabRowFocus: FocusRequester,
): TopTabFocusState {
    val coroutineScope = rememberCoroutineScope()
    val focusRequesters = remember { mutableMapOf<TabType, FocusRequester>() }
        .also { requesters ->
            state.tabs.forEach { tab ->
                requesters.getOrPut(tab.type) { FocusRequester() }
            }
        }
    val selectedTabType = state.selectedTab
        .takeIf { selected -> state.tabs.any { it.type == selected } }
        ?: state.tabs.firstOrNull()?.type
    val focusedTabType = rememberSaveable { mutableStateOf(selectedTabType) }
    val focusIntentToken = rememberSaveable { mutableIntStateOf(0) }
    val currentFocusedTabType by rememberUpdatedState(focusedTabType.value)
    val currentFocusRequesters by rememberUpdatedState(focusRequesters)
    val dialogFocusRestorer = remember(tabRowFocus, coroutineScope) {
        TvDialogFocusRestorer(
            onDialogOpening = { tabRowFocus.saveFocusedChild() },
            onDialogClosed = {
                coroutineScope.launch {
                    delay(TOP_TAB_CHILD_FOCUS_DELAY_MS)
                    currentFocusRequesters[currentFocusedTabType]?.requestFocus()
                        ?: tabRowFocus.requestFocus()
                }
            },
        )
    }
    ReconcileTopTabFocusEffect(
        tabs = state.tabs,
        selectedTab = state.selectedTab,
        focusedTabType = focusedTabType,
        focusIntentToken = focusIntentToken,
    )
    val selectedIndex = state.tabs.indexOfFirst { it.type == focusedTabType.value }
        .takeIf { it >= 0 }
        ?: state.tabs.indexOfFirst { it.type == selectedTabType }.coerceAtLeast(0)
    return TopTabFocusState(
        focusedTabType = focusedTabType.value,
        focusIntentToken = focusIntentToken.intValue,
        selectedIndex = selectedIndex,
        focusRequesters = focusRequesters,
        dialogFocusRestorer = dialogFocusRestorer,
        onTabFocused = { tabType ->
            val nextIntent = TopTabFocusIntent(
                focusedTab = focusedTabType.value,
                token = focusIntentToken.intValue,
            ).focus(tabType)
            focusedTabType.value = nextIntent.focusedTab
            focusIntentToken.intValue = nextIntent.token
        },
    )
}

@Composable
private fun ReconcileTopTabFocusEffect(
    tabs: List<MainTab>,
    selectedTab: TabType,
    focusedTabType: androidx.compose.runtime.MutableState<TabType?>,
    focusIntentToken: androidx.compose.runtime.MutableIntState,
) {
    LaunchedEffect(tabs.map(MainTab::type), selectedTab) {
        val currentIntent = TopTabFocusIntent(
            focusedTab = focusedTabType.value,
            token = focusIntentToken.intValue,
        )
        val nextIntent = currentIntent.reconcile(
            visibleTabs = tabs.map(MainTab::type),
            selectedTab = selectedTab,
        )
        if (nextIntent != currentIntent) {
            focusedTabType.value = nextIntent.focusedTab
            focusIntentToken.intValue = nextIntent.token
        }
    }
}

private const val TOP_TAB_CHILD_FOCUS_DELAY_MS = 16L
