package com.kino.puber.ui.feature.auth.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.kino.puber.ui.feature.auth.model.AuthViewState
import com.kino.puber.ui.feature.auth.vm.AuthVM
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.koin.androidx.compose.koinViewModel
import androidx.compose.ui.res.painterResource
import com.kino.puber.R
import java.util.Locale

@Composable
internal fun AuthScreenComponent() {
    val vm = koinViewModel<AuthVM>()
    val viewState by vm.collectViewState()
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        when (val state = viewState) {
            is AuthViewState.Content -> CodeInfo(state.code, state.url, state.expireTimeSeconds)
            AuthViewState.Loading -> CircularProgressIndicator()
        }
    }
}

@Composable
internal fun CodeInfo(code: String, url: String, expireTimeSeconds: Int) {
    val timeLeft = remember { mutableStateOf("") }

    val timer = remember {
        flow {
            for (i in expireTimeSeconds downTo 0) {
                emit(i)
                delay(1000)
            }
        }.map { timeLeft ->
            val minutes = timeLeft / 60
            val seconds = timeLeft % 60
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    LaunchedEffect(expireTimeSeconds) {
        timer.collect {
            timeLeft.value = it
        }
    }
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
        Text(timeLeft.value)
    }
}

@Preview
@Composable
internal fun CodeInfoPreview() {
    CodeInfo(code = "123456", url = "https://www.example.com", expireTimeSeconds = 120)
}
