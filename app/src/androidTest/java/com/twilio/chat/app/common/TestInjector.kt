package com.twilio.chat.app.common

import android.app.Application
import android.content.Context
import androidx.paging.PagedList
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.twilio.chat.app.data.localCache.entity.ChannelDataItem
import com.twilio.chat.app.data.localCache.entity.MemberDataItem
import com.twilio.chat.app.data.models.MessageListViewItem
import com.twilio.chat.app.data.models.RepositoryRequestStatus
import com.twilio.chat.app.data.models.RepositoryResult
import com.twilio.chat.app.manager.*
import com.twilio.chat.app.repository.ChatRepository
import com.twilio.chat.app.testUtil.mockito.mock
import com.twilio.chat.app.testUtil.mockito.whenCall
import com.twilio.chat.app.viewModel.ChannelDetailsViewModel
import com.twilio.chat.app.viewModel.ChannelListViewModel
import com.twilio.chat.app.viewModel.ChannelViewModel
import com.twilio.chat.app.viewModel.MemberListViewModel
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

val testInjector: TestInjector get() = injector as? TestInjector ?: error("You must call setupTestInjector() first")

fun setupTestInjector() = setupTestInjector(TestInjector())

open class TestInjector : Injector() {

    val typingMembersListChannel = BroadcastChannel<List<MemberDataItem>>(1)
    val messageResultChannel = BroadcastChannel<RepositoryResult<PagedList<MessageListViewItem>>>(1)

    var publicChannelRepositoryResult : Flow<RepositoryResult<List<ChannelDataItem>>> = emptyFlow()
    var userChannelRepositoryResult : Flow<RepositoryResult<List<ChannelDataItem>>> = emptyFlow()
    var memberRepositoryResult : Flow<RepositoryResult<List<MemberDataItem>>> = emptyFlow()
    var typingMembersList = typingMembersListChannel.asFlow()
    var messageResult = messageResultChannel.asFlow()

    private val repositoryMock: ChatRepository = mock {
        whenCall(getPublicChannels()) doAnswer { publicChannelRepositoryResult }
        whenCall(getUserChannels()) doAnswer { userChannelRepositoryResult }
        whenCall(getChannelMembers(any())) doAnswer { memberRepositoryResult }
        whenCall(getTypingMembers(any())) doAnswer { typingMembersList }
        whenCall(getMessages(any(), any())) doAnswer { messageResult }
        whenCall(getSelfUser()) doAnswer { emptyFlow() }
        runBlocking {
            whenCall(getChannel(any())) doAnswer { flowOf(RepositoryResult(null as ChannelDataItem?, RepositoryRequestStatus.COMPLETE)) }
        }
    }

    private val channelManagerMock: ChannelListManager = com.nhaarman.mockitokotlin2.mock {
        onBlocking { createChannel(any(), any()) } doReturn  ""
        onBlocking { joinChannel(any()) } doReturn Unit
        onBlocking { removeChannel(any()) } doReturn Unit
        onBlocking { leaveChannel(any()) } doReturn Unit
        onBlocking { muteChannel(any()) } doReturn Unit
        onBlocking { unmuteChannel(any()) } doReturn Unit
    }

    private val memberListManagerMock: MemberListManager = com.nhaarman.mockitokotlin2.mock {
        onBlocking { addMember(any()) } doReturn Unit
        onBlocking { removeMember(any()) } doReturn Unit
    }

    private val userManagerMock: UserManager = com.nhaarman.mockitokotlin2.mock {
        onBlocking { setFriendlyName(any()) } doReturn Unit
    }

    private val channelManager: ChannelManager = com.nhaarman.mockitokotlin2.mock()

    private val loginManagerMock: LoginManager = com.nhaarman.mockitokotlin2.mock()

    override fun createChannelListViewModel(application: Application)
            = ChannelListViewModel(repositoryMock, channelManagerMock, userManagerMock, loginManagerMock)

    override fun createChannelViewModel(appContext: Context, channelSid: String)
            = ChannelViewModel(appContext, channelSid, repositoryMock, channelManager)

    override fun createMemberListViewModel(channelSid: String)
            = MemberListViewModel(channelSid, repositoryMock, memberListManagerMock)

    override fun createChannelDetailsViewModel(channelSid: String)
            = ChannelDetailsViewModel(channelSid, repositoryMock, channelManagerMock, memberListManagerMock)
}
