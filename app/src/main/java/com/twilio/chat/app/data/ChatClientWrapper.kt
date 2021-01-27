package com.twilio.chat.app.data

import android.content.Context
import android.net.Uri
import androidx.annotation.RestrictTo
import androidx.annotation.RestrictTo.Scope
import com.twilio.chat.ChatClient
import com.twilio.chat.app.BuildConfig
import com.twilio.chat.app.common.enums.ChatError
import com.twilio.chat.app.common.extensions.createClientAsync
import com.twilio.chat.app.data.models.Client
import com.twilio.chat.app.data.models.Error
import com.twilio.chat.app.data.models.Response
import com.twilio.chat.app.data.models.Token
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.FileNotFoundException
import java.net.URL

class ChatClientWrapper {

    private val deferredClient = CompletableDeferred<ChatClient>()

    private lateinit var identity: String
    private lateinit var password: String

    val isClientCreated get() = deferredClient.isCompleted && !deferredClient.isCancelled

    suspend fun getChatClient() = deferredClient.await() // Business logic will wait until chatClient created

    /**
     * Get token and call createClient if token is not null
     */
    suspend fun create(applicationContext: Context, identity: String, password: String): Response {
        Timber.d("create")
        return when (val tokenResponse = getToken(identity, password)) {
            is Error -> tokenResponse
            is Token -> {
                Timber.d("token: ${tokenResponse.token}")
                val createClientResponse = createClient(applicationContext, tokenResponse.token)
                if (createClientResponse is Client) {
                    this@ChatClientWrapper.identity = identity
                    this@ChatClientWrapper.password = password
                    this@ChatClientWrapper.deferredClient.complete(createClientResponse.chatClient)
                }
                createClientResponse
            }
            else -> getGenericError()
        }
    }

    suspend fun shutdown() {
        getChatClient().shutdown()
    }

    /**
     * Create client and return it on success, otherwise return error
     */
    private suspend fun createClient(applicationContext: Context, token: String): Response {
        return createClientAsync(
            applicationContext,
            token,
            ChatClient.Properties.Builder().createProperties()
        )
    }

    /**
     * Fetch Twilio access token and return it, if token is non-null, otherwise return error
     */
    private suspend fun getToken(userName: String, password: String): Response {
        return withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(TOKEN_URL)
                    .buildUpon()
                    .appendQueryParameter(QUERY_IDENTITY, userName)
                    .appendQueryParameter(QUERY_PASSWORD, password)
                    .build()
                    .toString()
                Token(URL(uri).readText())
            } catch (e: FileNotFoundException) {
                getTokenAccessDeniedError()
            } catch (e: Exception) {
                getTokenError()
            }
        }
    }

    /**
     * Construct generic token error
     */
    private fun getTokenError() = Error(ChatError.TOKEN_ERROR)

    /**
     * Construct token access denied error
     */
    private fun getTokenAccessDeniedError() = Error(ChatError.TOKEN_ACCESS_DENIED)

    /**
     * Construct generic error
     */
    private fun getGenericError() = Error(ChatError.GENERIC_ERROR)

    companion object {
        private const val TOKEN_URL = BuildConfig.ACCESS_TOKEN_SERVICE_URL
        private const val QUERY_IDENTITY = "identity"
        private const val QUERY_PASSWORD = "password"

        val INSTANCE get() = _instance ?: error("call ChatClientWrapper.createInstance() first")

        private var _instance: ChatClientWrapper? = null

        fun createInstance() {
            check(_instance == null) { "ChatClientWrapper singleton instance has been already created" }
            _instance = ChatClientWrapper()
        }

        @RestrictTo(Scope.TESTS)
        fun recreateInstance() {
            _instance?.let { instance ->
                // Shutdown old client if it will ever be created
                GlobalScope.launch { instance.getChatClient().shutdown() }
            }

            _instance = null
            createInstance()
        }
    }
}
