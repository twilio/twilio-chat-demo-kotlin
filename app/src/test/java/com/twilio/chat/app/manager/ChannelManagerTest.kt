package com.twilio.chat.app.manager

import android.os.Looper
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.gson.Gson
import com.nhaarman.mockitokotlin2.*
import com.twilio.chat.*
import com.twilio.chat.app.common.enums.ChatError
import com.twilio.chat.app.common.enums.Reaction
import com.twilio.chat.app.common.enums.SendStatus
import com.twilio.chat.app.common.extensions.*
import com.twilio.chat.app.common.getReactions
import com.twilio.chat.app.createTestMessageDataItem
import com.twilio.chat.app.data.ChatClientWrapper
import com.twilio.chat.app.data.models.ReactionAttributes
import com.twilio.chat.app.getExpectedReactions
import com.twilio.chat.app.repository.ChatRepository
import com.twilio.chat.app.testUtil.CoroutineTestRule
import com.twilio.chat.app.testUtil.toMessageMock
import com.twilio.chat.app.testUtil.whenCall
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(
    ChatClient::class,
    Channel::class,
    Message::class,
    Members::class,
    Member::class,
    Message.Media::class
)
class ChannelManagerTest {

    private val testDispatcher: TestCoroutineDispatcher = TestCoroutineDispatcher()

    @Rule
    var coroutineTestRule = CoroutineTestRule(testDispatcher)

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val channelSid = "channel_1"
    private val memberIdentity = "member_id"

    private lateinit var channelManager: ChannelManagerImpl

    @MockK
    private lateinit var channels: Channels
    @MockK
    private lateinit var messages: Messages
    @MockK
    private lateinit var members: Members
    @MockK
    private lateinit var member: Member
    @MockK
    private lateinit var chatClientWrapper: ChatClientWrapper
    @Mock
    private lateinit var chatRepository: ChatRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(Dispatchers.Unconfined)

        mockkStatic("com.twilio.chat.app.common.extensions.TwilioExtensionsKt")
        mockkStatic("com.twilio.chat.app.common.extensions.PaginatorExtensionsKt")
        mockkStatic("com.twilio.chat.app.common.DataConverterKt")
        mockkStatic("android.os.Looper")
        every { Looper.getMainLooper() } returns mockk()

        val chatClient = PowerMockito.mock(ChatClient::class.java)
        whenCall(chatClient.channels).thenReturn(channels)
        whenCall(chatClient.myIdentity).thenReturn(memberIdentity)

        coEvery { channels.getChannel(any()).messages } returns messages
        coEvery { channels.getChannel(any()).members } returns members
        coEvery { channels.getChannel(any()).members.getMember(any()) } returns member
        coEvery { member.identity } returns memberIdentity
        coEvery { chatClientWrapper.getChatClient() } returns chatClient

        channelManager = ChannelManagerImpl(channelSid, chatClientWrapper, chatRepository, coroutineTestRule.testDispatcherProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sendTextMessage() should update local cache with send status SENT on success`() = runBlockingTest {
        val messageUuid = "uuid"
        val message = createTestMessageDataItem(body = "test message", uuid = messageUuid)
        coEvery { member.sid } returns message.memberSid
        coEvery { messages.sendMessage(any()) } returns message.toMessageMock(member)
        channelManager.sendTextMessage(message.body!!, message.uuid)

        verify(chatRepository).insertMessage(argThat {
            message.body == body && message.uuid == uuid && sendStatus == SendStatus.SENDING.value
        })
    }

    @Test
    fun `sendTextMessage() should update local cache with send status SENDING on failure`() = runBlockingTest {
        val message = createTestMessageDataItem(body = "test message", uuid = "uuid")
        coEvery { member.sid } returns message.memberSid
        coEvery { messages.sendMessage(any()) } throws ChatException(ChatError.MESSAGE_SEND_FAILED)
        try {
            channelManager.sendTextMessage(message.body!!, message.uuid)
        } catch (e: ChatException) {
            assert(ChatError.MESSAGE_SEND_FAILED == e.error)
        }

        verify(chatRepository).insertMessage(argThat {
            message.body == body && message.uuid == uuid && sendStatus == SendStatus.SENDING.value
        })

        verify(chatRepository, times(0)).updateMessageByUuid(argThat {
            message.body == body && message.uuid == uuid && sendStatus == SendStatus.SENT.value
        })
    }

    @Test
    fun `sendTextMessage() should NOT update local cache with on member failure`() = runBlockingTest {
        val message = createTestMessageDataItem(body = "test message", uuid = "uuid")
        coEvery { member.sid } returns message.memberSid
        coEvery { channels.getChannel(any()).members.getMember(any()) } throws ChatException(ChatError.MESSAGE_SEND_FAILED)
        try {
            channelManager.sendTextMessage(message.body!!, message.uuid)
        } catch (e: ChatException) {
            assert(ChatError.MESSAGE_SEND_FAILED == e.error)
        }

        verify(chatRepository, times(0)).insertMessage(argThat {
            message.body == body && message.uuid == uuid && sendStatus == SendStatus.SENDING.value
        })

        verify(chatRepository, times(0)).updateMessageByUuid(argThat {
            message.body == body && message.uuid == uuid && sendStatus == SendStatus.SENT.value
        })
    }

    @Test
    fun `retrySendMessage() should update local cache with send status SENT on success`() = runBlockingTest {
        val message = createTestMessageDataItem(body = "test message", uuid = "uuid",
            author = memberIdentity, sendStatus = SendStatus.ERROR.value)
        coEvery { member.sid } returns message.memberSid
        coEvery { messages.sendMessage(any()) } returns message.toMessageMock(member)
        whenCall(chatRepository.getMessageByUuid(message.uuid)).thenReturn(message)
        channelManager.retrySendTextMessage(message.uuid)

        inOrder(chatRepository).verify(chatRepository).updateMessageByUuid(argThat {
            message.body == body && message.uuid == uuid && sendStatus == SendStatus.SENDING.value
        })
        inOrder(chatRepository).verify(chatRepository).updateMessageByUuid(argThat {
            message.body == body && message.uuid == uuid && sendStatus == SendStatus.SENT.value
        })
    }

    @Test
    fun `retrySendMessage() should NOT update local cache if already sending`() = runBlockingTest {
        val message = createTestMessageDataItem(body = "test message", uuid = "uuid",
            author = memberIdentity, sendStatus = SendStatus.SENDING.value)
        coEvery { member.sid } returns message.memberSid
        coEvery { messages.sendMessage(any()) } returns message.toMessageMock(member)
        whenCall(chatRepository.getMessageByUuid(message.uuid)).thenReturn(message)
        channelManager.retrySendTextMessage(message.uuid)

        verify(chatRepository, times(0)).updateMessageByUuid(argThat {
            message.body == body && message.uuid == uuid && sendStatus == SendStatus.SENT.value
        })
    }

    @Test
    fun `retrySendMessage() should update local cache with send status SENDING on failure`() = runBlockingTest {
        val message = createTestMessageDataItem(body = "test message", uuid = "uuid",
            author = memberIdentity, sendStatus = SendStatus.ERROR.value)
        coEvery { member.sid } returns message.memberSid
        coEvery { messages.sendMessage(any()) } returns message.toMessageMock(member)
        coEvery { channels.getChannel(any()) } throws ChatException(ChatError.MESSAGE_SEND_FAILED)
        whenCall(chatRepository.getMessageByUuid(message.uuid)).thenReturn(message)
        try {
            channelManager.retrySendTextMessage(message.uuid)
        } catch (e: ChatException) {
            assert(ChatError.MESSAGE_SEND_FAILED == e.error)
        }

        verify(chatRepository).updateMessageByUuid(argThat {
            message.body == body && message.uuid == uuid && sendStatus == SendStatus.SENDING.value
        })
    }

    @Test
    fun `sendMediaMessage() should update local cache with send status SENT on success`() = runBlockingTest {
        val messageUuid = "uuid"
        val mediaUri = "uri"
        val fileName = "fileName"
        val mimeType = "mimeType"
        val message = createTestMessageDataItem()
        every { member.sid } returns message.memberSid
        coEvery { messages.sendMessage(any()) } returns message.toMessageMock(member)
        channelManager.sendMediaMessage(mediaUri, mockk(), fileName, mimeType, messageUuid)

        verify(chatRepository).insertMessage(argThat {
            type == Message.Type.MEDIA.value
                    && body == null
                    && uuid == messageUuid
                    && sendStatus == SendStatus.SENDING.value
                    && mediaFileName == fileName
                    && mediaUploadUri == mediaUri
                    && mediaType == mimeType
        })
    }

    @Test
    fun `sendMediaMessage() should update local cache with send status SENDING on failure`() = runBlockingTest {
        val messageUuid = "uuid"
        val mediaUri = "uri"
        val fileName = "fileName"
        val mimeType = "mimeType"
        val message = createTestMessageDataItem()
        every { member.sid } returns message.memberSid
        coEvery { messages.sendMessage(any()) } throws ChatException(ChatError.MESSAGE_SEND_FAILED)
        try {
            channelManager.sendMediaMessage(mediaUri, mockk(), fileName, mimeType, messageUuid)
        } catch (e: ChatException) {
            assert(ChatError.MESSAGE_SEND_FAILED == e.error)
        }

        verify(chatRepository).insertMessage(argThat {
            type == Message.Type.MEDIA.value
                    && body == null
                    && uuid == messageUuid
                    && sendStatus == SendStatus.SENDING.value
                    && mediaFileName == fileName
                    && mediaUploadUri == mediaUri
                    && mediaType == mimeType
        })

        verify(chatRepository, never()).updateMessageByUuid(argThat {
            type == Message.Type.MEDIA.value
                    && body == null
                    && uuid == messageUuid
                    && sendStatus == SendStatus.SENT.value
                    && mediaFileName == fileName
                    && mediaUploadUri == mediaUri
                    && mediaType == mimeType
        })
    }

    @Test
    fun `sendMediaMessage() should NOT update local cache with on member failure`() = runBlockingTest {
        val messageUuid = "uuid"
        val mediaUri = "uri"
        val fileName = "fileName"
        val mimeType = "mimeType"
        val message = createTestMessageDataItem()
        every { member.sid } returns message.memberSid
        coEvery { channels.getChannel(any()).members.getMember(any()) } throws ChatException(ChatError.MESSAGE_SEND_FAILED)
        try {
            channelManager.sendMediaMessage(mediaUri, mockk(), fileName, mimeType, messageUuid)
        } catch (e: ChatException) {
            assert(ChatError.MESSAGE_SEND_FAILED == e.error)
        }

        verify(chatRepository, never()).insertMessage(argThat {
            type == Message.Type.MEDIA.value
                    && body == null
                    && uuid == messageUuid
                    && sendStatus == SendStatus.SENDING.value
                    && mediaFileName == fileName
                    && mediaUploadUri == mediaUri
                    && mediaType == mimeType
        })

        verify(chatRepository, never()).updateMessageByUuid(argThat {
            type == Message.Type.MEDIA.value
                    && body == null
                    && uuid == messageUuid
                    && sendStatus == SendStatus.SENT.value
                    && mediaFileName == fileName
                    && mediaUploadUri == mediaUri
                    && mediaType == mimeType
        })
    }

    @Test
    fun `retrySendMediaMessage() should update local cache with send status SENT on success`() = runBlockingTest {
        val messageUuid = "uuid"
        val mediaUri = "uri"
        val fileName = "fileName"
        val mimeType = "mimeType"
        val message = createTestMessageDataItem(uuid = messageUuid, author = memberIdentity,
            sendStatus = SendStatus.ERROR.value, mediaUploadUri = mediaUri, mediaFileName = fileName,
            mediaType = mimeType, type = Message.Type.MEDIA.value, mediaSid = "sid")
        every { member.sid } returns message.memberSid
        coEvery { messages.sendMessage(any()) } returns message.toMessageMock(member)
        whenCall(chatRepository.getMessageByUuid(message.uuid)).thenReturn(message)
        channelManager.retrySendMediaMessage(mockk(), message.uuid)

        inOrder(chatRepository).verify(chatRepository).updateMessageByUuid(argThat {
            type == Message.Type.MEDIA.value
                    && body == ""
                    && uuid == messageUuid
                    && sendStatus == SendStatus.SENDING.value
                    && mediaFileName == fileName
                    && mediaType == mimeType
        })
        inOrder(chatRepository).verify(chatRepository).updateMessageByUuid(argThat {
            type == Message.Type.MEDIA.value
                    && body == ""
                    && uuid == messageUuid
                    && sendStatus == SendStatus.SENT.value
                    && mediaFileName == fileName
                    && mediaType == mimeType
        })
    }

    @Test
    fun `retrySendMediaMessage() should NOT update local cache if already sending`() = runBlockingTest {
        val messageUuid = "uuid"
        val mediaUri = "uri"
        val fileName = "fileName"
        val mimeType = "mimeType"
        val message = createTestMessageDataItem(uuid = messageUuid, author = memberIdentity,
            sendStatus = SendStatus.SENDING.value, mediaUploadUri = mediaUri, mediaFileName = fileName,
            mediaType = mimeType, type = Message.Type.MEDIA.value, mediaSid = "sid")
        coEvery { member.sid } returns message.memberSid
        coEvery { messages.sendMessage(any()) } returns message.toMessageMock(member)
        whenCall(chatRepository.getMessageByUuid(message.uuid)).thenReturn(message)
        channelManager.retrySendMediaMessage(mockk(), message.uuid)

        verify(chatRepository, times(0)).updateMessageByUuid(argThat {
            type == Message.Type.MEDIA.value
                    && body == ""
                    && uuid == messageUuid
                    && sendStatus == SendStatus.SENT.value
                    && mediaFileName == fileName
                    && mediaType == mimeType
        })
    }

    @Test
    fun `retrySendMediaMessage() should update local cache with send status SENDING on failure`() = runBlockingTest {
        val messageUuid = "uuid"
        val mediaUri = "uri"
        val fileName = "fileName"
        val mimeType = "mimeType"
        val message = createTestMessageDataItem(uuid = messageUuid, author = memberIdentity,
            sendStatus = SendStatus.ERROR.value, mediaUploadUri = mediaUri, mediaFileName = fileName,
            mediaType = mimeType, type = Message.Type.MEDIA.value, mediaSid = "sid")
        coEvery { member.sid } returns message.memberSid
        coEvery { messages.sendMessage(any()) } returns message.toMessageMock(member)
        coEvery { channels.getChannel(any()) } throws ChatException(ChatError.MESSAGE_SEND_FAILED)
        whenCall(chatRepository.getMessageByUuid(message.uuid)).thenReturn(message)
        try {
            channelManager.retrySendMediaMessage(mockk(), message.uuid)
        } catch (e: ChatException) {
            assert(ChatError.MESSAGE_SEND_FAILED == e.error)
        }

        verify(chatRepository).updateMessageByUuid(argThat {
            type == Message.Type.MEDIA.value
                    && body == ""
                    && uuid == messageUuid
                    && sendStatus == SendStatus.SENDING.value
                    && mediaFileName == fileName
                    && mediaType == mimeType
        })
    }

    @Test
    fun `addRemoveReaction() should add new reaction if not already added`() = testDispatcher.runBlockingTest {
        val messageIndex = 1L
        val message = mockk<Message>()
        var mockAttributes = Attributes()
        val reactions: MutableMap<String, Set<String>> = mutableMapOf()
        val expectedReactions = Reaction.values().toList().getExpectedReactions(listOf(memberIdentity))
        var actualReactions = ReactionAttributes()

        coEvery { message.memberSid } returns memberIdentity
        coEvery { member.sid } returns message.memberSid
        coEvery { messages.getMessageByIndex(any()) } returns message
        coEvery { message.attributes } returns mockAttributes
        coEvery { message.setAttributes(any()) } answers {
            val attributes = getReactions("${args[1]}")
            reactions.putAll(attributes)
            actualReactions = ReactionAttributes(reactions)
            mockAttributes = Attributes(JSONObject(Gson().toJson(actualReactions)))
            Unit
        }
        Reaction.values().forEach { reaction ->
            channelManager.addRemoveReaction(messageIndex, reaction)
        }

        assertEquals(expectedReactions, actualReactions)
    }

    @Test
    fun `addRemoveReaction() should update reaction counter if already added but not by self user`() = runBlockingTest {
        val existingMember = "existing_member"
        val messageIndex = 1L
        val message = mockk<Message>()
        val reactions: MutableMap<String, Set<String>> = mutableMapOf()
        val initialReactions = listOf(Reaction.HEART).getExpectedReactions(listOf(existingMember))
        val expectedReactions = listOf(Reaction.HEART).getExpectedReactions(listOf(existingMember, memberIdentity))
        var mockAttributes = Attributes(JSONObject(Gson().toJson(initialReactions)))
        var actualReactions = ReactionAttributes()

        coEvery { message.memberSid } returns memberIdentity
        coEvery { member.sid } returns message.memberSid
        coEvery { messages.getMessageByIndex(any()) } returns message
        coEvery { message.attributes } returns mockAttributes
        coEvery { message.setAttributes(any()) } answers {
            val attributes = getReactions("${args[1]}")
            reactions.putAll(attributes)
            actualReactions = ReactionAttributes(reactions)
            mockAttributes = Attributes(JSONObject(Gson().toJson(actualReactions)))
            Unit
        }
        channelManager.addRemoveReaction(messageIndex, Reaction.HEART)
        assertEquals(expectedReactions, actualReactions)
    }

    @Test
    fun `addRemoveReaction() should remove reaction if added by self user`() = runBlockingTest {
        val existingMember = "existing_member"
        val messageIndex = 1L
        val message = mockk<Message>()
        val reactions: MutableMap<String, Set<String>> = mutableMapOf()
        val initialReactions = listOf(Reaction.HEART).getExpectedReactions(listOf(existingMember, memberIdentity))
        val expectedReactions = listOf(Reaction.HEART).getExpectedReactions(listOf(existingMember))
        var mockAttributes = Attributes(JSONObject(Gson().toJson(initialReactions)))
        var actualReactions = ReactionAttributes()

        coEvery { message.memberSid } returns memberIdentity
        coEvery { member.sid } returns message.memberSid
        coEvery { messages.getMessageByIndex(any()) } returns message
        coEvery { message.attributes } returns mockAttributes
        coEvery { message.setAttributes(any()) } answers {
            val attributes = getReactions("${args[1]}")
            reactions.putAll(attributes)
            actualReactions = ReactionAttributes(reactions)
            mockAttributes = Attributes(JSONObject(Gson().toJson(actualReactions)))
            Unit
        }
        channelManager.addRemoveReaction(messageIndex, Reaction.HEART)
        assertEquals(expectedReactions, actualReactions)
    }

    @Test
    fun `addRemoveReaction() should not be updated on error`() = runBlockingTest {
        val messageIndex = 1L
        val message = mockk<Message>()

        coEvery { message.memberSid } returns memberIdentity
        coEvery { member.sid } returns message.memberSid
        coEvery { messages.getMessageByIndex(any()) } returns message
        coEvery { channels.getChannel(any()) } throws ChatException(ChatError.REACTION_UPDATE_FAILED)
        try {
            channelManager.addRemoveReaction(messageIndex, Reaction.HEART)
        } catch (e: ChatException) {
            assert(ChatError.REACTION_UPDATE_FAILED == e.error)
        }
    }

    @Test
    fun `setMessageMediaDownloadId should update repository`() = runBlockingTest {
        val messageIndex = 1L
        val downloadId = 2L
        val messageSid = "sid"
        val message = mockk<Message>()
        every { message.sid } returns messageSid
        coEvery { messages.getMessageByIndex(messageIndex) } returns message

        channelManager.setMessageMediaDownloadId(messageIndex, downloadId)

        verify { chatRepository.updateMessageMediaDownloadStatus(messageSid = message.sid, downloadId = downloadId)}
    }

    @Test
    fun `getMediaContentTemporaryUrl returns Media getContentTemporaryUrl`() = runBlockingTest {
        val messageIndex = 1L
        val mediaTempUrl = "url"
        val message = mockk<Message>()
        val media = mockk<Message.Media>()
        coEvery { media.getContentTemporaryUrl() } returns mediaTempUrl
        every { message.media } returns media
        coEvery { messages.getMessageByIndex(messageIndex) } returns message

        assertEquals(mediaTempUrl, channelManager.getMediaContentTemporaryUrl(messageIndex))
    }
}
