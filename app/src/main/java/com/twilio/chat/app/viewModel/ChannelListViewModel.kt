package com.twilio.chat.app.viewModel

import androidx.lifecycle.*
import com.twilio.chat.Channel
import com.twilio.chat.app.common.SingleLiveEvent
import com.twilio.chat.app.common.asChannelListViewItems
import com.twilio.chat.app.common.asUserViewItem
import com.twilio.chat.app.common.enums.ChatError
import com.twilio.chat.app.common.enums.CrashIn
import com.twilio.chat.app.common.extensions.ChatException
import com.twilio.chat.app.common.merge
import com.twilio.chat.app.data.models.ChannelListViewItem
import com.twilio.chat.app.data.models.RepositoryRequestStatus
import com.twilio.chat.app.data.models.UserViewItem
import com.twilio.chat.app.manager.ChannelListManager
import com.twilio.chat.app.manager.LoginManager
import com.twilio.chat.app.manager.UserManager
import com.twilio.chat.app.repository.ChatRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import timber.log.Timber
import kotlin.properties.Delegates

@ExperimentalCoroutinesApi
@FlowPreview
class ChannelListViewModel(
    private val chatRepository: ChatRepository,
    private val channelListManager: ChannelListManager,
    private val userManager: UserManager,
    private val loginManager: LoginManager
) : ViewModel() {

    private val unfilteredPublicChannelItems = MutableLiveData<List<ChannelListViewItem>>(emptyList())
    private val unfilteredUserChannelItems = MutableLiveData<List<ChannelListViewItem>>(emptyList())
    val publicChannelItems = MutableLiveData<List<ChannelListViewItem>>(emptyList())
    val userChannelItems = MutableLiveData<List<ChannelListViewItem>>(emptyList())
    val selfUser = MutableLiveData<UserViewItem>()
    val isDataLoading = SingleLiveEvent<Boolean>()
    val onChannelCreated = SingleLiveEvent<Unit>()
    val onChannelRemoved = SingleLiveEvent<Unit>()
    val onChannelJoined = SingleLiveEvent<Unit>()
    val onChannelMuted = SingleLiveEvent<Boolean>()
    val onChannelLeft = SingleLiveEvent<Unit>()
    val onChannelError = SingleLiveEvent<ChatError>()
    val onUserUpdated = SingleLiveEvent<Unit>()
    val onSignedOut = SingleLiveEvent<Unit>()
    var selectedChannelSid: String? = null
    var channelFilter by Delegates.observable("") { _, _, name ->
        publicChannelItems.value = unfilteredPublicChannelItems.value?.filterByName(name) ?: emptyList()
        userChannelItems.value = unfilteredUserChannelItems.value?.filterByName(name) ?: emptyList()
    }

    init {
        Timber.d("init")

        getPublicChannels()
        getUserChannels()
        getSelfUser()

        unfilteredPublicChannelItems.observeForever {
            publicChannelItems.value = it.filterByName(channelFilter)
        }

        unfilteredUserChannelItems.observeForever {
            userChannelItems.value = it.filterByName(channelFilter)
        }
    }

    fun getPublicChannels() = viewModelScope.launch {
        chatRepository.getPublicChannels().collect { (list, status) ->
            unfilteredPublicChannelItems.value = list.asChannelListViewItems().merge(unfilteredPublicChannelItems.value)
            if (status is RepositoryRequestStatus.Error) {
                onChannelError.value = ChatError.CHANNEL_FETCH_PUBLIC_FAILED
            }
        }
    }

    fun getUserChannels() = viewModelScope.launch {
        chatRepository.getUserChannels().collect { (list, status) ->
            unfilteredUserChannelItems.value = list.asChannelListViewItems().merge(unfilteredUserChannelItems.value)
            if (status is RepositoryRequestStatus.Error) {
                onChannelError.value = ChatError.CHANNEL_FETCH_USER_FAILED
            }
        }
    }

    private fun getSelfUser() = viewModelScope.launch {
        chatRepository.getSelfUser().collect { user ->
            Timber.d("Self user collected: ${user.friendlyName}, ${user.identity}")
            selfUser.value = user.asUserViewItem()
        }
    }

    private fun setDataLoading(loading: Boolean) {
        if (isDataLoading.value != loading) {
            isDataLoading.value = loading
        }
    }

    private fun setChannelLoading(channelSid: String, loading: Boolean) {
        fun ChannelListViewItem.transform() = if (sid == channelSid) copy(isLoading = loading) else this
        unfilteredPublicChannelItems.value = unfilteredPublicChannelItems.value?.map { it.transform() }
        unfilteredUserChannelItems.value = unfilteredUserChannelItems.value?.map { it.transform() }
    }

    private fun isChannelLoading(channelSid: String): Boolean =
        unfilteredPublicChannelItems.value?.find { it.sid == channelSid }?.isLoading == true ||
                unfilteredUserChannelItems.value?.find { it.sid == channelSid }?.isLoading == true

    private fun List<ChannelListViewItem>.filterByName(name: String): List<ChannelListViewItem> =
        if (name.isEmpty()) {
            this
        } else {
            filter {
                it.name.contains(name, ignoreCase = true)
            }
        }

    fun createChannel(friendlyName: String, type: Channel.ChannelType) = viewModelScope.launch {
        Timber.d("Creating channel: $friendlyName $type")
        try {
            setDataLoading(true)
            val channel = channelListManager.createChannel(friendlyName, type)
            Timber.d("Created channel: $friendlyName $type $channel")
            onChannelCreated.call()
            joinChannel(channel)
        } catch (e: ChatException) {
            Timber.d("Failed to create channel")
            onChannelError.value = ChatError.CHANNEL_CREATE_FAILED
        } finally {
            setDataLoading(false)
        }
    }

    fun joinChannel(channelSid: String) = viewModelScope.launch {
        if (isChannelLoading(channelSid)) {
            return@launch
        }
        Timber.d("Joining channel: $channelSid")
        try {
            setChannelLoading(channelSid, true)
            channelListManager.joinChannel(channelSid)
            onChannelJoined.call()
        } catch (e: ChatException) {
            Timber.d("Failed to join channel")
            onChannelError.value = ChatError.CHANNEL_JOIN_FAILED
        } finally {
            setChannelLoading(channelSid, false)
        }
    }

    fun muteChannel(channelSid: String) = viewModelScope.launch {
        if (isChannelLoading(channelSid)) {
            return@launch
        }
        Timber.d("Muting channel: $channelSid")
        try {
            setChannelLoading(channelSid, true)
            channelListManager.muteChannel(channelSid)
            onChannelMuted.value = true
        } catch (e: ChatException) {
            Timber.d("Failed to mute channel")
            onChannelError.value = ChatError.CHANNEL_MUTE_FAILED
        } finally {
            setChannelLoading(channelSid, false)
        }
    }

    fun unmuteChannel(channelSid: String) = viewModelScope.launch {
        if (isChannelLoading(channelSid)) {
            return@launch
        }
        Timber.d("Unmuting channel: $channelSid")
        try {
            setChannelLoading(channelSid, true)
            channelListManager.unmuteChannel(channelSid)
            onChannelMuted.value = false
        } catch (e: ChatException) {
            Timber.d("Failed to unmute channel")
            onChannelError.value = ChatError.CHANNEL_UNMUTE_FAILED
        } finally {
            setChannelLoading(channelSid, false)
        }
    }

    fun leaveChannel(channelSid: String) = viewModelScope.launch {
        if (isChannelLoading(channelSid)) {
            return@launch
        }
        Timber.d("Leaving channel: $channelSid")
        try {
            setChannelLoading(channelSid, true)
            channelListManager.leaveChannel(channelSid)
            onChannelLeft.call()
        } catch (e: ChatException) {
            Timber.d("Failed to remove channel")
            onChannelError.value = ChatError.CHANNEL_LEAVE_FAILED
        } finally {
            setChannelLoading(channelSid, false)
        }
    }

    fun removeChannel(channelSid: String) = viewModelScope.launch {
        if (isChannelLoading(channelSid)) {
            return@launch
        }
        Timber.d("Removing channel: $channelSid")
        try {
            setChannelLoading(channelSid, true)
            channelListManager.removeChannel(channelSid)
            onChannelRemoved.call()
        } catch (e: ChatException) {
            Timber.d("Failed to remove channel")
            onChannelError.value = ChatError.CHANNEL_REMOVE_FAILED
        } finally {
            setChannelLoading(channelSid, false)
        }
    }

    fun setFriendlyName(friendlyName: String) = viewModelScope.launch {
        Timber.d("Updating self user: $friendlyName")
        try {
            setDataLoading(true)
            userManager.setFriendlyName(friendlyName)
            Timber.d("Self user updated: $friendlyName")
            onUserUpdated.call()
        } catch (e: ChatException) {
            Timber.d("Failed to update self user")
            onChannelError.value = ChatError.USER_UPDATE_FAILED
        } finally {
            setDataLoading(false)
        }
    }

    fun simulateCrash(where: CrashIn) {
        chatRepository.simulateCrash(where)
    }

    fun signOut() = viewModelScope.launch {
        Timber.d("signOut")
        loginManager.signOut()
        onSignedOut.call()
    }

    fun isChannelJoined(channelSid: String): Boolean =
        unfilteredUserChannelItems.value?.find { it.sid == channelSid }?.participatingStatus == Channel.ChannelStatus.JOINED.value ||
                unfilteredPublicChannelItems.value?.find { it.sid == channelSid }?.participatingStatus == Channel.ChannelStatus.JOINED.value

    fun isChannelMuted(channelSid: String): Boolean =
        unfilteredUserChannelItems.value?.find { it.sid == channelSid }?.isMuted == true ||
                unfilteredPublicChannelItems.value?.find { it.sid == channelSid }?.isMuted == true
}
