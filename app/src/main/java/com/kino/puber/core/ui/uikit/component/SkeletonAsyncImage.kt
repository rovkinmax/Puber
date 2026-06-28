package com.kino.puber.core.ui.uikit.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.tv.material3.MaterialTheme
import coil3.compose.AsyncImage
import com.kino.puber.core.ui.uikit.component.modifier.placeholder

@Composable
fun SkeletonAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    onError: () -> Unit = {},
) {
    var isLoading by remember(model) { mutableStateOf(model != null) }
    var isFailed by remember(model) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32F)),
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = contentScale,
                alignment = alignment,
                onLoading = {
                    isLoading = true
                    isFailed = false
                },
                onSuccess = {
                    isLoading = false
                    isFailed = false
                },
                onError = {
                    isLoading = false
                    isFailed = true
                    onError()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (isFailed) 0F else 1F),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .placeholder(visible = isLoading),
        )
    }
}
