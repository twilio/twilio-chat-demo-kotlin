package com.twilio.chat.app.viewModel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.twilio.chat.Member
import com.twilio.chat.app.MEMBER_COUNT
import com.twilio.chat.app.common.asMemberListViewItems
import com.twilio.chat.app.common.enums.ChatError
import com.twilio.chat.app.common.extensions.ChatException
import com.twilio.chat.app.createTestMemberDataItem
import com.twilio.chat.app.data.models.RepositoryRequestStatus
import com.twilio.chat.app.data.models.RepositoryResult
import com.twilio.chat.app.getMockedMembers
import com.twilio.chat.app.manager.MemberListManager
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
    Member::class
)
class MemberListViewModelTest {

    private val channelSid = "channelSid"
    private val memberIdentity = "memberIdentity"

    @Rule
    var coroutineTestRule = CoroutineTestRule()

    @Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @MockK
    private lateinit var chatRepository: ChatRepository

    @MockK
    private lateinit var memberListManager: MemberListManager

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @Test
    fun `memberListViewModel_membersList() should contain all members stored in local cache`() = runBlocking {
        val expectedMembers = getMockedMembers(MEMBER_COUNT, "User Channels").toList()
        coEvery { chatRepository.getChannelMembers(any()) } returns
                flowOf(RepositoryResult(expectedMembers, RepositoryRequestStatus.COMPLETE))
        val memberListViewModel = MemberListViewModel(channelSid, chatRepository, memberListManager)
        assertTrue(memberListViewModel.membersList.waitValue(expectedMembers.asMemberListViewItems()))
        assertEquals(MEMBER_COUNT, memberListViewModel.membersList.waitValue().size)
    }

    @Test
    fun `memberListViewModel_membersLis should contain only filtered items`() = runBlocking {
        val memberAbc = createTestMemberDataItem(friendlyName = "abc")
        val memberBcd = createTestMemberDataItem(friendlyName = "bcd")
        val memberCde = createTestMemberDataItem(friendlyName = "cde")
        val expectedMembers = listOf(memberAbc, memberBcd, memberCde)
        coEvery { chatRepository.getChannelMembers(any()) } returns
                flowOf(RepositoryResult(expectedMembers, RepositoryRequestStatus.COMPLETE))
        val memberListViewModel = MemberListViewModel(channelSid, chatRepository, memberListManager)

        memberListViewModel.memberFilter = "c"
        assertEquals(3, memberListViewModel.membersList.waitValue().size)
        assertTrue(memberListViewModel.membersList.waitValue(expectedMembers.asMemberListViewItems()))

        memberListViewModel.memberFilter = "b"
        assertEquals(2, memberListViewModel.membersList.waitValue().size)
        assertTrue(memberListViewModel.membersList.waitValue(listOf(memberAbc, memberBcd).asMemberListViewItems()))

        memberListViewModel.memberFilter = "a"
        assertEquals(1, memberListViewModel.membersList.waitValue().size)
        assertTrue(memberListViewModel.membersList.waitValue(listOf(memberAbc).asMemberListViewItems()))

        memberListViewModel.memberFilter = ""
        assertEquals(3, memberListViewModel.membersList.waitValue().size)
        assertTrue(memberListViewModel.membersList.waitValue(expectedMembers.asMemberListViewItems()))
    }

    @Test
    fun `memberListViewModel_filteredUserChannelItems should ignore filter case`() = runBlocking {
        val namePrefix = "User Channels"
        val expectedMembers = getMockedMembers(MEMBER_COUNT, namePrefix).toList()
        coEvery { chatRepository.getChannelMembers(any()) } returns
                flowOf(RepositoryResult(expectedMembers, RepositoryRequestStatus.COMPLETE))
        val memberListViewModel = MemberListViewModel(channelSid, chatRepository, memberListManager)

        // When the filter string matches all channel names but is in uppercase
        memberListViewModel.memberFilter = namePrefix.toUpperCase()

        // Then verify that all channels match the filter
        assertEquals(MEMBER_COUNT, memberListViewModel.membersList.waitValue().size)
        assertTrue(memberListViewModel.membersList.waitValue(expectedMembers.asMemberListViewItems()))
    }

    @Test
    fun `memberListViewModel_memberList should be empty when Error occurred`() = runBlocking {
        coEvery { chatRepository.getChannelMembers(any()) } returns
                flowOf(RepositoryResult(listOf(), RepositoryRequestStatus.Error(ChatError.GENERIC_ERROR)))

        val memberListViewModel = MemberListViewModel(channelSid, chatRepository, memberListManager)
        assertEquals(0, memberListViewModel.membersList.waitValue().size)
    }

    @Test
    fun `memberListViewModel_removeMember() should call onMemberRemoved on success`() = runBlocking {
        coEvery { chatRepository.getChannelMembers(any()) } returns
                flowOf(RepositoryResult(listOf(), RepositoryRequestStatus.COMPLETE))
        coEvery { memberListManager.removeMember(memberIdentity) } returns Unit

        val memberListViewModel = MemberListViewModel(channelSid, chatRepository, memberListManager)
        memberListViewModel.removeMember(memberIdentity)

        coVerify { memberListManager.removeMember(memberIdentity) }
        assertTrue(memberListViewModel.onMemberRemoved.waitCalled())
    }

    @Test
    fun `memberListViewModel_removeMember() should call onMemberError on failure`() = runBlocking {
        coEvery { chatRepository.getChannelMembers(any()) } returns
                flowOf(RepositoryResult(listOf(), RepositoryRequestStatus.COMPLETE))
        coEvery { memberListManager.removeMember(memberIdentity) } throws ChatException(ChatError.MEMBER_REMOVE_FAILED)

        val memberListViewModel = MemberListViewModel(channelSid, chatRepository, memberListManager)
        memberListViewModel.removeMember(memberIdentity)

        coVerify { memberListManager.removeMember(memberIdentity) }
        assertTrue(memberListViewModel.onMemberError.waitValue(ChatError.MEMBER_REMOVE_FAILED))
    }
}
