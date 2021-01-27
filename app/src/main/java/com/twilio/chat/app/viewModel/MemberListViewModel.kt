package com.twilio.chat.app.viewModel

import androidx.lifecycle.*
import com.twilio.chat.app.common.SingleLiveEvent
import com.twilio.chat.app.common.asMemberListViewItems
import com.twilio.chat.app.common.enums.ChatError
import com.twilio.chat.app.common.extensions.ChatException
import com.twilio.chat.app.data.models.MemberListViewItem
import com.twilio.chat.app.data.models.RepositoryRequestStatus
import com.twilio.chat.app.manager.MemberListManager
import com.twilio.chat.app.repository.ChatRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import timber.log.Timber
import kotlin.properties.Delegates

@ExperimentalCoroutinesApi
@FlowPreview
class MemberListViewModel(
    val channelSid: String,
    private val chatRepository: ChatRepository,
    private val memberListManager: MemberListManager
) : ViewModel() {

    private var unfilteredMembersList by Delegates.observable(listOf<MemberListViewItem>()) { _, _, _ ->
        updateMemberList()
    }
    val membersList = MutableLiveData<List<MemberListViewItem>>(emptyList())
    var memberFilter by Delegates.observable("") { _, _, _ ->
        updateMemberList()
    }
    val onMemberError = SingleLiveEvent<ChatError>()
    val onMemberRemoved = SingleLiveEvent<Unit>()
    var selectedMemberIdentity: String? = null

    init {
        Timber.d("init")
        getChannelMembers()
    }

    private fun updateMemberList() {
        membersList.value = unfilteredMembersList.filterByName(memberFilter)
    }

    private fun List<MemberListViewItem>.filterByName(name: String): List<MemberListViewItem> =
        if (name.isEmpty()) {
            this
        } else {
            filter {
                it.friendlyName.contains(name, ignoreCase = true)
            }
        }

    fun getChannelMembers() = viewModelScope.launch {
        chatRepository.getChannelMembers(channelSid).collect { (list, status) ->
            unfilteredMembersList = list.asMemberListViewItems()
            if (status is RepositoryRequestStatus.Error) {
                onMemberError.value = ChatError.MEMBER_FETCH_FAILED
            }
        }
    }

    fun removeMember(identity: String) = viewModelScope.launch {
        try {
            memberListManager.removeMember(identity)
            onMemberRemoved.call()
        } catch (e: ChatException) {
            Timber.d("Failed to remove member")
            onMemberError.value = ChatError.MEMBER_REMOVE_FAILED
        }
    }
}
