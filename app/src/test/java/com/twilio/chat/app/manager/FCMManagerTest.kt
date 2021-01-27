package com.twilio.chat.app.manager

import android.app.Application
import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.twilio.chat.ChatClient
import com.twilio.chat.app.common.enums.ChatError
import com.twilio.chat.app.common.extensions.ChatException
import com.twilio.chat.app.common.extensions.registerFCMToken
import com.twilio.chat.app.data.ChatClientWrapper
import com.twilio.chat.app.data.CredentialStorage
import com.twilio.chat.app.testUtil.whenCall
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(
    FCMManager::class,
    ChatClientWrapper::class,
    CredentialStorage::class,
    ChatClient::class
)
class FCMManagerTest {

    private val mainThreadSurrogate = newSingleThreadContext("UI thread")

    @Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var application: Application
    @Mock
    private lateinit var context: Context
    @MockK
    private lateinit var chatClientWrapper: ChatClientWrapper
    @Mock
    private lateinit var credentialStorage: CredentialStorage

    private lateinit var chatClient: ChatClient
    private lateinit var fcmManager: FCMManager

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(mainThreadSurrogate)

        mockkStatic("com.twilio.chat.app.common.extensions.TwilioExtensionsKt")
        whenCall(application.applicationContext).thenReturn(context)
        chatClient = PowerMockito.mock(ChatClient::class.java)
        coEvery { chatClientWrapper.getChatClient() } returns chatClient

        fcmManager = FCMManagerImpl(application, chatClientWrapper, credentialStorage)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onNewToken - token saved in credentials if registered`() = runBlocking {
        val token = "fcm_token"
        coEvery { chatClient.registerFCMToken(token) } returns Unit
        fcmManager.onNewToken(token)
        verify(credentialStorage, times(1)).fcmToken = token
    }

    @Test
    fun `onNewToken - token not saved in credentials if registration failed`() = runBlocking {
        val token = "fcm_token"
        coEvery { chatClient.registerFCMToken(token) } throws ChatException(ChatError.TOKEN_ERROR)
        fcmManager.onNewToken(token)
        verify(credentialStorage, times(0)).fcmToken = token
    }
}
