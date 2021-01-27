package com.twilio.chat.app.viewModel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.twilio.chat.Channel
import com.twilio.chat.User
import com.twilio.chat.app.PUBLIC_CHANNEL_COUNT
import com.twilio.chat.app.USER_CHANNEL_COUNT
import com.twilio.chat.app.common.asChannelListViewItems
import com.twilio.chat.app.common.enums.ChatError
import com.twilio.chat.app.common.extensions.ChatException
import com.twilio.chat.app.createTestChannelDataItem
import com.twilio.chat.app.data.models.RepositoryRequestStatus
import com.twilio.chat.app.data.models.RepositoryResult
import com.twilio.chat.app.getMockedChannels
import com.twilio.chat.app.manager.ChannelListManager
import com.twilio.chat.app.manager.LoginManager
import com.twilio.chat.app.manager.UserManager
import com.twilio.chat.app.repository.ChatRepository
import com.twilio.chat.app.testUtil.*
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(
    Channel::class,
    User::class
)
class ChannelListViewModelTest {

    @Rule
    var coroutineTestRule = CoroutineTestRule()

    @Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var chatRepository: ChatRepository

    @MockK
    private lateinit var channelListManager: ChannelListManager

    @MockK
    private lateinit var userManager: UserManager

    @MockK
    private lateinit var loginManager: LoginManager

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        coEvery { chatRepository.getUserChannels() } returns
                flowOf(RepositoryResult(listOf(), RepositoryRequestStatus.COMPLETE))
        coEvery { chatRepository.getPublicChannels() } returns
                flowOf(RepositoryResult(listOf(), RepositoryRequestStatus.COMPLETE))
        coEvery { chatRepository.getSelfUser() } returns flowOf(createUserMock())
    }

    @Test
    fun `channelListViewModel_userChannelDataItems() should contain all user channels stored in local cache`() = runBlocking {
        val expectedChannels = getMockedChannels(USER_CHANNEL_COUNT, "User Channels").toList()

        coEvery { chatRepository.getUserChannels() } returns
                flowOf(RepositoryResult(expectedChannels, RepositoryRequestStatus.COMPLETE))

        val channelListViewModel = ChannelListViewModel(chatRepository, channelListManager, userManager, loginManager)
        assertTrue(channelListViewModel.userChannelItems.waitValue(expectedChannels.asChannelListViewItems()))
        assertEquals(USER_CHANNEL_COUNT, channelListViewModel.userChannelItems.waitValue().size)
    }

    @Test
    fun `channelListViewModel_filteredUserChannelItems should contain only filtered items`() = runBlocking {
        val channelAbc = createTestChannelDataItem(friendlyName = "abc")
        val channelBcd = createTestChannelDataItem(friendlyName = "bcd")
        val channelCde = createTestChannelDataItem(friendlyName = "cde")
        val expectedChannels = listOf(channelAbc, channelBcd, channelCde)
        coEvery { chatRepository.getUserChannels() } returns
                flowOf(RepositoryResult(expectedChannels, RepositoryRequestStatus.COMPLETE))
        val channelListViewModel = ChannelListViewModel(chatRepository, channelListManager, userManager, loginManager)

        channelListViewModel.channelFilter = "c"
        assertEquals(3, channelListViewModel.userChannelItems.waitValue().size)
        assertTrue(channelListViewModel.userChannelItems.waitValue(expectedChannels.asChannelListViewItems()))

        channelListViewModel.channelFilter = "b"
        assertEquals(2, channelListViewModel.userChannelItems.waitValue().size)
        assertTrue(channelListViewModel.userChannelItems.waitValue(listOf(channelAbc, channelBcd).asChannelListViewItems()))

        channelListViewModel.channelFilter = "a"
        assertEquals(1, channelListViewModel.userChannelItems.waitValue().size)
        assertTrue(channelListViewModel.userChannelItems.waitValue(listOf(channelAbc).asChannelListViewItems()))

        channelListViewModel.channelFilter = ""
        assertEquals(3, channelListViewModel.userChannelItems.waitValue().size)
        assertTrue(channelListViewModel.userChannelItems.waitValue(expectedChannels.asChannelListViewItems()))
    }

    @Test
    fun `channelListViewModel_filteredUserChannelItems should ignore filter case`() = runBlocking {
        val namePrefix = "User Channels"
        val expectedChannels = getMockedChannels(USER_CHANNEL_COUNT, namePrefix).toList()
        coEvery { chatRepository.getUserChannels() } returns
                flowOf(RepositoryResult(expectedChannels, RepositoryRequestStatus.COMPLETE))
        val channelListViewModel = ChannelListViewModel(chatRepository, channelListManager, userManager, loginManager)

        // When the filter string matches all channel names but is in uppercase
        channelListViewModel.channelFilter = namePrefix.toUpperCase()

        // Then verify that all channels match the filter
        assertEquals(USER_CHANNEL_COUNT, channelListViewModel.userChannelItems.waitValue().size)
        assertTrue(channelListViewModel.userChannelItems.waitValue(expectedChannels.asChannelListViewItems()))
    }

    @Test
    fun `channelListViewModel_publicChannelDataItems() should contain all public channels stored in local cache`() = runBlocking {
        val expectedChannels = getMockedChannels(PUBLIC_CHANNEL_COUNT, "Public Channels").toList()

        coEvery { chatRepository.getPublicChannels() } returns
                flowOf(RepositoryResult(expectedChannels, RepositoryRequestStatus.COMPLETE))

        val channelListViewModel = ChannelListViewModel(chatRepository, channelListManager, userManager, loginManager)
        assertTrue(channelListViewModel.publicChannelItems.waitValue(expectedChannels.asChannelListViewItems()))
        assertEquals(PUBLIC_CHANNEL_COUNT, channelListViewModel.publicChannelItems.waitValue().size)
    }

    @Test
    fun `channelListViewModel_filteredPublicChannelItems should contain only filtered items`() = runBlocking {
        val channelAbc = createTestChannelDataItem(friendlyName = "abc")
        val channelBcd = createTestChannelDataItem(friendlyName = "bcd")
        val channelCde = createTestChannelDataItem(friendlyName = "cde")
        val expectedChannels = listOf(channelAbc, channelBcd, channelCde)
        coEvery { chatRepository.getPublicChannels() } returns
                flowOf(RepositoryResult(expectedChannels, RepositoryRequestStatus.COMPLETE))
        val channelListViewModel = ChannelListViewModel(chatRepository, channelListManager, userManager, loginManager)

        channelListViewModel.channelFilter = "c"
        assertEquals(3, channelListViewModel.publicChannelItems.waitValue().size)
        assertTrue(channelListViewModel.publicChannelItems.waitValue(expectedChannels.asChannelListViewItems()))

        channelListViewModel.channelFilter = "b"
        assertEquals(2, channelListViewModel.publicChannelItems.waitValue().size)
        assertTrue(channelListViewModel.publicChannelItems.waitValue(listOf(channelAbc, channelBcd).asChannelListViewItems()))

        channelListViewModel.channelFilter = "a"
        assertEquals(1, channelListViewModel.publicChannelItems.waitValue().size)
        assertTrue(channelListViewModel.publicChannelItems.waitValue(listOf(channelAbc).asChannelListViewItems()))

        channelListViewModel.channelFilter = ""
        assertEquals(3, channelListViewModel.publicChannelItems.waitValue().size)
        assertTrue(channelListViewModel.publicChannelItems.waitValue(expectedChannels.asChannelListViewItems()))
    }

    @Test
    fun `channelListViewModel_filteredPublicChannelItems should ignore filter case`() = runBlocking {
        val namePrefix = "Public Channels"
        val expectedChannels = getMockedChannels(USER_CHANNEL_COUNT, namePrefix).toList()
        coEvery { chatRepository.getPublicChannels() } returns
                flowOf(RepositoryResult(expectedChannels, RepositoryRequestStatus.COMPLETE))
        val channelListViewModel = ChannelListViewModel(chatRepository, channelListManager, userManager, loginManager)

        // When the filter string matches all channel names but is in uppercase
        channelListViewModel.channelFilter = namePrefix.toUpperCase()

        // Then verify that all channels match the filter
        assertEquals(USER_CHANNEL_COUNT, channelListViewModel.publicChannelItems.waitValue().size)
        assertTrue(channelListViewModel.publicChannelItems.waitValue(expectedChannels.asChannelListViewItems()))
    }

    @Test
    fun `channelListViewModel_userChannelDataItems() should be empty when Error occurred`() = runBlocking {
        coEvery { chatRepository.getUserChannels() } returns
                flowOf(RepositoryResult(listOf(), RepositoryRequestStatus.Error(ChatError.GENERIC_ERROR)))

        val channelListViewModel = ChannelListViewModel(chatRepository, channelListManager, userManager, loginManager)
        assertEquals(0, channelListViewModel.userChannelItems.waitValue().size)
    }

    @Test
    fun `channelListViewModel_publicChannelDataItems() should be empty when Error occurred`() = runBlocking {
        coEvery { chatRepository.getPublicChannels() } returns
                flowOf(RepositoryResult(listOf(), RepositoryRequestStatus.Error(ChatError.GENERIC_ERROR)))

        val channelListViewModel = ChannelListViewModel(chatRepository, channelListManager, userManager, loginManager)
        assertEquals(0, channelListViewModel.publicChannelItems.waitValue().size)
    }

    @Test
    fun `channelListViewModel_createChannel() should call onChannelCreated on success`() = runBlocking {
        val channelName = "Private Channel"
        val channelType = Channel.ChannelType.PRIVATE
        val channelMock = createTestChannelDataItem(
            friendlyName = channelName,
            uniqueName = channelName,
            type = channelType.value
        ).toChannelMock()

        coEvery { channelListManager.createChannel(channelName, channelType)} returns channelMock.sid
        coEvery { channelListManager.joinChannel(channelMock.sid) } returns Unit

        val channelListViewModel = ChannelListViewModel(chatRepository, channelListManager, userManager, loginManager)
        channelListViewModel.createChannel(channelName, channelType)

        assertTrue(channelListViewModel.isDataLoading.waitCalled())
        assertTrue(channelListViewModel.onChannelCreated.waitCalled())
        assertTrue(channelListViewModel.onChannelJoined.waitCalled())
        coVerify { channelListManager.joinChannel(any()) }
        assertTrue(channelListViewModel.isDataLoading.waitCalled())
    }

    @Test
    fun `channelListViewModel_createChannel() should call onChannelError on failure`() = runBlocking {
        val channelName = "Private Channel"
        val channelType = Channel.ChannelType.PRIVATE

        coEvery { channelListManager.createChannel(channelName, channelType)} throws ChatException(ChatError.CHANNEL_CREATE_FAILED)
        coEvery { channelListManager.joinChannel(any()) } returns Unit

        val channelListViewModel = ChannelListViewModel(chatRepository, channelListManager, userManager, loginManager)
        channelListViewModel.createChannel(channelName, channelType)

        assertTrue(channelListViewModel.isDataLoading.waitCalled())
        coVerify(exactly = 0) { channelListManager.joinChannel(any()) }
        assertTrue(channelListViewModel.onChannelCreated.waitNotCalled())
        assertTrue(channelListViewModel.onChannelError.waitValue(ChatError.CHANNEL_CREATE_FAILED))
        assertTrue(channelListViewModel.isDataLoading.waitCalled())
    }

    @Test
    fun `channelListViewModel_joinChannel() should call onChannelJoined on success`() = runBlocking {
        val channelSid = "sid"
        coEvery { channelListManager.joinChannel(channelSid) } returns Unit

        val channelListViewModel = ChannelListViewModel(chatRepository, channelListManager, userManager, loginManager)
        channelListViewModel.joinChannel(channelSid)

        coVerify { channelListManager.joinChannel(channelSid) }
        assertTrue(channelListViewModel.onChannelJoined.waitCalled())
    }

    @Test
    fun `channelListViewModel_joinChannel() should call onChannelError on failure`() = runBlocking {
        val channelSid = "sid"
        coEvery { channelListManager.joinChannel(channelSid) } throws ChatException(ChatError.CHANNEL_JOIN_FAILED)

        val channelListViewModel = ChannelListViewModel(chatRepository, channelListManager, userManager, loginManager)
        channelListViewModel.joinChannel(channelSid)

        coVerify { channelListManager.joinChannel(channelSid) }
        assertTrue(channelListViewModel.onChannelJoined.waitNotCalled())
        assertTrue(channelListViewModel.onChannelError.waitValue(ChatError.CHANNEL_JOIN_FAILED))
    }

    @Test
    fun `channelListViewModel_muteChannel() should call onChannelError on failure`() = runBlocking {
        val channelSid = "sid"
        coEvery { channelListManager.muteChannel(channelSid) } throws ChatException(ChatError.CHANNEL_MUTE_FAILED)

        val channelListViewModel = ChannelListViewModel(chatRepository, channelListManager, userManager, loginManager)
        channelListViewModel.muteChannel(channelSid)

        coVerify { channelListManager.muteChannel(channelSid) }
        assertTrue(channelListViewModel.onChannelMuted.waitNotCalled())
        assertTrue(channelListViewModel.onChannelError.waitValue(ChatError.CHANNEL_MUTE_FAILED))
    }

    @Test
    fun `channelListViewModel_muteChannel() should call onChannelJoined on success`() = runBlocking {
        val channelSid = "sid"
        coEvery { channelListManager.muteChannel(channelSid) } returns Unit

        val channelListViewModel = ChannelListViewModel(chatRepository, channelListManager, userManager, loginManager)
        channelListViewModel.muteChannel(channelSid)

        coVerify { channelListManager.muteChannel(channelSid) }
        assertTrue(channelListViewModel.onChannelMuted.waitValue(true))
    }

    @Test
    fun `channelListViewModel_unmuteChannel() should call onChannelError on failure`() = runBlocking {
        val channelSid = "sid"
        coEvery { channelListManager.unmuteChannel(channelSid) } throws ChatException(ChatError.CHANNEL_UNMUTE_FAILED)

        val channelListViewModel = ChannelListViewModel(chatRepository, channelListManager, userManager, loginManager)
        channelListViewModel.unmuteChannel(channelSid)

        coVerify { channelListManager.unmuteChannel(channelSid) }
        assertTrue(channelListViewModel.onChannelMuted.waitNotCalled())
        assertTrue(channelListViewModel.onChannelError.waitValue(ChatError.CHANNEL_UNMUTE_FAILED))
    }

    @Test
    fun `channelListViewModel_unmuteChannel() should call onChannelJoined on success`() = runBlocking {
        val channelSid = "sid"
        coEvery { channelListManager.unmuteChannel(channelSid) } returns Unit

        val channelListViewModel = ChannelListViewModel(chatRepository, channelListManager, userManager, loginManager)
        channelListViewModel.unmuteChannel(channelSid)

        coVerify { channelListManager.unmuteChannel(channelSid) }
        assertTrue(channelListViewModel.onChannelMuted.waitValue(false))
    }

    @Test
    fun `channelListViewModel_leaveChannel() should call onChannelLeft on success`() = runBlocking {
        val channelSid = "sid"
        coEvery { channelListManager.leaveChannel(channelSid) } returns Unit

        val channelListViewModel = ChannelListViewModel(chatRepository, channelListManager, userManager, loginManager)
        channelListViewModel.leaveChannel(channelSid)

        coVerify { channelListManager.leaveChannel(channelSid) }
        assertTrue(channelListViewModel.onChannelLeft.waitCalled())
    }

    @Test
    fun `channelListViewModel_leaveChannel() should call onChannelError on failure`() = runBlocking {
        val channelSid = "sid"
        coEvery { channelListManager.leaveChannel(channelSid) } throws ChatException(ChatError.CHANNEL_LEAVE_FAILED)

        val channelListViewModel = ChannelListViewModel(chatRepository, channelListManager, userManager, loginManager)
        channelListViewModel.leaveChannel(channelSid)

        coVerify { channelListManager.leaveChannel(channelSid) }
        assertTrue(channelListViewModel.onChannelLeft.waitNotCalled())
        assertTrue(channelListViewModel.onChannelError.waitValue(ChatError.CHANNEL_LEAVE_FAILED))
    }

    @Test
    fun `channelListViewModel_removeChannel() should call onChannelRemoved on success`() = runBlocking {
        val channelSid = "sid"
        coEvery { channelListManager.removeChannel(channelSid) } returns Unit

        val channelListViewModel = ChannelListViewModel(chatRepository, channelListManager, userManager, loginManager)
        channelListViewModel.removeChannel(channelSid)

        coVerify { channelListManager.removeChannel(channelSid) }
        assertTrue(channelListViewModel.onChannelRemoved.waitCalled())
    }

    @Test
    fun `channelListViewModel_removeChannel() should call onChannelError on failure`() = runBlocking {
        val channelSid = "sid"
        coEvery { channelListManager.removeChannel(channelSid) } throws ChatException(ChatError.CHANNEL_REMOVE_FAILED)

        val channelListViewModel = ChannelListViewModel(chatRepository, channelListManager, userManager, loginManager)
        channelListViewModel.removeChannel(channelSid)

        coVerify { channelListManager.removeChannel(channelSid) }
        assertTrue(channelListViewModel.onChannelError.waitValue(ChatError.CHANNEL_REMOVE_FAILED))
    }

    @Test
    fun `channelListViewModel_setFriendlyName() should call onUserUpdated on success`() = runBlocking {
        val friendlyName = "friendly name"
        coEvery { userManager.setFriendlyName(friendlyName) } returns Unit

        val channelListViewModel = ChannelListViewModel(chatRepository, channelListManager, userManager, loginManager)
        channelListViewModel.setFriendlyName(friendlyName)

        coVerify { userManager.setFriendlyName(friendlyName) }
        assertTrue(channelListViewModel.onUserUpdated.waitCalled())
    }

    @Test
    fun `channelListViewModel_setFriendlyName() should call onChannelError on failure`() = runBlocking {
        val friendlyName = "friendly name"
        coEvery { userManager.setFriendlyName(friendlyName) } throws ChatException(ChatError.USER_UPDATE_FAILED)

        val channelListViewModel = ChannelListViewModel(chatRepository, channelListManager, userManager, loginManager)
        channelListViewModel.setFriendlyName(friendlyName)

        coVerify { userManager.setFriendlyName(friendlyName) }
        assertTrue(channelListViewModel.onChannelError.waitValue(ChatError.USER_UPDATE_FAILED))
    }
}
