package com.twilio.chat.app.viewModel

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.twilio.chat.Channel
import com.twilio.chat.app.*
import com.twilio.chat.app.common.asMessageListViewItems
import com.twilio.chat.app.common.enums.ChatError
import com.twilio.chat.app.common.extensions.ChatException
import com.twilio.chat.app.data.localCache.entity.ChannelDataItem
import com.twilio.chat.app.data.localCache.entity.MemberDataItem
import com.twilio.chat.app.data.models.MessageListViewItem
import com.twilio.chat.app.data.models.RepositoryRequestStatus
import com.twilio.chat.app.data.models.RepositoryResult
import com.twilio.chat.app.manager.ChannelManager
import com.twilio.chat.app.repository.ChatRepository
import com.twilio.chat.app.testUtil.*
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.io.InputStream
import java.util.*

@RunWith(PowerMockRunner::class)
@PrepareForTest(
    Channel::class
)
class ChannelViewModelTest {

    @Rule
    var coroutineTestRule = CoroutineTestRule()

    @Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val channelSid = "channelSid"
    private val channelName = "Test Channel"
    private val messageCount = 10
    private val messageBody = "Test Message"

    @MockK
    private lateinit var chatRepository: ChatRepository

    @MockK
    private lateinit var channelManager: ChannelManager

    @MockK
    private lateinit var context: Context

    private lateinit var channelViewModel: ChannelViewModel

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(Dispatchers.Default)
        val channel: ChannelDataItem? = createTestChannelDataItem(sid = channelSid, friendlyName = channelName)
        coEvery { chatRepository.getChannel(any()) } returns
                flowOf(RepositoryResult(channel, RepositoryRequestStatus.COMPLETE))
        coEvery { chatRepository.getMessages(any(), any()) } returns
                flowOf(RepositoryResult(listOf< MessageListViewItem>().asPagedList(), RepositoryRequestStatus.COMPLETE))
        coEvery { channelManager.updateMessageStatus(any(), any()) } returns Unit
        coEvery { chatRepository.getTypingMembers(channelSid) } returns flowOf(listOf())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `channelViewModel_channelName should be initialized on init`() = runBlocking {
        channelViewModel = ChannelViewModel(context, channelSid, chatRepository, channelManager)
        assertEquals(channelName, channelViewModel.channelName.waitValue())
    }

    @Test
    fun `channelViewModel_messageItems should contain all messages`() = runBlocking {
        val expectedMessages = getMockedMessages(messageCount, messageBody, channelSid).asMessageListViewItems()

        coEvery { chatRepository.getMessages(any(), any()) } returns
                flowOf(RepositoryResult(expectedMessages.asPagedList(), RepositoryRequestStatus.COMPLETE))

        channelViewModel = ChannelViewModel(context, channelSid, chatRepository, channelManager)
        assertEquals(expectedMessages, channelViewModel.messageItems.waitValue())
        assertEquals(messageCount, channelViewModel.messageItems.waitValue().size)
    }

    @Test
    fun `channelViewModel_messageItems should be empty when Error occurred`() = runBlocking {
        coEvery { chatRepository.getMessages(any(), any()) } returns
                flowOf(RepositoryResult(listOf< MessageListViewItem>().asPagedList(), RepositoryRequestStatus.Error(ChatError.GENERIC_ERROR)))

        channelViewModel = ChannelViewModel(context, channelSid, chatRepository, channelManager)
        assertEquals(0, channelViewModel.messageItems.waitValue().size)
    }

    @Test
    fun `channelViewModel_sendTextMessage should call onMessageSent on success`() = runBlocking {
        coEvery { channelManager.sendTextMessage(any(), any()) } returns Unit
        channelViewModel = ChannelViewModel(context, channelSid, chatRepository, channelManager)
        channelViewModel.sendTextMessage(messageBody)

        assertTrue(channelViewModel.onMessageSent.waitCalled())
        assertTrue(channelViewModel.onMessageError.waitNotCalled())
    }

    @Test
    fun `channelViewModel_sendTextMessage should call onMessageError on failure`() = runBlocking {
        coEvery { channelManager.sendTextMessage(any(), any()) } throws ChatException(ChatError.MESSAGE_SEND_FAILED)
        channelViewModel = ChannelViewModel(context, channelSid, chatRepository, channelManager)
        channelViewModel.sendTextMessage(messageBody)

        assertTrue(channelViewModel.onMessageSent.waitNotCalled())
        assertTrue(channelViewModel.onMessageError.waitValue(ChatError.MESSAGE_SEND_FAILED))
    }

    @Test
    fun `channelViewModel_resendTextMessage should call onMessageSent on success`() = runBlocking {
        coEvery { channelManager.retrySendTextMessage(any()) } returns Unit
        channelViewModel = ChannelViewModel(context, channelSid, chatRepository, channelManager)
        channelViewModel.resendTextMessage(UUID.randomUUID().toString())

        assertTrue(channelViewModel.onMessageSent.waitCalled())
        assertTrue(channelViewModel.onMessageError.waitNotCalled())
    }

    @Test
    fun `channelViewModel_resendTextMessage should call onMessageError on failure`() = runBlocking {
        coEvery { channelManager.retrySendTextMessage(any()) } throws ChatException(ChatError.MESSAGE_SEND_FAILED)
        channelViewModel = ChannelViewModel(context, channelSid, chatRepository, channelManager)
        channelViewModel.resendTextMessage(UUID.randomUUID().toString())

        assertTrue(channelViewModel.onMessageSent.waitNotCalled())
        assertTrue(channelViewModel.onMessageError.waitValue(ChatError.MESSAGE_SEND_FAILED))
    }

    @Test
    fun `typingMembersList is updated`() = runBlocking {
        val userIdentity = "user1"
        coEvery { chatRepository.getTypingMembers(channelSid) } returns flowOf(listOf(
            MemberDataItem(
                identity = userIdentity, channelSid = channelSid, lastConsumptionTimestamp = null,
                lastConsumedMessageIndex = null, sid = "321", friendlyName = "user", isOnline = true
            )
        ))

        channelViewModel = ChannelViewModel(context, channelSid, chatRepository, channelManager)
        assertEquals(listOf(userIdentity), channelViewModel.typingMembersList.waitValue())
    }

    @Test
    fun `sendMediaMessage should call onMessageSent on success`() = runBlocking {
        coEvery { channelManager.sendMediaMessage(any(), any(), any(), any(), any()) } returns Unit
        channelViewModel = ChannelViewModel(context, channelSid, chatRepository, channelManager)
        channelViewModel.sendMediaMessage("", mock(InputStream::class.java), null, null)

        assertTrue(channelViewModel.onMessageSent.waitCalled())
        assertTrue(channelViewModel.onMessageError.waitNotCalled())
    }

    @Test
    fun `sendMediaMessage should call onMessageError on failure`() = runBlocking {
        coEvery { channelManager.sendMediaMessage(any(), any(), any(), any(), any()) } throws ChatException(ChatError.MESSAGE_SEND_FAILED)
        channelViewModel = ChannelViewModel(context, channelSid, chatRepository, channelManager)
        channelViewModel.sendMediaMessage("", mock(InputStream::class.java), null, null)

        assertTrue(channelViewModel.onMessageSent.waitNotCalled())
        assertTrue(channelViewModel.onMessageError.waitValue(ChatError.MESSAGE_SEND_FAILED))
    }

    @Test
    fun `resendMediaMessage should call onMessageSent on success`() = runBlocking {
        coEvery { channelManager.retrySendMediaMessage(any(), any()) } returns Unit
        channelViewModel = ChannelViewModel(context, channelSid, chatRepository, channelManager)
        channelViewModel.resendMediaMessage( mock(InputStream::class.java), "")

        assertTrue(channelViewModel.onMessageSent.waitCalled())
        assertTrue(channelViewModel.onMessageError.waitNotCalled())
    }

    @Test
    fun `resendMediaMessage should call onMessageError on failure`() = runBlocking {
        coEvery { channelManager.retrySendMediaMessage(any(), any()) } throws ChatException(ChatError.MESSAGE_SEND_FAILED)
        channelViewModel = ChannelViewModel(context, channelSid, chatRepository, channelManager)
        channelViewModel.resendMediaMessage( mock(InputStream::class.java), "")

        assertTrue(channelViewModel.onMessageSent.waitNotCalled())
        assertTrue(channelViewModel.onMessageError.waitValue(ChatError.MESSAGE_SEND_FAILED))
    }

    @Test
    fun `getMediaMessageFileSource should return getMediaContentTemporaryUrl`() = runBlocking {
        val resultUrl = "asd"
        val messageIndex = 0L
        coEvery { channelManager.getMediaContentTemporaryUrl(any()) } returns resultUrl
        channelViewModel = ChannelViewModel(context, channelSid, chatRepository, channelManager)

        assertEquals(resultUrl, channelViewModel.getMediaMessageFileSource( messageIndex))
        coVerify { channelManager.getMediaContentTemporaryUrl(messageIndex) }
    }

    @Test
    fun `getMediaMessageFileSource should call onMessageError on failure`() = runBlocking {
        coEvery { channelManager.getMediaContentTemporaryUrl(any()) } throws ChatException(ChatError.MESSAGE_MEDIA_DOWNLOAD_FAILED)
        channelViewModel = ChannelViewModel(context, channelSid, chatRepository, channelManager)

        assertEquals(null, channelViewModel.getMediaMessageFileSource(0))
        assertTrue(channelViewModel.onMessageError.waitValue(ChatError.MESSAGE_MEDIA_DOWNLOAD_FAILED))
    }

    @Test
    fun `updateMessageMediaDownloadStatus should call ChannelManager#updateMessageMediaDownloadStatus`() = runBlocking {
        val messageIndex = 1L
        val downloading = false
        val downloadedBytes = 2L
        val downloadLocation = "asd"
        coEvery { channelManager.updateMessageMediaDownloadStatus(any(), any(), any(), any()) } returns Unit
        channelViewModel = ChannelViewModel(context, channelSid, chatRepository, channelManager)

        channelViewModel.updateMessageMediaDownloadStatus(messageIndex, downloading, downloadedBytes, downloadLocation)
        coVerify { channelManager.updateMessageMediaDownloadStatus(messageIndex, downloading, downloadedBytes, downloadLocation) }
    }
}
