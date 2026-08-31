package com.kino.puber.profile

import android.content.Context
import com.kino.puber.playertestfixtures.network.LoopbackNetworkBlocker
import com.kino.puber.playertestfixtures.network.LoopbackNetworkJournal

internal object BaselineNetworkBlocker {

    fun install(context: Context, allowedOrigin: String) {
        LoopbackNetworkBlocker.install(context, allowedOrigin)
    }

    fun journal(context: Context): LoopbackNetworkJournal =
        LoopbackNetworkJournal(context.applicationContext)
}
