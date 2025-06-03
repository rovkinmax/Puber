package com.kino.puber

import android.util.Log
import androidx.lifecycle.ViewModel

class MainViewmodel: ViewModel() {

    fun hello() {
        Log.d(this::class.simpleName, "Hello")
    }
}