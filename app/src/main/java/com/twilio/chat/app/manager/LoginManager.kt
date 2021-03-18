package com.twilio.chat.app.manager

import android.content.Context
import com.google.firebase.iid.FirebaseInstanceId
import com.twilio.chat.app.data.ChatClientWrapper
import com.twilio.chat.app.data.CredentialStorage
import com.twilio.chat.app.data.models.Client
import com.twilio.chat.app.data.models.Error
import com.twilio.chat.app.data.models.Response
import com.twilio.chat.app.common.enums.ChatError
import com.twilio.chat.app.common.enums.ChatError.EMPTY_CREDENTIALS
import com.twilio.chat.app.common.extensions.ChatException
import com.twilio.chat.app.repository.ChatRepository
import com.twilio.chat.app.common.extensions.registerFCMToken
import com.twilio.chat.app.common.extensions.retrieveToken
import com.twilio.chat.app.common.extensions.unregisterFCMToken
import timber.log.Timber

interface LoginManager {
    suspend fun signIn(applicationContext: Context, identity: String, password: String): Response
    suspend fun signInUsingStoredCredentials(applicationContext: Context): Response
    suspend fun signOut()
    suspend fun registerForFcm()
    suspend fun unregisterFromFcm()
    fun clearCredentials()
    fun isLoggedIn(): Boolean
}

class LoginManagerImpl(
    private val chatClient: ChatClientWrapper,
    private val chatRepository: ChatRepository,
    private val credentialStorage: CredentialStorage
) : LoginManager {

    override suspend fun registerForFcm() {
        try {
            val token = FirebaseInstanceId.getInstance().instanceId.retrieveToken()
            credentialStorage.fcmToken = token
            Timber.d("Registering for FCM: $token")
            chatClient.getChatClient().registerFCMToken(token)
        } catch (e: Exception) {
            Timber.d(e, "Failed to register FCM")
        }
    }

    override suspend fun unregisterFromFcm() {
        try {
            credentialStorage.fcmToken.takeIf { it.isNotEmpty() }?.let { token ->
                Timber.d("Unregistering from FCM")
                chatClient.getChatClient().unregisterFCMToken(token)
            }
        } catch (e: ChatException) {
            Timber.d(e, "Failed to unregister FCM")
        }
    }

    override suspend fun signIn(applicationContext: Context, identity: String, password: String): Response {
        Timber.d("signIn")
        val response = chatClient.create(applicationContext, identity, password)
        if (response is Client) {
            credentialStorage.storeCredentials(identity, password)
            chatRepository.subscribeToChatClientEvents()
            registerForFcm()
        }
        return response
    }

    override suspend fun signInUsingStoredCredentials(applicationContext: Context): Response {
        Timber.d("signInUsingStoredCredentials")
        if (credentialStorage.isEmpty()) return Error(EMPTY_CREDENTIALS)
        val identity = credentialStorage.identity
        val password = credentialStorage.password
        val response = chatClient.create(applicationContext, identity, password)
        if (response is Error) {
            handleError(response.error)
        } else {
            chatRepository.subscribeToChatClientEvents()
            registerForFcm()
        }
        return response
    }

    override suspend fun signOut() {
        unregisterFromFcm()
        clearCredentials()
        chatRepository.unsubscribeFromChatClientEvents()
        chatRepository.clear()
        chatClient.shutdown()
    }

    override fun isLoggedIn() = chatClient.isClientCreated && !credentialStorage.isEmpty()

    override fun clearCredentials() {
        credentialStorage.clearCredentials()
    }

    private fun handleError(error: ChatError) {
        Timber.d("handleError")
        if (error == ChatError.TOKEN_ACCESS_DENIED) {
            clearCredentials()
        }
    }
}
