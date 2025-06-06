package com.kino.puber.ui.feature.main.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import com.kino.puber.ui.feature.main.vm.MainVM
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun MainScreenComponent() {

    val vm = koinViewModel<MainVM>()
    val viewState by vm.collectViewState()
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text("Main sc")
    }

}