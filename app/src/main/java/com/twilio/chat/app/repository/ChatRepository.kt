package com.twilio.chat.app.repository

import androidx.paging.PagedList
import com.twilio.chat.*
import com.twilio.chat.app.common.*
import com.twilio.chat.app.common.enums.CrashIn
import com.twilio.chat.app.common.extensions.*
import com.twilio.chat.app.data.ChatClientWrapper
import com.twilio.chat.app.data.localCache.LocalCacheProvider
import com.twilio.chat.app.data.localCache.dao.ChannelsDao
import com.twilio.chat.app.data.localCache.entity.ChannelDataItem
import com.twilio.chat.app.data.localCache.entity.MemberDataItem
import com.twilio.chat.app.data.localCache.entity.MessageDataItem
import com.twilio.chat.app.data.models.MessageListViewItem
import com.twilio.chat.app.data.models.RepositoryRequestStatus
import com.twilio.chat.app.data.models.RepositoryRequestStatus.*
import com.twilio.chat.app.data.models.RepositoryResult
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import timber.log.Timber

interface ChatRepository {
    fun getPublicChannels(): Flow<RepositoryResult<List<ChannelDataItem>>>
    fun getUserChannels(): Flow<RepositoryResult<List<ChannelDataItem>>>
    fun getChannel(channelSid: String): Flow<RepositoryResult<ChannelDataItem?>>
    fun getSelfUser(): Flow<User>
    fun getMessageByUuid(messageUuid: String): MessageDataItem?
    // Interim solution till paging v3.0 is available as an alpha version.
    // It has support for converting PagedList types
    fun getMessages(channelSid: String, pageSize: Int): Flow<RepositoryResult<PagedList<MessageListViewItem>>>
    fun insertMessage(message: MessageDataItem)
    fun updateMessageByUuid(message: MessageDataItem)
    fun updateMessageStatus(messageUuid: String, sendStatus: Int)
    fun getTypingMembers(channelSid: String): Flow<List<MemberDataItem>>
    fun getChannelMembers(channelSid: String): Flow<RepositoryResult<List<MemberDataItem>>>
    fun updateMessageMediaDownloadStatus(
        messageSid: String,
        downloadId: Long? = null,
        downloadLocation: String? = null,
        downloading: Boolean? = null,
        downloadedBytes: Long? = null
    )
    fun updateMessageMediaUploadStatus(
        messageUuid: String,
        uploading: Boolean? = null,
        uploadedBytes: Long? = null
    )
    fun simulateCrash(where: CrashIn)
    fun clear()
    fun subscribeToChatClientEvents()
    fun unsubscribeFromChatClientEvents()
}

class ChatRepositoryImpl(
    private val chatClientWrapper: ChatClientWrapper,
    private val localCache: LocalCacheProvider,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ChatRepository {

    private val repositoryScope = CoroutineScope(dispatchers.io() + SupervisorJob())

    private val clientListener = createClientListener(
            onChannelDeleted = { channel ->
                launch {
                    Timber.d("Channel deleted $channel")
                    localCache.channelsDao().delete(channel.sid)
                }
            },
            onChannelUpdated = { channel, _ ->
                launch{ insertOrUpdateChannel(channel.sid) }
            },
            onChannelJoined = { channel ->
                launch { insertOrUpdateChannel(channel.sid) }
            },
            onChannelAdded = { channel ->
                launch {
                    insertOrUpdateChannel(channel.sid)
                }
            },
            onChannelSynchronizationChange = { channel ->
                launch { insertOrUpdateChannel(channel.sid) }
            }
    )

    private val channelListener = createChannelListener(
        onTypingStarted = { channel, member ->
            Timber.d("${member.identity} started typing in ${channel.friendlyName}")
            this@ChatRepositoryImpl.launch {
                val userDescriptor = member.getUserDescriptor()
                localCache.membersDao().insertOrReplace(member.asMemberDataItem(typing = true, userDescriptor = userDescriptor))
            }
        },
        onTypingEnded = { channel, member ->
            Timber.d("${member.identity} stopped typing in ${channel.friendlyName}")
            this@ChatRepositoryImpl.launch {
                val userDescriptor = member.getUserDescriptor()
                localCache.membersDao().insertOrReplace(member.asMemberDataItem(typing = false, userDescriptor = userDescriptor))
            }
        },
        onMemberAdded = { member ->
            Timber.d("${member.identity} added in ${member.channel.sid}")
            this@ChatRepositoryImpl.launch {
                val userDescriptor = member.getUserDescriptor()
                localCache.membersDao().insertOrReplace(member.asMemberDataItem(userDescriptor = userDescriptor))
            }
        },
        onMemberUpdated = { member, reason ->
            Timber.d("${member.identity} updated in ${member.channel.sid}, reason: $reason")
            this@ChatRepositoryImpl.launch {
                val userDescriptor = member.getUserDescriptor()
                localCache.membersDao().insertOrReplace(member.asMemberDataItem( userDescriptor = userDescriptor))
            }
        },
        onMemberDeleted = { member ->
            Timber.d("${member.identity} deleted in ${member.channel.sid}")
            this@ChatRepositoryImpl.launch {
                localCache.membersDao().delete(member.asMemberDataItem())
            }
        },
        onMessageDeleted = { message ->
            deleteMessage(message)
        },
        onMessageUpdated = { message, reason ->
            updateMessage(message, reason)
        },
        onMessageAdded = { message ->
            addMessage(message)
        }
    )

    private fun launch(block: suspend CoroutineScope.() -> Unit) = repositoryScope.launch(
        context = CoroutineExceptionHandler { _, e -> Timber.e(e, "Coroutine failed ${e.localizedMessage}") },
        block = block
    )

    override fun getPublicChannels(): Flow<RepositoryResult<List<ChannelDataItem>>> {
        val localDataFlow = localCache.channelsDao().getPublicChannels()
        val fetchStatusFlow =
            fetchChannels(Channels::getPublicChannelsList, ChannelsDao::deleteGonePublicChannels).flowOn(dispatchers.io())

        return combine(localDataFlow, fetchStatusFlow) { data, status -> RepositoryResult(data, status) }
    }

    override fun getUserChannels(): Flow<RepositoryResult<List<ChannelDataItem>>> {
        val localDataFlow = localCache.channelsDao().getUserChannels()
        val fetchStatusFlow =
            fetchChannels(Channels::getUserChannelsList, ChannelsDao::deleteGoneUserChannels).flowOn(dispatchers.io())

        return combine(localDataFlow, fetchStatusFlow) { data, status -> RepositoryResult(data, status) }
    }

    override fun getChannel(channelSid: String): Flow<RepositoryResult<ChannelDataItem?>> {
        val localDataFlow = localCache.channelsDao().getChannel(channelSid)
        val fetchStatusFlow = fetchChannel(channelSid).flowOn(dispatchers.io())

        return combine(localDataFlow, fetchStatusFlow) { data, status -> RepositoryResult(data, status) }
    }

    override fun getMessageByUuid(messageUuid: String) = localCache.messagesDao().getMessageByUuid(messageUuid)

    override fun getMessages(channelSid: String, pageSize: Int): Flow<RepositoryResult<PagedList<MessageListViewItem>>> {
        Timber.v("getMessages($channelSid, $pageSize)")
        val requestStatusChannel = BroadcastChannel<RepositoryRequestStatus>(BUFFERED)
        val boundaryCallback = object : PagedList.BoundaryCallback<MessageListViewItem>() {
            override fun onZeroItemsLoaded() {
                Timber.v("BoundaryCallback.onZeroItemsLoaded()")
                launch {
                    fetchMessages(channelSid) { getLastMessages(pageSize) }
                        .flowOn(dispatchers.io())
                        .collect {
                            requestStatusChannel.send(it)
                        }
                }
            }

            override fun onItemAtEndLoaded(itemAtEnd: MessageListViewItem) {
                Timber.v("BoundaryCallback.onItemAtEndLoaded($itemAtEnd)")
            }

            override fun onItemAtFrontLoaded(itemAtFront: MessageListViewItem) {
                Timber.v("BoundaryCallback.onItemAtFrontLoaded($itemAtFront)")
                if (itemAtFront.index > 0) {
                    launch {
                        fetchMessages(channelSid) { getMessagesBefore(itemAtFront.index - 1, pageSize) }
                            .flowOn(dispatchers.io())
                            .collect {
                                requestStatusChannel.send(it)
                            }
                    }
                }
            }
        }

        val pagedListFlow = localCache.messagesDao().getMessagesSorted(channelSid)
            .mapByPage { it?.asMessageListViewItems() }
            .toFlow(
                pageSize = pageSize,
                boundaryCallback = boundaryCallback
            )
            .onStart {
                requestStatusChannel.send(FETCHING)
            }
            .onEach {
                requestStatusChannel.send(COMPLETE)
            }

        return combine(pagedListFlow, requestStatusChannel.asFlow().distinctUntilChanged() ) { data, status -> RepositoryResult(data, status) }
    }

    override fun insertMessage(message: MessageDataItem) {
        launch {
            localCache.messagesDao().insertOrReplace(message)
        }
    }

    override fun updateMessageByUuid(message: MessageDataItem) {
        launch {
            localCache.messagesDao().updateByUuidOrInsert(message)
        }
    }

    override fun updateMessageStatus(messageUuid: String, sendStatus: Int) {
        launch {
            localCache.messagesDao().updateMessageStatus(messageUuid, sendStatus)
        }
    }

    override fun getTypingMembers(channelSid: String): Flow<List<MemberDataItem>> =
        localCache.membersDao().getTypingMembers(channelSid)

    override fun getChannelMembers(channelSid: String): Flow<RepositoryResult<List<MemberDataItem>>> {
        val localDataFlow = localCache.membersDao().getAllMembers(channelSid)
        val fetchStatusFlow = fetchMembers(channelSid).flowOn(dispatchers.io())

        return combine(localDataFlow, fetchStatusFlow) { data, status -> RepositoryResult(data, status) }
    }

    override fun updateMessageMediaDownloadStatus(
        messageSid: String,
        downloadId: Long?,
        downloadLocation: String?,
        downloading: Boolean?,
        downloadedBytes: Long?
    ) {
        launch {
            if (downloadId != null) {
                localCache.messagesDao().updateMediaDownloadId(messageSid, downloadId)
            }
            if (downloadLocation != null) {
                localCache.messagesDao().updateMediaDownloadLocation(messageSid, downloadLocation)
            }
            if (downloading != null) {
                localCache.messagesDao().updateMediaDownloadStatus(messageSid, downloading)
            }
            if (downloadedBytes != null) {
                localCache.messagesDao().updateMediaDownloadedBytes(messageSid, downloadedBytes)
            }
        }
    }

    override fun updateMessageMediaUploadStatus(
        messageUuid: String,
        uploading: Boolean?,
        uploadedBytes: Long?
    ) {
        launch {
            if (uploading != null) {
                localCache.messagesDao().updateMediaUploadStatus(messageUuid, uploading)
            }
            if (uploadedBytes != null) {
                localCache.messagesDao().updateMediaUploadedBytes(messageUuid, uploadedBytes)
            }
        }
    }

    override fun simulateCrash(where: CrashIn) {
        launch {
            chatClientWrapper.getChatClient().simulateCrash(where)
        }
    }

    override fun clear() {
        launch {
            localCache.clearAllTables()
        }
    }

    override fun getSelfUser(): Flow<User> = callbackFlow {
        val client = chatClientWrapper.getChatClient()
        val listener = createClientListener (
            onUserUpdated = { user, _ ->
                user.takeIf { it.identity == client.myIdentity}
                    ?.let { offer(it) }
            }
        )
        client.addListener(listener)
        send(client.users.myUser)
        awaitClose { client.removeListener(listener) }
    }

    private fun fetchMessages(channelSid: String, fetch: suspend Messages.() -> List<Message>) = flow {
        emit(FETCHING)
        try {
            val identity = chatClientWrapper.getChatClient().myIdentity
            val messages = chatClientWrapper
                .getChatClient()
                .channels
                .getChannel(channelSid)
                .messages
                .fetch()
                .asMessageDataItems(identity)
            localCache.messagesDao().insert(messages)
            emit(COMPLETE)
        } catch (e: ChatException) {
            Timber.d("fetchMessages error: ${e.error.message}")
            emit(Error(e.error))
        }
    }

    private fun fetchChannel(channelSid: String) = flow {
        emit(FETCHING)
        try {
            insertOrUpdateChannel(channelSid)
            emit(COMPLETE)
        } catch (e: ChatException) {
            Timber.d("fetchChannels error: ${e.error.message}")
            emit(Error(e.error))
        }
    }

    private fun fetchMembers(channelSid: String) = flow {
        emit(FETCHING)
        try {
            val channel = chatClientWrapper.getChatClient().channels.getChannel(channelSid)
            channel.waitForSynchronization()
            channel.members.membersList.forEach { member ->
                val userDescriptor = member.getUserDescriptor()
                localCache.membersDao().insertOrReplace(member.asMemberDataItem(userDescriptor = userDescriptor))
            }
            emit(COMPLETE)
        } catch (e: ChatException) {
            Timber.d("fetchChannels error: ${e.error.message}")
            emit(Error(e.error))
        }
    }

    private fun fetchChannels(fetch: suspend Channels.() -> Paginator<ChannelDescriptor>,
                      deleteGoneChannels: ChannelsDao.(channels: List<ChannelDataItem>) -> Unit) = channelFlow {
        send(FETCHING)

        try {
            // get items from client
            val dataItems = chatClientWrapper
                .getChatClient()
                .channels
                .fetch()
                .requestAllItems()
                .toChannelDataItemList()
            Timber.d("repo dataItems from client $dataItems")

            localCache.channelsDao().deleteGoneChannels(dataItems)
            send(SUBSCRIBING)

            var status: RepositoryRequestStatus = COMPLETE
            supervisorScope {
                // get all channels and update channel data in local cache
                dataItems.forEach {
                    launch {
                        try {
                            insertOrUpdateChannel(it.sid)
                        } catch (e: ChatException) {
                            Timber.d("insertOrUpdateChannel error: ${e.error.message}")
                            status = Error(e.error)
                        }
                    }
                }
            }
            Timber.d("fetchChannels completed with status: $status")
            send(status)
        } catch (e: ChatException) {
            Timber.d("fetchChannels error: ${e.error.message}")
            send(Error(e.error))
        }
    }

    override fun subscribeToChatClientEvents() {
        launch {
            Timber.d("Client listener added")
            chatClientWrapper.getChatClient().addListener(clientListener)
        }
    }

    override fun unsubscribeFromChatClientEvents() {
        launch {
            Timber.d("Client listener removed")
            chatClientWrapper.getChatClient().removeListener(clientListener)
        }
    }

    private suspend fun insertOrUpdateChannel(channelSid: String) {
        val channel = chatClientWrapper.getChatClient().channels.getChannel(channelSid)
        Timber.d("repo updating dataItem in db... ${channel.friendlyName}")
        channel.addListener(channelListener)
        localCache.channelsDao().insert(channel.toChannelDataItem())
        localCache.channelsDao().update(channel.sid, channel.type.value,
            channel.status.value, channel.notificationLevel.value, channel.friendlyName)
        launch {
            localCache.channelsDao().updateMemberCount(channelSid, channel.getMemberCount())
        }
        launch {
            localCache.channelsDao().updateMessagesCount(channelSid, channel.getMessageCount())
        }
        launch {
            localCache.channelsDao().updateUnconsumedMessagesCount(channelSid, channel.getUnconsumedMessageCount() ?: return@launch)
        }
    }

    private fun deleteMessage(message: Message) {
        launch {
            val identity = chatClientWrapper.getChatClient().myIdentity
            Timber.d("Message deleted: ${message.toMessageDataItem(identity)}")
            localCache.messagesDao().delete(message.toMessageDataItem(identity))
        }
    }

    private fun updateMessage(message: Message, updateReason: Message.UpdateReason) {
        launch {
            val identity = chatClientWrapper.getChatClient().myIdentity
            Timber.d("Message updated: ${message.toMessageDataItem(identity)}, reason: $updateReason")
            localCache.messagesDao().insertOrReplace(message.toMessageDataItem(identity))
        }
    }

    private fun addMessage(message: Message) {
        launch {
            val identity = chatClientWrapper.getChatClient().myIdentity
            Timber.d("Message added: ${message.toMessageDataItem(identity)}")
            localCache.messagesDao().updateByUuidOrInsert(message.toMessageDataItem(identity, message.attributes.string ?: ""))
        }
    }

    companion object {
        val INSTANCE get() = _instance ?: error("call ChatRepository.createInstance() first")

        private var _instance: ChatRepository? = null

        fun createInstance(chatClientWrapper: ChatClientWrapper, localCache: LocalCacheProvider) {
            check(_instance == null) { "ChatRepository singleton instance has been already created" }
            _instance = ChatRepositoryImpl(chatClientWrapper, localCache)
        }
    }
}
