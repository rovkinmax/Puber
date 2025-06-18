package com.kino.puber.core.ui.navigation

interface Screens {
    fun auth(): PuberScreen

    fun main(): PuberScreen

    fun favorites(): PuberScreen

    fun deviceSettings(): PuberScreen

    fun underDevelopment(): PuberScreen
}