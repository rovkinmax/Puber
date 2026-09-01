package com.kino.puber.ui.feature.player

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import com.kaspersky.components.composesupport.config.withComposeSupport
import com.kaspersky.kaspresso.kaspresso.Kaspresso
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import com.kino.puber.ui.feature.player.component.PlayerComposeScreen
import io.github.kakaocup.compose.node.element.ComposeScreen

internal abstract class PlayerInstrumentationTestCase : TestCase(
    kaspressoBuilder = Kaspresso.Builder.simple(),
)

internal abstract class PlayerComposeInstrumentationTestCase : TestCase(
    kaspressoBuilder = Kaspresso.Builder.withComposeSupport(),
) {
    protected fun onPlayerScreen(
        semanticsProvider: SemanticsNodeInteractionsProvider,
        actions: PlayerComposeScreen.() -> Unit,
    ) {
        ComposeScreen.onComposeScreen<PlayerComposeScreen>(
            semanticsProvider = semanticsProvider,
            function = actions,
        )
    }
}
