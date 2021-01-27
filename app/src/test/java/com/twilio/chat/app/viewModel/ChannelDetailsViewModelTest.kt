package com.twilio.chat.app.viewModel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.twilio.chat.Channel
import com.twilio.chat.app.*
import com.twilio.chat.app.common.asChannelDetailsViewItem
import com.twilio.chat.app.common.enums.ChatError
import com.twilio.chat.app.common.extensions.ChatException
import com.twilio.chat.app.data.localCache.entity.ChannelDataItem
import com.twilio.chat.app.data.models.RepositoryRequestStatus
import com.twilio.chat.app.data.models.RepositoryResult
import com.twilio.chat.app.manager.ChannelListManager
import com.twilio.chat.app.manager.MemberListManager
import com.twilio.chat.app.repository.ChatRepository
import com.twilio.chat.app.testUtil.*
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import junit.framework.TestCase.*
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
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(
    Channel::class
)
class ChannelDetailsViewModelTest {

    @Rule
    var coroutineTestRule = CoroutineTestRule()

    @Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val memberIdentity = "memberIdentity"
    private val channelSid = "channelSid"
    private val channelName = "Test Channel"
    private val channelCreator = "User 01"
    private val channel: ChannelDataItem? = createTestChannelDataItem(sid = channelSid, friendlyName = channelName,
        createdBy = channelCreator)

    @MockK
    private lateinit var chatRepository: ChatRepository

    @MockK
    private lateinit var channelListManager: ChannelListManager

    @MockK
    private lateinit var memberListManager: MemberListManager

    private lateinit var channelDetailsViewModel: ChannelDetailsViewModel

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(Dispatchers.Default)
        coEvery { chatRepository.getChannel(any()) } returns
                flowOf(RepositoryResult(channel, RepositoryRequestStatus.COMPLETE))
        coEvery { chatRepository.getUserChannels() } returns
                flowOf(RepositoryResult(listOf(), RepositoryRequestStatus.COMPLETE))
        coEvery { chatRepository.getPublicChannels() } returns
                flowOf(RepositoryResult(listOf(), RepositoryRequestStatus.COMPLETE))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `channelDetailViewModel_channelDetails should be initialized on init`() = runBlocking {
        channelDetailsViewModel = ChannelDetailsViewModel(channelSid, chatRepository, channelListManager, memberListManager)
        assertEquals(channel?.asChannelDetailsViewItem(), channelDetailsViewModel.channelDetails.waitValue())
    }

    @Test
    fun `channelDetailViewModel_onDetailsError should be called when get channel failed`() = runBlocking {
        coEvery { chatRepository.getChannel(any()) } returns
                flowOf(RepositoryResult(channel, RepositoryRequestStatus.Error(ChatError.CHANNEL_GET_FAILED)))
        channelDetailsViewModel = ChannelDetailsViewModel(channelSid, chatRepository, channelListManager, memberListManager)
        assertTrue(channelDetailsViewModel.onDetailsError.waitValue(ChatError.CHANNEL_GET_FAILED))
    }
    @Test
    fun `channelDetailViewModel_muteChannel() should call onDetailsError on failure`() = runBlocking {
        val channelSid = "sid"
        coEvery { channelListManager.muteChannel(channelSid) } throws ChatException(ChatError.CHANNEL_MUTE_FAILED)

        channelDetailsViewModel = ChannelDetailsViewModel(channelSid, chatRepository, channelListManager, memberListManager)
        channelDetailsViewModel.muteChannel()

        coVerify { channelListManager.muteChannel(channelSid) }
        assertTrue(channelDetailsViewModel.onChannelMuted.waitNotCalled())
        assertTrue(channelDetailsViewModel.onDetailsError.waitValue(ChatError.CHANNEL_MUTE_FAILED))
    }

    @Test
    fun `channelDetailViewModel_muteChannel() should call onChannelJoined on success`() = runBlocking {
        val channelSid = "sid"
        coEvery { channelListManager.muteChannel(channelSid) } returns Unit

        channelDetailsViewModel = ChannelDetailsViewModel(channelSid, chatRepository, channelListManager, memberListManager)
        channelDetailsViewModel.muteChannel()

        coVerify { channelListManager.muteChannel(channelSid) }
        assertTrue(channelDetailsViewModel.onChannelMuted.waitValue(true))
    }

    @Test
    fun `channelDetailViewModel_unmuteChannel() should call onDetailsError on failure`() = runBlocking {
        val channelSid = "sid"
        coEvery { channelListManager.unmuteChannel(channelSid) } throws ChatException(ChatError.CHANNEL_UNMUTE_FAILED)

        channelDetailsViewModel = ChannelDetailsViewModel(channelSid, chatRepository, channelListManager, memberListManager)
        channelDetailsViewModel.unmuteChannel()

        coVerify { channelListManager.unmuteChannel(channelSid) }
        assertTrue(channelDetailsViewModel.onChannelMuted.waitNotCalled())
        assertTrue(channelDetailsViewModel.onDetailsError.waitValue(ChatError.CHANNEL_UNMUTE_FAILED))
    }

    @Test
    fun `channelDetailViewModel_unmuteChannel() should call onChannelJoined on success`() = runBlocking {
        val channelSid = "sid"
        coEvery { channelListManager.unmuteChannel(channelSid) } returns Unit

        channelDetailsViewModel = ChannelDetailsViewModel(channelSid, chatRepository, channelListManager, memberListManager)
        channelDetailsViewModel.unmuteChannel()

        coVerify { channelListManager.unmuteChannel(channelSid) }
        assertTrue(channelDetailsViewModel.onChannelMuted.waitValue(false))
    }

    @Test
    fun `channelDetailViewModel_removeChannel() should call onChannelRemoved on success`() = runBlocking {
        val channelSid = "sid"
        coEvery { channelListManager.removeChannel(channelSid) } returns Unit

        channelDetailsViewModel = ChannelDetailsViewModel(channelSid, chatRepository, channelListManager, memberListManager)
        channelDetailsViewModel.removeChannel()

        coVerify { channelListManager.removeChannel(channelSid) }
        assertTrue(channelDetailsViewModel.onChannelRemoved.waitCalled())
    }

    @Test
    fun `channelDetailViewModel_removeChannel() should call onDetailsError on failure`() = runBlocking {
        val channelSid = "sid"
        coEvery { channelListManager.removeChannel(channelSid) } throws ChatException(ChatError.CHANNEL_REMOVE_FAILED)

        channelDetailsViewModel = ChannelDetailsViewModel(channelSid, chatRepository, channelListManager, memberListManager)
        channelDetailsViewModel.removeChannel()

        coVerify { channelListManager.removeChannel(channelSid) }
        assertTrue(channelDetailsViewModel.onChannelRemoved.waitNotCalled())
        assertTrue(channelDetailsViewModel.onDetailsError.waitValue(ChatError.CHANNEL_REMOVE_FAILED))
    }

    @Test
    fun `channelDetailViewModel_renameChannel() should call onChannelRenamed on success`() = runBlocking {
        val channelSid = "sid"
        val friendlyName = channelName + "2"
        coEvery { channelListManager.renameChannel(channelSid, friendlyName) } returns Unit

        channelDetailsViewModel = ChannelDetailsViewModel(channelSid, chatRepository, channelListManager, memberListManager)
        channelDetailsViewModel.renameChannel(friendlyName)

        coVerify { channelListManager.renameChannel(channelSid, friendlyName) }
        assertTrue(channelDetailsViewModel.onChannelRenamed.waitCalled())
    }

    @Test
    fun `channelDetailViewModel_renameChannel() should call onDetailsError on failure`() = runBlocking {
        val channelSid = "sid"
        val friendlyName = channelName + "2"
        coEvery { channelListManager.renameChannel(channelSid, friendlyName) } throws ChatException(ChatError.CHANNEL_RENAME_FAILED)

        channelDetailsViewModel = ChannelDetailsViewModel(channelSid, chatRepository, channelListManager, memberListManager)
        channelDetailsViewModel.renameChannel(friendlyName)

        coVerify { channelListManager.renameChannel(channelSid, friendlyName) }
        assertTrue(channelDetailsViewModel.onChannelRenamed.waitNotCalled())
        assertTrue(channelDetailsViewModel.onDetailsError.waitValue(ChatError.CHANNEL_RENAME_FAILED))
    }

    @Test
    fun `channelDetailViewModel_addMember() should call onMemberAdded on success`() = runBlocking {
        coEvery { chatRepository.getChannelMembers(any()) } returns
                flowOf(RepositoryResult(listOf(), RepositoryRequestStatus.COMPLETE))
        coEvery { memberListManager.addMember(memberIdentity) } returns Unit

        val channelDetailViewModel = ChannelDetailsViewModel(channelSid, chatRepository, channelListManager, memberListManager)
        channelDetailViewModel.addMember(memberIdentity)

        coVerify { memberListManager.addMember(memberIdentity) }
        assertTrue(channelDetailViewModel.onMemberAdded.waitCalled())
    }

    @Test
    fun `channelDetailViewModel_addMember() should call onMemberError on failure`() = runBlocking {
        coEvery { chatRepository.getChannelMembers(any()) } returns
                flowOf(RepositoryResult(listOf(), RepositoryRequestStatus.COMPLETE))
        coEvery { memberListManager.addMember(memberIdentity) } throws ChatException(ChatError.MEMBER_ADD_FAILED)

        val channelDetailViewModel = ChannelDetailsViewModel(channelSid, chatRepository, channelListManager, memberListManager)
        channelDetailViewModel.addMember(memberIdentity)

        coVerify { memberListManager.addMember(memberIdentity) }
        assertTrue(channelDetailViewModel.onDetailsError.waitValue(ChatError.MEMBER_ADD_FAILED))
    }
}
