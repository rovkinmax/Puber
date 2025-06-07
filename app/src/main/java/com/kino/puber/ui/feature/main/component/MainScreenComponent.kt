package com.kino.puber.ui.feature.main.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import com.kino.puber.core.ui.navigation.component.OsomeCurrentTab
import com.kino.puber.core.ui.navigation.component.TabComponent
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.main.model.MainViewState
import com.kino.puber.ui.feature.main.vm.MainVM
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun MainScreenComponent() {
    val vm = koinViewModel<MainVM>()
    val state by vm.collectViewState()
    val onAction: (UIAction) -> Unit = remember { vm::onAction }
    MainScreenContent(state, onAction = onAction)
}


@Composable
private fun MainScreenContent(state: MainViewState, onAction: (UIAction) -> Unit) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    NavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            Column(
                Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp)
                    .selectableGroup()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start,
            ) {
                state.tabs.forEachIndexed { index, tab ->

                    NavigationDrawerItem(
                        modifier = Modifier.height(40.dp),
                        selected = tab.isSelected,
                        onClick = {
                            onAction(CommonAction.ItemSelected(tab))
                            drawerState.setValue(DrawerValue.Closed)
                        },
                        leadingContent = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                            )
                        },
                        trailingContent = if (tab.badge > 0) {
                            {
                                Badge {
                                    Text(
                                        text = tab.badge.toString(),
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                    )
                                }
                            }
                        } else {
                            null
                        }

                    ) {
                        Text(text = tab.label)
                    }
                }
            }
        }
    ) {
        TabComponent {
            Box(Modifier.padding()) {
                OsomeCurrentTab()
            }
        }
    }
}