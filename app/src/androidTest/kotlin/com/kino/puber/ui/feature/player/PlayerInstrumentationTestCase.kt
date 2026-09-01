package com.kino.puber.ui.feature.player

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import com.kaspersky.kaspresso.kaspresso.Kaspresso
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import com.kino.puber.ui.feature.player.component.PlayerComposeScreen
import io.github.kakaocup.compose.node.element.ComposeScreen
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

internal abstract class PlayerInstrumentationTestCase : TestCase(
    kaspressoBuilder = Kaspresso.Builder.simple(),
) {

    @get:Rule(order = 1)
    val namedStepRule: TestRule = TestRule { statement, description ->
        object : Statement() {
            override fun evaluate() {
                this@PlayerInstrumentationTestCase.run(testName = description.displayName) {
                    step(description.methodName.toPlayerStepName()) {
                        statement.evaluate()
                    }
                }
            }
        }
    }

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

private fun String?.toPlayerStepName(): String {
    return this
        ?.replace('_', ' ')
        ?.replace(CAMEL_CASE_BOUNDARY, " ")
        ?.replaceFirstChar(Char::uppercase)
        ?: "Run player instrumentation scenario"
}

private val CAMEL_CASE_BOUNDARY = Regex("(?<=[a-z0-9])(?=[A-Z])")
