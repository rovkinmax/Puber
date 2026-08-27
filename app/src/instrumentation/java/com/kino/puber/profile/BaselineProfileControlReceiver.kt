package com.kino.puber.profile

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

internal class BaselineProfileControlReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val journal = BaselineNetworkJournal(context)
        when (intent.action) {
            ACTION_CLEAR -> {
                journal.clear()
                BaselineInstrumentationEnvironment.clearNetworkCaches(context)
                setResultCode(Activity.RESULT_OK)
                setResultData(RESULT_CLEARED)
            }

            ACTION_VERIFY -> {
                val result = journal.snapshot().joinToString("\n")
                setResultCode(if (result.isEmpty()) Activity.RESULT_OK else RESULT_VIOLATIONS)
                setResultData(result)
            }

            else -> {
                setResultCode(RESULT_UNKNOWN_ACTION)
                setResultData(RESULT_UNKNOWN_ACTION_TEXT)
            }
        }
    }

    companion object {
        const val ACTION_CLEAR = "com.kino.puber.profile.CLEAR"
        const val ACTION_VERIFY = "com.kino.puber.profile.VERIFY"
        const val RESULT_VIOLATIONS = 2

        private const val RESULT_CLEARED = "CLEARED"
        private const val RESULT_UNKNOWN_ACTION = 3
        private const val RESULT_UNKNOWN_ACTION_TEXT = "UNKNOWN_ACTION"
    }
}
