package com.kino.puber.core.ui.uikit.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.modifier.placeholder
import com.kino.puber.core.ui.uikit.theme.PuberTheme

@Immutable
sealed class RatingUIState(open val value: String) {
    open val isLoading: Boolean = false

    data class KP(
        override val value: String,
        override val isLoading: Boolean = false,
    ) : RatingUIState(value)

    data class IMDB(
        override val value: String,
        override val isLoading: Boolean = false,
    ) : RatingUIState(value)

    data class PUB(
        override val value: String,
        override val isLoading: Boolean = false,
    ) : RatingUIState(value)
}

@Suppress("unused")
@Composable
fun Rating(
    state: RatingUIState,
    modifier: Modifier = Modifier,
) {
    val iconResource = when (state) {
        is RatingUIState.KP -> R.drawable.ic_kinopoisk
        is RatingUIState.IMDB -> R.drawable.ic_imdb
        is RatingUIState.PUB -> R.drawable.ic_kinopub
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier
                .size(14.dp),
            painter = painterResource(iconResource),
            contentDescription = "Rating",
            tint = Color.Unspecified,
        )

        Box(modifier = Modifier.placeholder(visible = state.isLoading)) {
            Text(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .drawWithContent {
                        if (state.isLoading.not()) {
                            drawContent()
                        }
                    },
                text = state.value,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RatingPreview() = PuberTheme {
    Column {
        Rating(RatingUIState.IMDB(value = "7.5"))
        Rating(RatingUIState.KP(value = "7.5"))
        Rating(RatingUIState.PUB(value = "7.5"))
    }
}