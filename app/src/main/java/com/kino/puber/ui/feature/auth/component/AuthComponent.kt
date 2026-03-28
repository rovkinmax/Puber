package com.kino.puber.ui.feature.auth.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import com.kino.puber.ui.feature.auth.model.AuthViewState
import com.kino.puber.ui.feature.auth.vm.AuthVM
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun AuthScreenComponent() {
    val vm = koinViewModel<AuthVM>()
    val viewState by vm.collectViewState()
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        when (val state = viewState) {
            is AuthViewState.Content -> CodeInfo(
                code = state.code,
                url = state.url,
                timeLeft = state.timeLeft,
            )
            AuthViewState.Loading -> FullScreenProgressIndicator()
        }
    }
}

@Composable
internal fun CodeInfo(code: String, url: String, timeLeft: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(code, fontSize = 32.sp)
        Spacer(modifier = Modifier.height(20.dp))
        Image(
            painter = painterResource(R.drawable.qr_device),
            contentDescription = null,
            modifier = Modifier.size(200.dp),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(url)
        Spacer(modifier = Modifier.height(16.dp))
        Text(timeLeft)
    }
}

@Preview
@Composable
internal fun CodeInfoPreview() {
    CodeInfo(code = "123456", url = "https://www.example.com", timeLeft = "02:00")
}
