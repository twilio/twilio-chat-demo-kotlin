package com.twilio.chat.app.manager

import android.app.Application
import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.iid.InstanceIdResult
import com.twilio.chat.app.data.ChatClientWrapper
import com.twilio.chat.app.data.CredentialStorage
import com.twilio.chat.app.data.models.Client
import com.twilio.chat.app.data.models.Error
import com.twilio.chat.app.testUtil.*
import com.twilio.chat.app.common.enums.ChatError
import com.twilio.chat.app.repository.ChatRepository
import com.twilio.chat.app.testUtil.whenCall
import kotlinx.coroutines.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runBlockingTest
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

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
@RunWith(PowerMockRunner::class)
@PrepareForTest(
    LoginManager::class,
    ChatClientWrapper::class,
    CredentialStorage::class,
    ChatRepository::class,
    Client::class,
    Error::class,
    FirebaseInstanceId::class
)
class LoginManagerTest {
    @Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val mainThreadSurrogate = newSingleThreadContext("UI thread")

    private lateinit var loginManager: LoginManager
    private lateinit var error: Error

    @Mock
    private lateinit var application: Application
    @Mock
    private lateinit var firebaseInstanceId: FirebaseInstanceId
    @Mock
    private lateinit var instanceId: Task<InstanceIdResult>
    @Mock
    private lateinit var taskResult: Task<Task<InstanceIdResult>>
    @Mock
    private lateinit var context: Context
    @Mock
    private lateinit var client: Client
    @Mock
    private lateinit var chatClientWrapper: ChatClientWrapper
    @Mock
    private lateinit var credentialStorage: CredentialStorage
    @Mock
    private lateinit var chatRepository: ChatRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(mainThreadSurrogate)
        PowerMockito.mockStatic(FirebaseInstanceId::class.java)

        loginManager = LoginManagerImpl(chatClientWrapper, chatRepository, credentialStorage)

        whenCall(application.applicationContext).thenReturn(context)
        whenCall(FirebaseInstanceId.getInstance()).thenReturn(firebaseInstanceId)
        whenCall(FirebaseInstanceId.getInstance().instanceId).thenReturn(instanceId)
        `when`(instanceId.addOnCompleteListener(any())).thenAnswer {
            (it.getArgument(0) as OnCompleteListener<Task<InstanceIdResult>>).onComplete(taskResult)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `signIn() should attempt sign in`() = runBlockingTest {
        loginManager.signIn(context, VALID_CREDENTIAL, VALID_CREDENTIAL)
        verify(chatClientWrapper, times(1)).create(context, VALID_CREDENTIAL, VALID_CREDENTIAL)
    }

    @Test
    fun `signInUsingStoredCredentials() should attempt sign in`() = runBlockingTest {
        credentialStorageNotEmpty(credentialStorage, VALID_CREDENTIAL)
        loginManager.signInUsingStoredCredentials(context)
        verify(chatClientWrapper, times(1)).create(context, VALID_CREDENTIAL, VALID_CREDENTIAL)
    }

    @Test
    fun `signInUsingStoredCredentials() should not attempt sign in when credential storage is empty`() = runBlockingTest {
        credentialStorageEmpty(credentialStorage)
        loginManager.signInUsingStoredCredentials(context)
        verify(chatClientWrapper, times(0)).create(context, INVALID_CREDENTIAL, INVALID_CREDENTIAL)
    }

    @Test
    fun `signIn() should attempt to store credentials when response is Client`() = runBlockingTest {
        whenCall(chatClientWrapper.create(context, VALID_CREDENTIAL, VALID_CREDENTIAL)).thenReturn(client)
        loginManager.signIn(context, VALID_CREDENTIAL, VALID_CREDENTIAL)
        verify(credentialStorage, times(1)).storeCredentials(VALID_CREDENTIAL, VALID_CREDENTIAL)
    }

    @Test
    fun `signIn() should not attempt to clear credentials when response is fatal error`() = runBlockingTest {
        error = Error(ChatError.TOKEN_ACCESS_DENIED)
        whenCall(chatClientWrapper.create(context, INVALID_CREDENTIAL, INVALID_CREDENTIAL)).thenReturn(error)
        loginManager.signIn(context, INVALID_CREDENTIAL, INVALID_CREDENTIAL)
        verify(credentialStorage, times(0)).clearCredentials()
    }

    @Test
    fun `signIn() should not attempt to store credentials when response is not Client`() = runBlockingTest {
        error = Error(ChatError.GENERIC_ERROR)
        whenCall(chatClientWrapper.create(context, INVALID_CREDENTIAL, INVALID_CREDENTIAL)).thenReturn(error)
        loginManager.signIn(context, INVALID_CREDENTIAL, INVALID_CREDENTIAL)
        verify(credentialStorage, times(0)).storeCredentials(INVALID_CREDENTIAL, INVALID_CREDENTIAL)
    }

    @Test
    fun `signInUsingStoredCredentials() should not attempt to store credentials`() = runBlockingTest {
        whenCall(chatClientWrapper.create(context, VALID_CREDENTIAL, VALID_CREDENTIAL)).thenReturn(client)
        loginManager.signInUsingStoredCredentials(context)
        verify(credentialStorage, times(0)).storeCredentials(VALID_CREDENTIAL, VALID_CREDENTIAL)
    }

    @Test
    fun `signInUsingStoredCredentials() should attempt to clear credentials when response is fatal error`() = runBlockingTest {
        credentialStorageNotEmpty(credentialStorage, OUTDATED_CREDENTIAL)
        error = Error(ChatError.TOKEN_ACCESS_DENIED)
        whenCall(chatClientWrapper.create(context, OUTDATED_CREDENTIAL, OUTDATED_CREDENTIAL)).thenReturn(error)
        loginManager.signInUsingStoredCredentials(context)
        verify(credentialStorage, times(1)).clearCredentials()
    }

    @Test
    fun `signOut should clear credentials`() = runBlockingTest {
        credentialStorageNotEmpty(credentialStorage, OUTDATED_CREDENTIAL)
        loginManager.signOut()
        verify(credentialStorage, times(1)).clearCredentials()
        verify(chatRepository, times(1)).clear()
        verify(chatClientWrapper, times(1)).shutdown()
    }
}
