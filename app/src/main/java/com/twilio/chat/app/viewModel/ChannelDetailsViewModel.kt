package com.twilio.chat.app.viewModel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twilio.chat.app.common.SingleLiveEvent
import com.twilio.chat.app.common.asChannelDetailsViewItem
import com.twilio.chat.app.common.enums.ChatError
import com.twilio.chat.app.common.extensions.ChatException
import com.twilio.chat.app.data.models.ChannelDetailsViewItem
import com.twilio.chat.app.data.models.RepositoryRequestStatus
import com.twilio.chat.app.manager.ChannelListManager
import com.twilio.chat.app.manager.MemberListManager
import com.twilio.chat.app.repository.ChatRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber

class ChannelDetailsViewModel(
    val channelSid: String,
    private val chatRepository: ChatRepository,
    private val channelListManager: ChannelListManager,
    private val memberListManager: MemberListManager
) : ViewModel() {

    val channelDetails = MutableLiveData<ChannelDetailsViewItem>()
    val isShowProgress = MutableLiveData<Boolean>()
    val onDetailsError = SingleLiveEvent<ChatError>()
    val onChannelMuted = SingleLiveEvent<Boolean>()
    val onChannelRemoved = SingleLiveEvent<Unit>()
    val onChannelRenamed = SingleLiveEvent<Unit>()
    val onMemberAdded = SingleLiveEvent<String>()

    init {
        Timber.d("init: $channelSid")
        viewModelScope.launch {
            getChannelResult()
        }
    }

    private suspend fun getChannelResult() {
        chatRepository.getChannel(channelSid).collect { result ->
            if (result.requestStatus is RepositoryRequestStatus.Error) {
                onDetailsError.value = ChatError.CHANNEL_GET_FAILED
                return@collect
            }
            result.data?.let { channelDetails.value = it.asChannelDetailsViewItem() }
        }
    }

    private fun setShowProgress(show: Boolean) {
        if (isShowProgress.value != show) {
            isShowProgress.value = show
        }
    }

    fun renameChannel(friendlyName: String) = viewModelScope.launch {
        if (isShowProgress.value == true) {
            return@launch
        }
        Timber.d("Renaming channel: $friendlyName")
        try {
            setShowProgress(true)
            channelListManager.renameChannel(channelSid, friendlyName)
            onChannelRenamed.call()
        } catch (e: ChatException) {
            Timber.d("Failed to rename channel")
            onDetailsError.value = ChatError.CHANNEL_RENAME_FAILED
        } finally {
            setShowProgress(false)
        }
    }

    fun muteChannel() = viewModelScope.launch {
        if (isShowProgress.value == true) {
            return@launch
        }
        Timber.d("Muting channel: $channelSid")
        try {
            setShowProgress(true)
            channelListManager.muteChannel(channelSid)
            onChannelMuted.value = true
        } catch (e: ChatException) {
            Timber.d("Failed to mute channel")
            onDetailsError.value = ChatError.CHANNEL_MUTE_FAILED
        } finally {
            setShowProgress(false)
        }
    }

    fun unmuteChannel() = viewModelScope.launch {
        if (isShowProgress.value == true) {
            return@launch
        }
        Timber.d("Unmuting channel: $channelSid")
        try {
            setShowProgress(true)
            channelListManager.unmuteChannel(channelSid)
            onChannelMuted.value = false
        } catch (e: ChatException) {
            Timber.d("Failed to unmute channel")
            onDetailsError.value = ChatError.CHANNEL_UNMUTE_FAILED
        } finally {
            setShowProgress(false)
        }
    }

    fun removeChannel() = viewModelScope.launch {
        if (isShowProgress.value == true) {
            return@launch
        }
        Timber.d("Removing channel: $channelSid")
        try {
            setShowProgress(true)
            channelListManager.removeChannel(channelSid)
            onChannelRemoved.call()
        } catch (e: ChatException) {
            Timber.d("Failed to remove channel")
            onDetailsError.value = ChatError.CHANNEL_REMOVE_FAILED
        } finally {
            setShowProgress(false)
        }
    }

    fun addMember(identity: String) = viewModelScope.launch {
        if (isShowProgress.value == true) {
            return@launch
        }
        Timber.d("Adding member: $identity")
        try {
            setShowProgress(true)
            memberListManager.addMember(identity)
            onMemberAdded.value = identity
        } catch (e: ChatException) {
            Timber.d("Failed to remove channel")
            onDetailsError.value = ChatError.MEMBER_ADD_FAILED
        } finally {
            setShowProgress(false)
        }
    }

    fun isChannelMuted() = channelDetails.value?.isMuted == true
}
