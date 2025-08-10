package com.kino.puber.core.ui.uikit.model

object Lorem {
    private val words = listOf(
        "lorem", "ipsum", "dolor", "sit", "amet", "consectetur", "adipiscing", "elit",
        "sed", "do", "eiusmod", "tempor", "incididunt", "ut", "labore", "et", "dolore",
        "magna", "aliqua"
    )

    fun words(count: Int, newLineEachWordCount: Int = 0): String {
        return buildString {
            repeat(count) { index ->
                if (newLineEachWordCount > 0 && index > 0 && index % newLineEachWordCount == 0) {
                    append("\n")
                }
                append(words.random())
                append(" ")
            }
        }.trim()
    }
}