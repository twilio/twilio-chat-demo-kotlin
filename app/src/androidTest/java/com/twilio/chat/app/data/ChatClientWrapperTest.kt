package com.twilio.chat.app.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.twilio.chat.app.INVALID_IDENTITY
import com.twilio.chat.app.INVALID_PASSWORD
import com.twilio.chat.app.data.models.Error
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatClientWrapperTest {

    private lateinit var context: Context
    private lateinit var chatClientWrapper: ChatClientWrapper

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext

        ChatClientWrapper.recreateInstance()
        chatClientWrapper = ChatClientWrapper.INSTANCE
    }

    @Test
    fun create_withInvalidCredentials_returnsError() = runBlocking {
        val response = chatClientWrapper.create(context.applicationContext, INVALID_IDENTITY, INVALID_PASSWORD)
        assertTrue(response is Error)
    }
}
