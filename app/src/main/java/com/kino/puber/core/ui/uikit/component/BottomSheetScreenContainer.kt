package com.kino.puber.core.ui.uikit.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

@Composable
fun BottomSheetScreenContainer(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .focusGroup(),
        shape = SheetShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .size(width = 32.dp, height = 4.dp)
                    .align(Alignment.CenterHorizontally)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = RoundedCornerShape(2.dp),
                    )
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.animateContentSize()) {
                content()
            }
        }
    }
}
