package com.kino.puber.ui.feature.auth.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import com.kino.puber.ui.feature.auth.model.AuthViewState
import com.kino.puber.ui.feature.auth.vm.MainViewmodel
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun AuthComponent() {
    val vm = koinViewModel<MainViewmodel>()
    val viewState by vm.collectViewState()
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        when (val state = viewState) {
            is AuthViewState.Content -> Text(state.code)
            AuthViewState.Loading -> CircularProgressIndicator()
        }
    }
}