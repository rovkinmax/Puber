package com.kino.puber

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kino.puber.domain.interactor.IAuthInteractor
import kotlinx.coroutines.launch
import timber.log.Timber

class MainViewmodel(
    private val authInteractor: IAuthInteractor,
) : ViewModel() {

    fun hello() {
        viewModelScope.launch {
            authInteractor.getAuthState().collect {
                Timber.d("Auth state - $it")
            }
        }
        Timber.d("Hello")
    }
}