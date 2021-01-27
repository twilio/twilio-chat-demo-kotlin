package com.twilio.chat.app.viewModel

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twilio.chat.app.data.models.Client
import com.twilio.chat.app.data.models.Error
import com.twilio.chat.app.manager.LoginManager
import com.twilio.chat.app.common.enums.ChatError
import com.twilio.chat.app.common.SingleLiveEvent
import kotlinx.coroutines.launch
import timber.log.Timber

class LoginViewModel(
    private val loginManager: LoginManager,
    private val application: Application
) : ViewModel() {

    val isLoading = MutableLiveData<Boolean>()
    val onSignInError = SingleLiveEvent<ChatError>()
    val onSignInSuccess = SingleLiveEvent<Unit>()

    init {
        Timber.d("init view model ${this.hashCode()}")
        isLoading.value = false
    }

    fun signIn(identity: String, password: String) {
        if (isLoading.value == true) return

        Timber.d("signIn in viewModel")

        val credentialError = validateSignInDetails(identity, password)

        if (credentialError != ChatError.NO_ERROR) {
            Timber.d("creds not valid")
            onSignInError.value = credentialError
            return
        }
        Timber.d("creds valid")
        isLoading.value = true
        viewModelScope.launch {
            when (val response = loginManager.signIn(application.applicationContext, identity, password)) {
                is Client -> onSignInSuccess.call()
                is Error -> {
                    isLoading.value = false
                    onSignInError.value = response.error
                }
            }
        }
    }

    private fun validateSignInDetails(identity: String, password: String): ChatError {
        Timber.d("validateSignInDetails")
        return when {
            identity.isNotEmpty() && password.isNotEmpty() -> ChatError.NO_ERROR
            identity.isEmpty() && password.isNotEmpty() -> ChatError.INVALID_USERNAME
            identity.isNotEmpty() && password.isEmpty() -> ChatError.INVALID_PASSWORD
            else -> ChatError.INVALID_USERNAME_AND_PASSWORD
        }
    }
}
