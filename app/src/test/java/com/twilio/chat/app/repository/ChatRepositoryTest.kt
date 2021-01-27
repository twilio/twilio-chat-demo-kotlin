@file:Suppress("IncorrectScope")

package com.twilio.chat.app.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.twilio.chat.*
import com.twilio.chat.app.*
import com.twilio.chat.app.common.*
import com.twilio.chat.app.common.enums.ChatError.UNKNOWN
import com.twilio.chat.app.common.extensions.*
import com.twilio.chat.app.data.ChatClientWrapper
import com.twilio.chat.app.data.localCache.LocalCacheProvider
import com.twilio.chat.app.data.localCache.entity.MemberDataItem
import com.twilio.chat.app.data.models.RepositoryRequestStatus
import com.twilio.chat.app.data.models.RepositoryRequestStatus.FETCHING
import com.twilio.chat.app.data.models.RepositoryRequestStatus.COMPLETE
import com.twilio.chat.app.data.models.RepositoryRequestStatus.SUBSCRIBING
import com.twilio.chat.app.testUtil.*
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(
    ChatClient::class,
    Channels::class,
    Channel::class,
    Member::class,
    Message::class,
    UserDescriptor::class
)
class ChatRepositoryTest {

    private val testDispatcher: TestCoroutineDispatcher = TestCoroutineDispatcher()

    @Rule
    var coroutineTestRule = CoroutineTestRule(testDispatcher)

    @Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var chatRepository: ChatRepositoryImpl

    @RelaxedMockK
    private lateinit var localCacheProvider: LocalCacheProvider

    @MockK
    private lateinit var chatClientWrapper: ChatClientWrapper

    @MockK
    private lateinit var channels: Channels

    @MockK
    private lateinit var messages: Messages
    private lateinit var clientListener: ChatClientListener

    private val myIdentity = "test_id"

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        mockkStatic("com.twilio.chat.app.common.extensions.TwilioExtensionsKt")
        mockkStatic("com.twilio.chat.app.common.extensions.PaginatorExtensionsKt")
        mockkStatic("com.twilio.chat.app.common.DataConverterKt")

        // MockK cannot mock native jni methods for now. So we have to use PowerMockito for that.
        val chatClient = PowerMockito.mock(ChatClient::class.java)
        whenCall(chatClient.channels).thenReturn(channels)
        whenCall(chatClient.myIdentity).thenReturn(myIdentity)
        whenCall(chatClient.addListener(any())).then { clientListener = it.arguments[0] as ChatClientListener; Unit }

        coEvery { channels.getChannel(any()).messages } returns messages
        coEvery { chatClientWrapper.getChatClient() } returns chatClient

        chatRepository = ChatRepositoryImpl(chatClientWrapper, localCacheProvider, coroutineTestRule.testDispatcherProvider)
    }

    @Test
    fun `getUserChannels() should return statuses in correct order`() = runBlocking {
        every { localCacheProvider.channelsDao().getUserChannels() } returns flowOf(emptyList())
        coEvery { channels.getUserChannelsList().requestAllItems().toChannelDataItemList() } returns emptyList()

        val actual = chatRepository.getUserChannels().toList().map { it.requestStatus }
        val expected = listOf(FETCHING, SUBSCRIBING, COMPLETE)

        assertEquals(expected, actual)
    }

    @Test
    fun `getUserChannels() first should return user channels stored in local cache`() = runBlocking {
        val expectedChannels = getMockedChannels(USER_CHANNEL_COUNT, "User Channels").toList()

        every { localCacheProvider.channelsDao().getUserChannels() } returns flowOf(expectedChannels)
        coEvery { channels.getUserChannelsList().requestAllItems().toChannelDataItemList() } returns emptyList()

        val actualChannels = chatRepository.getUserChannels().first().data

        assertEquals(expectedChannels, actualChannels)
    }

    @Test
    fun `getUserChannels() should fetch channels and store them in local cache`() = runBlocking {
        val expectedChannel = createTestChannelDataItem()

        every { localCacheProvider.channelsDao().getUserChannels() } returns flowOf(emptyList())
        coEvery { channels.getUserChannelsList().requestAllItems().toChannelDataItemList() } returns listOf(expectedChannel)
        coEvery { channels.getChannel(any()) } returns expectedChannel.toChannelMock()

        val actualStatus = chatRepository.getUserChannels().toList().last().requestStatus

        assertEquals(COMPLETE, actualStatus)
        verify { localCacheProvider.channelsDao().insert(expectedChannel) }
    }

    @Test
    fun `getUserChannels() should delete outdated channels from local cache`() = runBlocking {
        val expectedChannels = getMockedChannels(USER_CHANNEL_COUNT, "User Channels").toList()

        every { localCacheProvider.channelsDao().getUserChannels() } returns flowOf(expectedChannels)
        coEvery { channels.getUserChannelsList().requestAllItems().toChannelDataItemList() } returns emptyList()

        val actualStatus = chatRepository.getUserChannels().toList().last().requestStatus

        assertEquals(COMPLETE, actualStatus)
        verify { localCacheProvider.channelsDao().deleteGoneUserChannels(emptyList()) }
        confirmVerified(localCacheProvider)
    }

    @Test
    fun `getUserChannels() should return error if cannot fetch channel descriptors`() = runBlocking {
        every { localCacheProvider.channelsDao().getUserChannels() } returns flowOf(emptyList())
        coEvery { channels.getUserChannelsList().requestAllItems().toChannelDataItemList() } throws ChatException(UNKNOWN)

        val actualStatus = chatRepository.getUserChannels().toList().last().requestStatus
        assertEquals(UNKNOWN, (actualStatus as RepositoryRequestStatus.Error).error)
    }

    @Test
    fun `getUserChannels() should return error if cannot fetch channel`() = runBlocking {
        val expectedChannel = createTestChannelDataItem()

        every { localCacheProvider.channelsDao().getUserChannels() } returns flowOf(emptyList())
        coEvery { channels.getUserChannelsList().requestAllItems().toChannelDataItemList() } returns listOf(expectedChannel)
        coEvery { channels.getChannel(any()) } throws ChatException(UNKNOWN)

        val actualStatus = chatRepository.getUserChannels().toList().last().requestStatus
        assertEquals(UNKNOWN, (actualStatus as RepositoryRequestStatus.Error).error)
    }

    @Test
    fun `getPublicChannels() should return statuses in correct order`() = runBlocking {
        every { localCacheProvider.channelsDao().getPublicChannels() } returns flowOf(emptyList())
        coEvery { channels.getPublicChannelsList().requestAllItems().toChannelDataItemList() } returns emptyList()

        val actual = chatRepository.getPublicChannels().toList().map { it.requestStatus }
        val expected = listOf(FETCHING, SUBSCRIBING, COMPLETE)

        assertEquals(expected, actual)
    }

    @Test
    fun `getPublicChannels() first should return user channels stored in local cache`() = runBlocking {
        val expectedChannels = getMockedChannels(PUBLIC_CHANNEL_COUNT, "Public Channels").toList()

        every { localCacheProvider.channelsDao().getPublicChannels() } returns flowOf(expectedChannels)
        coEvery { channels.getPublicChannelsList().requestAllItems().toChannelDataItemList() } returns emptyList()

        val actualChannels = chatRepository.getPublicChannels().first().data

        assertEquals(expectedChannels, actualChannels)
    }

    @Test
    fun `getPublicChannels() should fetch channels and store them in local cache`() = runBlocking {
        val expectedChannel = createTestChannelDataItem()

        every { localCacheProvider.channelsDao().getPublicChannels() } returns flowOf(emptyList())
        coEvery { channels.getPublicChannelsList().requestAllItems().toChannelDataItemList() } returns listOf(expectedChannel)
        coEvery { channels.getChannel(any()) } returns expectedChannel.toChannelMock()

        val actualStatus = chatRepository.getPublicChannels().toList().last().requestStatus

        assertEquals(COMPLETE, actualStatus)
        verify { localCacheProvider.channelsDao().insert(expectedChannel) }
    }

    @Test
    fun `getPublicChannels() should delete outdated channels from local cache`() = runBlocking {
        val expectedChannels = getMockedChannels(PUBLIC_CHANNEL_COUNT, "Public Channels").toList()

        every { localCacheProvider.channelsDao().getPublicChannels() } returns flowOf(expectedChannels)
        coEvery { channels.getPublicChannelsList().requestAllItems().toChannelDataItemList() } returns emptyList()

        val actualStatus = chatRepository.getPublicChannels().toList().last().requestStatus

        assertEquals(COMPLETE, actualStatus)
        verify { localCacheProvider.channelsDao().deleteGonePublicChannels(emptyList()) }
        confirmVerified(localCacheProvider)
    }

    @Test
    fun `getPublicChannels() should return error if cannot fetch channel descriptors`() = runBlocking {
        every { localCacheProvider.channelsDao().getPublicChannels() } returns flowOf(emptyList())
        coEvery { channels.getPublicChannelsList().requestAllItems().toChannelDataItemList() } throws ChatException(UNKNOWN)

        val actualStatus = chatRepository.getPublicChannels().toList().last().requestStatus
        assertEquals(UNKNOWN, (actualStatus as RepositoryRequestStatus.Error).error)
    }

    @Test
    fun `getPublicChannels() should return error if cannot fetch channel`() = runBlocking {
        val expectedChannel = createTestChannelDataItem()

        every { localCacheProvider.channelsDao().getPublicChannels() } returns flowOf(emptyList())
        coEvery { channels.getPublicChannelsList().requestAllItems().toChannelDataItemList() } returns listOf(expectedChannel)
        coEvery { channels.getChannel(any()) } throws ChatException(UNKNOWN)

        val actualStatus = chatRepository.getPublicChannels().toList().last().requestStatus
        assertEquals(UNKNOWN, (actualStatus as RepositoryRequestStatus.Error).error)
    }

    @Test
    fun `onChannelDeleted should remove received Channel from local cache when called`() = runBlocking {
        val channel = createTestChannelDataItem().toChannelMock()

        clientListener.onChannelDeleted(channel)

        verify(timeout = 10_000) { localCacheProvider.channelsDao().delete(channel.sid) }
        confirmVerified(localCacheProvider)
    }

    @Test
    fun `onChannelAdded should add received Channel to local cache when called`() = runBlocking {
        val channel = createTestChannelDataItem()
        coEvery { channels.getChannel(any()) } returns channel.toChannelMock()

        clientListener.onChannelAdded(channel.toChannelMock())

        verify(timeout = 10_000) { localCacheProvider.channelsDao().insert(channel) }
        verify(timeout = 10_000) { localCacheProvider.channelsDao().update(channel.sid, channel.type,
            channel.participatingStatus, channel.notificationLevel, channel.friendlyName) }
        confirmVerified(localCacheProvider)
    }

    @Test
    fun `onChannelUpdated should update received Channel in local cache when called`() = runBlocking {
        val channel = createTestChannelDataItem()
        coEvery { channels.getChannel(any()) } returns channel.toChannelMock()

        clientListener.onChannelUpdated(channel.toChannelMock(), Channel.UpdateReason.ATTRIBUTES)

        verify(timeout = 10_000) { localCacheProvider.channelsDao().insert(channel) }
        verify(timeout = 10_000) { localCacheProvider.channelsDao().update(channel.sid, channel.type,
            channel.participatingStatus, channel.notificationLevel, channel.friendlyName) }
        confirmVerified(localCacheProvider)
    }

    @Test
    fun `onChannelJoined should update received Channel in local cache when called`() = runBlocking {
        val channel = createTestChannelDataItem()
        coEvery { channels.getChannel(any()) } returns channel.toChannelMock()

        clientListener.onChannelJoined(channel.toChannelMock())

        verify(timeout = 10_000) { localCacheProvider.channelsDao().insert(channel) }
        verify(timeout = 10_000) { localCacheProvider.channelsDao().update(channel.sid, channel.type,
            channel.participatingStatus, channel.notificationLevel, channel.friendlyName) }
        confirmVerified(localCacheProvider)
    }

    @Test
    fun `getMessages() should return statuses in correct order`() = runBlocking {
        every { localCacheProvider.messagesDao().getMessagesSorted(any()) } returns ItemDataSource.factory(emptyList())
        coEvery { messages.getLastMessages(any()).asMessageDataItems(any()) } returns emptyList()
        val expected = listOf(FETCHING, COMPLETE)

        assertEquals(expected, chatRepository.getMessages("", 1).take(2).toList().map { it.requestStatus })
    }

    @Test
    fun `getMessages() first should return messages stored in local cache`() = runBlocking {
        val channelSid = "channel_1"
        val expectedMessages = getMockedMessages(MESSAGE_COUNT, "Message body", channelSid)

        every { localCacheProvider.messagesDao().getMessagesSorted(channelSid) } returns ItemDataSource.factory(expectedMessages)

        val actualChannels = chatRepository.getMessages(channelSid, MESSAGE_COUNT).first().data

        assertEquals(expectedMessages.asMessageListViewItems(), actualChannels)
    }

    @Test
    fun `getMessages() should fetch messages and store them in local cache`() = runBlocking {
        val channelSid = "channel_1"
        val expectedMessage = createTestMessageDataItem(channelSid = channelSid)

        every { localCacheProvider.messagesDao().getMessagesSorted(any()) } returns ItemDataSource.factory(emptyList())
        coEvery { messages.getLastMessages(any()).asMessageDataItems(any()) } returns listOf(expectedMessage)

        chatRepository.getMessages(channelSid, MESSAGE_COUNT).first { it.requestStatus is COMPLETE }

        verify { localCacheProvider.messagesDao().insert(listOf(expectedMessage)) }
    }

    @Test
    fun `getMessages() should return error if cannot fetch channel descriptors`() = runBlocking {
        every { localCacheProvider.messagesDao().getMessagesSorted(any()) } returns ItemDataSource.factory(emptyList())
        coEvery { messages.getLastMessages(any()).asMessageDataItems(any()) } throws ChatException(UNKNOWN)

        val actualStatus = chatRepository.getMessages("channelSid", MESSAGE_COUNT)
            .first { it.requestStatus is RepositoryRequestStatus.Error }.requestStatus

        assertEquals(UNKNOWN, (actualStatus as RepositoryRequestStatus.Error).error)
    }

    @Test
    fun `getMessages() should return error if cannot fetch channel`() = runBlocking {
        val channelSid = "channel_1"
        val expectedMessage = createTestMessageDataItem(channelSid = channelSid)

        every { localCacheProvider.messagesDao().getMessagesSorted(any())} returns ItemDataSource.factory(emptyList())
        coEvery { messages.getLastMessages(any()).asMessageDataItems(any()) } returns listOf(expectedMessage)
        coEvery { channels.getChannel(any()) } throws ChatException(UNKNOWN)

        val actualStatus = chatRepository.getMessages(channelSid, MESSAGE_COUNT)
            .first { it.requestStatus is RepositoryRequestStatus.Error }.requestStatus
        assertEquals(UNKNOWN, (actualStatus as RepositoryRequestStatus.Error).error)
    }

    @Test
    fun `getTypingMemebers should return data from LocalCache`() = runBlocking {
        val channelSid = "123"
        val typingMembers = listOf(MemberDataItem(channelSid = channelSid, identity = "asd", sid = "321",
            lastConsumedMessageIndex = null, lastConsumptionTimestamp = null, friendlyName = "user", isOnline = true))
        every { localCacheProvider.membersDao().getTypingMembers(channelSid) } returns flowOf(typingMembers)

        assertEquals(typingMembers, chatRepository.getTypingMembers(channelSid).first())
    }

    @Test
    fun `member typing status updated via ChannelManagerListener`() = runBlocking {
        // Set up a ChatRepository and capture the ChannelManagerListener that's added to joined channels
        val chatClient = PowerMockito.mock(ChatClient::class.java)
        val channels = PowerMockito.mock(Channels::class.java)
        val listenerSlot = slot<ChannelListener>()
        whenCall(chatClient.channels).thenReturn(channels)
        coEvery { chatClientWrapper.getChatClient() } returns chatClient

        val channel = mockk<Channel>()
        coEvery { channels.getChannel(any()) } returns channel
        every { channel.addListener(capture(listenerSlot)) } answers { }
        every { channel.friendlyName } returns ""
        every { channel.sid } returns "123"

        chatRepository = ChatRepositoryImpl(chatClientWrapper, localCacheProvider)
        clientListener.onChannelAdded(channel)

        val member = PowerMockito.mock(Member::class.java)
        val userDescriptor = PowerMockito.mock(UserDescriptor::class.java)
        whenCall(userDescriptor.identity).thenReturn("User")
        whenCall(userDescriptor.isOnline).thenReturn(true)
        whenCall(member.sid).thenReturn("321")
        whenCall(member.channel).thenReturn(channel)
        whenCall(member.identity).thenReturn("asd")
        coEvery { member.getUserDescriptor() } returns userDescriptor
        val memberDataItemTyping = member.asMemberDataItem(typing = true, userDescriptor = userDescriptor)
        val memberDataItemNotTyping = member.asMemberDataItem(typing = false, userDescriptor = userDescriptor)

        // When calling ChannelListener.onTypingStarted(..)
        listenerSlot.captured.onTypingStarted(channel, member)

        // Then the local cache is updated with that member
        verify { localCacheProvider.membersDao().insertOrReplace(memberDataItemTyping) }

        // When calling ChannelListener.onTypingEnded(..)
        listenerSlot.captured.onTypingEnded(channel, member)

        // Then the local cache is updated with that member
        verify { localCacheProvider.membersDao().insertOrReplace(memberDataItemNotTyping) }
    }

    @Test
    fun `message deleted via ChannelListener`() = testDispatcher.runBlockingTest {
        val channelListenerCaptor = ArgumentCaptor.forClass(ChannelListener::class.java)
        val channel = createTestChannelDataItem().toChannelMock(channelListenerCaptor = channelListenerCaptor)
        val member = createTestMemberDataItem().toMemberMock(channel)
        val message = createTestMessageDataItem().toMessageMock(member)
        val expectedMessage = message.toMessageDataItem(currentUserIdentity = myIdentity)
        prepareChatRepository(channel)

        channelListenerCaptor.value.onMessageDeleted(message)

        verify(timeout = 10_000) { localCacheProvider.messagesDao().delete(expectedMessage) }
    }

    @Test
    fun `message updated via ChannelListener`() = testDispatcher.runBlockingTest {
        val channelListenerCaptor = ArgumentCaptor.forClass(ChannelListener::class.java)
        val channel = createTestChannelDataItem().toChannelMock(channelListenerCaptor = channelListenerCaptor)
        val member = createTestMemberDataItem().toMemberMock(channel)
        val message = createTestMessageDataItem().toMessageMock(member)
        val expectedMessage = message.toMessageDataItem(currentUserIdentity = myIdentity)
        prepareChatRepository(channel)

        channelListenerCaptor.value.onMessageUpdated(message, Message.UpdateReason.BODY)

        verify(timeout = 10_000) { localCacheProvider.messagesDao().insertOrReplace(expectedMessage) }
    }

    @Test
    fun `message added via ChannelListener`() = testDispatcher.runBlockingTest {
        val channelListenerCaptor = ArgumentCaptor.forClass(ChannelListener::class.java)
        val channel = createTestChannelDataItem().toChannelMock(channelListenerCaptor = channelListenerCaptor)
        val member = createTestMemberDataItem().toMemberMock(channel)
        val message = createTestMessageDataItem().toMessageMock(member)
        val expectedMessage = message.toMessageDataItem(currentUserIdentity = myIdentity)
        prepareChatRepository(channel)

        channelListenerCaptor.value.onMessageAdded(message)

        verify(timeout = 10_000) { localCacheProvider.messagesDao().updateByUuidOrInsert(expectedMessage) }
    }

    private fun prepareChatRepository(channel: Channel) {
        // Set up a ChatRepository with a single channel
        val chatClient = PowerMockito.mock(ChatClient::class.java)
        val channels = PowerMockito.mock(Channels::class.java)
        whenCall(chatClient.channels).thenReturn(channels)
        whenCall(chatClient.myIdentity).thenReturn(myIdentity)
        whenCall(chatClient.addListener(any())).then { clientListener = it.arguments[0] as ChatClientListener; Unit }
        coEvery { chatClientWrapper.getChatClient() } returns chatClient

        coEvery { channels.getChannel(any()) } returns channel
        chatRepository = ChatRepositoryImpl(chatClientWrapper, localCacheProvider,
            coroutineTestRule.testDispatcherProvider)
        clientListener.onChannelAdded(channel)
    }
}
