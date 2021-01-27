package com.twilio.chat.app.common.extensions

import android.content.Context
import com.twilio.chat.*
import com.twilio.chat.ChatClient.Properties
import com.twilio.chat.ErrorInfo.CHANNEL_NOT_SYNCHRONIZED
import com.twilio.chat.app.common.enums.ChatError
import com.twilio.chat.app.common.enums.CrashIn
import com.twilio.chat.app.data.models.Client
import com.twilio.chat.app.data.models.Response
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class ChatException(val error: ChatError) : Exception("$error") {
    constructor(errorInfo: ErrorInfo) : this(ChatError.fromErrorInfo(errorInfo))
}

suspend fun createClientAsync(context: Context, token: String, properties: Properties): Response {
    val client = createChatClient(context, token, properties)
    client.waitForSynchronization()
    return Client(client)
}

private suspend fun createChatClient(applicationContext: Context, token: String, properties: Properties) =
    suspendCoroutine<ChatClient> { continuation ->
        ChatClient.create(applicationContext, token, properties, object : CallbackListener<ChatClient>() {
            override fun onSuccess(chatClient: ChatClient) = continuation.resume(chatClient)

            override fun onError(errorInfo: ErrorInfo) = continuation.resumeWithException(ChatException(errorInfo))
        })
    }

private suspend fun ChatClient.waitForSynchronization(): Unit = suspendCancellableCoroutine { continuation ->
    addListener(
        onClientSynchronization = { status ->
            synchronized(continuation) {
                if (continuation.isActive && status >= ChatClient.SynchronizationStatus.CHANNELS_COMPLETED) {
                    removeAllListeners()
                    continuation.resume(Unit)
                }
            }
        }
    )
}

suspend fun ChatClient.registerFCMToken(token: String) = suspendCancellableCoroutine<Unit> { continuation ->
    registerFCMToken(ChatClient.FCMToken(token), object : StatusListener() {

        override fun onSuccess() {
            if (continuation.isActive) continuation.resume(Unit)
        }

        override fun onError(errorInfo: ErrorInfo) {
            Timber.d("Failed to register for FCM: $token, $errorInfo")
            if (continuation.isActive) continuation.resumeWithException(ChatException(errorInfo))
        }
    })
}

suspend fun ChatClient.unregisterFCMToken(token: String) = suspendCancellableCoroutine<Unit> { continuation ->
    unregisterFCMToken(ChatClient.FCMToken(token), object : StatusListener() {

        override fun onSuccess() = continuation.resume(Unit)

        override fun onError(errorInfo: ErrorInfo) = continuation.resumeWithException(ChatException(errorInfo))
    })
}

fun ChatClient.simulateCrash(where: CrashIn) {
    val method = ChatClient::class.java.getDeclaredMethod("simulateCrash", Int::class.java)
    method.isAccessible = true
    method.invoke(this, where.value)
}

suspend fun Channel.waitForSynchronization() {
    val complete = CompletableDeferred<Unit>()
    val listener = addListener(
        onSynchronizationChanged = { channel ->
            synchronized<Unit>(complete) {
                if (complete.isCompleted) return@addListener
                if (channel.synchronizationStatus == Channel.SynchronizationStatus.FAILED) {
                    val errorInfo = ErrorInfo(CHANNEL_NOT_SYNCHRONIZED, "Channel synchronization failed: ${channel.sid}}")
                    complete.completeExceptionally(ChatException(errorInfo))
                } else if (channel.synchronizationStatus.value >= Channel.SynchronizationStatus.ALL.value) {
                    complete.complete(Unit)
                }
            }
        }
    )

    try {
        complete.await()
    } finally {
        removeListener(listener)
    }
}

suspend fun Member.getUserDescriptor(): UserDescriptor = suspendCancellableCoroutine { continuation ->
    getUserDescriptor(object : CallbackListener<UserDescriptor>() {

        override fun onSuccess(descriptor: UserDescriptor) = continuation.resume(descriptor)

        override fun onError(errorInfo: ErrorInfo) = continuation.resumeWithException(ChatException(errorInfo))
    })
}

suspend fun Members.removeMember(identity: String): Unit = suspendCancellableCoroutine { continuation ->
    removeByIdentity(identity, object : StatusListener() {

        override fun onSuccess() = continuation.resume(Unit)

        override fun onError(errorInfo: ErrorInfo) = continuation.resumeWithException(ChatException(errorInfo))
    })
}

suspend fun Members.addMember(identity: String): Unit = suspendCancellableCoroutine { continuation ->
    addByIdentity(identity, object : StatusListener() {

        override fun onSuccess() = continuation.resume(Unit)

        override fun onError(errorInfo: ErrorInfo) = continuation.resumeWithException(ChatException(errorInfo))
    })
}

suspend fun Channel.getMemberCount(): Long = suspendCancellableCoroutine { continuation ->
    getMembersCount(object : CallbackListener<Long>() {

        override fun onSuccess(count: Long) = continuation.resume(count)

        override fun onError(errorInfo: ErrorInfo) = continuation.resumeWithException(ChatException(errorInfo))
    })
}

suspend fun Channel.getMessageCount(): Long = suspendCancellableCoroutine { continuation ->
    getMessagesCount(object : CallbackListener<Long>() {

        override fun onSuccess(count: Long) = continuation.resume(count)

        override fun onError(errorInfo: ErrorInfo) = continuation.resumeWithException(ChatException(errorInfo))
    })
}

suspend fun Channel.getUnconsumedMessageCount(): Long? = suspendCancellableCoroutine { continuation ->
    getUnconsumedMessagesCount(object : CallbackListener<Long?>() {

        override fun onSuccess(count: Long?) = continuation.resume(count)

        override fun onError(errorInfo: ErrorInfo) = continuation.resumeWithException(ChatException(errorInfo))
    })
}

suspend fun Channels.getPublicChannelsList(): Paginator<ChannelDescriptor> = suspendCoroutine { continuation ->
    getPublicChannelsList(object : CallbackListener<Paginator<ChannelDescriptor>>() {

        override fun onSuccess(result: Paginator<ChannelDescriptor>) = continuation.resume(result)

        override fun onError(errorInfo: ErrorInfo) = continuation.resumeWithException(ChatException(errorInfo))
    })
}

suspend fun Channels.getUserChannelsList(): Paginator<ChannelDescriptor> = suspendCoroutine { continuation ->
    getUserChannelsList(object : CallbackListener<Paginator<ChannelDescriptor>>() {

        override fun onSuccess(result: Paginator<ChannelDescriptor>) = continuation.resume(result)

        override fun onError(errorInfo: ErrorInfo) = continuation.resumeWithException(ChatException(errorInfo))
    })
}

suspend fun Channels.getChannel(sidOrUniqueName: String): Channel = suspendCoroutine { continuation ->
    getChannel(sidOrUniqueName, object : CallbackListener<Channel>() {

        override fun onSuccess(result: Channel) = continuation.resume(result)

        override fun onError(errorInfo: ErrorInfo) = continuation.resumeWithException(ChatException(errorInfo))
    })
}

suspend fun Messages.getLastMessages(count: Int): List<Message> = suspendCoroutine { continuation ->
    getLastMessages(count, object : CallbackListener<List<Message>>() {

        override fun onSuccess(result: List<Message>) = continuation.resume(result)

        override fun onError(errorInfo: ErrorInfo) = continuation.resumeWithException(ChatException(errorInfo))
    })
}

suspend fun Messages.getMessagesBefore(index: Long, count: Int): List<Message> = suspendCoroutine { continuation ->
    getMessagesBefore(index, count, object : CallbackListener<List<Message>>() {

        override fun onSuccess(result: List<Message>) = continuation.resume(result)

        override fun onError(errorInfo: ErrorInfo) = continuation.resumeWithException(ChatException(errorInfo))
    })
}

suspend fun Messages.sendMessage(message: Message.Options): Message = suspendCoroutine { continuation ->
    sendMessage(message, object : CallbackListener<Message>() {

        override fun onSuccess(message: Message) = continuation.resume(message)

        override fun onError(errorInfo: ErrorInfo) = continuation.resumeWithException(ChatException(errorInfo))
    })
}

suspend fun Messages.advanceLastConsumedMessageIndex(index: Long): Long = suspendCoroutine { continuation ->
    advanceLastConsumedMessageIndexWithResult(index, object : CallbackListener<Long>() {

        override fun onSuccess(index: Long) = continuation.resume(index)

        override fun onError(errorInfo: ErrorInfo) = continuation.resumeWithException(ChatException(errorInfo))
    })
}

suspend fun Messages.getMessageByIndex(index: Long): Message = suspendCoroutine { continuation ->
    getMessageByIndex(index, object : CallbackListener<Message>() {

        override fun onSuccess(message: Message) = continuation.resume(message)

        override fun onError(errorInfo: ErrorInfo) = continuation.resumeWithException(ChatException(errorInfo))
    })
}

suspend fun Message.setAttributes(attributes: Attributes): Unit = suspendCoroutine { continuation ->
    setAttributes(attributes, object : StatusListener() {

        override fun onSuccess() = continuation.resume(Unit)

        override fun onError(errorInfo: ErrorInfo) = continuation.resumeWithException(ChatException(errorInfo))
    })
}

suspend fun <T> Paginator<T>.requestNextPage(): Paginator<T> = suspendCoroutine { continuation ->
    requestNextPage(object : CallbackListener<Paginator<T>>() {

        override fun onSuccess(result: Paginator<T>) = continuation.resume(result)

        override fun onError(errorInfo: ErrorInfo) = continuation.resumeWithException(ChatException(errorInfo))
    })
}

suspend fun Channels.createChannel(friendlyName: String, channelType: Channel.ChannelType): Channel = suspendCoroutine { continuation ->
    createChannel(friendlyName, channelType, object : CallbackListener<Channel>() {
        override fun onSuccess(result: Channel) = continuation.resume(result)

        override fun onError(errorInfo: ErrorInfo) = continuation.resumeWithException(ChatException(errorInfo))
    })
}

suspend fun Channel.join(): Unit = suspendCoroutine { continuation ->
    join(object : StatusListener() {
        override fun onSuccess() = continuation.resume(Unit)

        override fun onError(errorInfo: ErrorInfo) = continuation.resumeWithException(ChatException(errorInfo))
    })
}

suspend fun Channel.muteChannel(): Unit = suspendCoroutine { continuation ->
    setNotificationLevel(Channel.NotificationLevel.MUTED, object : StatusListener() {
        override fun onSuccess() = continuation.resume(Unit)

        override fun onError(errorInfo: ErrorInfo) = continuation.resumeWithException(ChatException(errorInfo))
    })
}

suspend fun Channel.unmuteChannel(): Unit = suspendCoroutine { continuation ->
    setNotificationLevel(Channel.NotificationLevel.DEFAULT, object : StatusListener() {
        override fun onSuccess() = continuation.resume(Unit)

        override fun onError(errorInfo: ErrorInfo) = continuation.resumeWithException(ChatException(errorInfo))
    })
}

suspend fun Channel.leave(): Unit = suspendCoroutine { continuation ->
    leave(object : StatusListener() {
        override fun onSuccess() = continuation.resume(Unit)

        override fun onError(errorInfo: ErrorInfo) = continuation.resumeWithException(ChatException(errorInfo))
    })
}

suspend fun Channel.destroy(): Unit = suspendCoroutine { continuation ->
    destroy(object : StatusListener() {
        override fun onSuccess() = continuation.resume(Unit)

        override fun onError(errorInfo: ErrorInfo) = continuation.resumeWithException(ChatException(errorInfo))
    })
}

suspend fun Channel.setFriendlyName(friendlyName: String): Unit = suspendCoroutine { continuation ->
    setFriendlyName(friendlyName, object : StatusListener() {
        override fun onSuccess() = continuation.resume(Unit)

        override fun onError(errorInfo: ErrorInfo) = continuation.resumeWithException(ChatException(errorInfo))
    })
}

suspend fun User.setFriendlyName(friendlyName: String): Unit = suspendCoroutine { continuation ->
    setFriendlyName(friendlyName, object : StatusListener() {
        override fun onSuccess() = continuation.resume(Unit)

        override fun onError(errorInfo: ErrorInfo) = continuation.resumeWithException(ChatException(errorInfo))
    })
}

suspend fun Message.Media.getContentTemporaryUrl(): String = suspendCoroutine { continuation ->
    getContentTemporaryUrl(object : CallbackListener<String>() {
        override fun onSuccess(contentTemporaryUrl: String) = continuation.resume(contentTemporaryUrl)

        override fun onError(errorInfo: ErrorInfo) = continuation.resumeWithException(ChatException(errorInfo))
    })
}

inline fun ChatClient.addListener(
    crossinline onChannelJoined: (channel: Channel) -> Unit = {},
    crossinline onChannelInvited: (channel: Channel) -> Unit = {},
    crossinline onChannelAdded: (channel: Channel) -> Unit = {},
    crossinline onChannelUpdated: (channel: Channel, reason: Channel.UpdateReason) -> Unit = { _, _ -> Unit },
    crossinline onChannelDeleted: (channel: Channel) -> Unit = {},
    crossinline onChannelSynchronizationChange: (channel: Channel) -> Unit = {},
    crossinline onError: (errorInfo: ErrorInfo) -> Unit = {},
    crossinline onUserUpdated: (user: User, reason: User.UpdateReason) -> Unit = { _, _ -> Unit },
    crossinline onUserSubscribed: (user: User) -> Unit = {},
    crossinline onUserUnsubscribed: (user: User) -> Unit = {},
    crossinline onClientSynchronization: (status: ChatClient.SynchronizationStatus) -> Unit = {},
    crossinline onNewMessageNotification: (channelSid: String, messageSid: String, messageIndex: Long) -> Unit = { _, _, _ -> Unit },
    crossinline onAddedToChannelNotification: (channelSid: String) -> Unit = {},
    crossinline onInvitedToChannelNotification: (channelSid: String) -> Unit = {},
    crossinline onRemovedFromChannelNotification: (channelSid: String) -> Unit = {},
    crossinline onNotificationSubscribed: () -> Unit = {},
    crossinline onNotificationFailed: (errorInfo: ErrorInfo) -> Unit = {},
    crossinline onConnectionStateChange: (state: ChatClient.ConnectionState) -> Unit = {},
    crossinline onTokenExpired: () -> Unit = {},
    crossinline onTokenAboutToExpire: () -> Unit = {}) {

    val listener = createClientListener(
        onChannelJoined,
        onChannelInvited,
        onChannelAdded,
        onChannelUpdated,
        onChannelDeleted,
        onChannelSynchronizationChange,
        onError,
        onUserUpdated,
        onUserSubscribed,
        onUserUnsubscribed,
        onClientSynchronization,
        onNewMessageNotification,
        onAddedToChannelNotification,
        onInvitedToChannelNotification,
        onRemovedFromChannelNotification,
        onNotificationSubscribed,
        onNotificationFailed,
        onConnectionStateChange,
        onTokenExpired,
        onTokenAboutToExpire)

    addListener(listener)
}

inline fun createClientListener(
    crossinline onChannelJoined: (channel: Channel) -> Unit = {},
    crossinline onChannelInvited: (channel: Channel) -> Unit = {},
    crossinline onChannelAdded: (channel: Channel) -> Unit = {},
    crossinline onChannelUpdated: (channel: Channel, reason: Channel.UpdateReason) -> Unit = { _, _ -> Unit },
    crossinline onChannelDeleted: (channel: Channel) -> Unit = {},
    crossinline onChannelSynchronizationChange: (channel: Channel) -> Unit = {},
    crossinline onError: (errorInfo: ErrorInfo) -> Unit = {},
    crossinline onUserUpdated: (user: User, reason: User.UpdateReason) -> Unit = { _, _ -> Unit },
    crossinline onUserSubscribed: (user: User) -> Unit = {},
    crossinline onUserUnsubscribed: (user: User) -> Unit = {},
    crossinline onClientSynchronization: (status: ChatClient.SynchronizationStatus) -> Unit = {},
    crossinline onNewMessageNotification: (channelSid: String, messageSid: String, messageIndex: Long) -> Unit = { _, _, _ -> Unit },
    crossinline onAddedToChannelNotification: (channelSid: String) -> Unit = {},
    crossinline onInvitedToChannelNotification: (channelSid: String) -> Unit = {},
    crossinline onRemovedFromChannelNotification: (channelSid: String) -> Unit = {},
    crossinline onNotificationSubscribed: () -> Unit = {},
    crossinline onNotificationFailed: (errorInfo: ErrorInfo) -> Unit = {},
    crossinline onConnectionStateChange: (state: ChatClient.ConnectionState) -> Unit = {},
    crossinline onTokenExpired: () -> Unit = {},
    crossinline onTokenAboutToExpire: () -> Unit = {}
): ChatClientListener = object : ChatClientListener {

    override fun onChannelJoined(channel: Channel) = onChannelJoined(channel)

    override fun onChannelInvited(channel: Channel) = onChannelInvited(channel)

    override fun onChannelAdded(channel: Channel) = onChannelAdded(channel)

    override fun onChannelUpdated(channel: Channel, reason: Channel.UpdateReason) = onChannelUpdated(channel, reason)

    override fun onChannelDeleted(channel: Channel) = onChannelDeleted(channel)

    override fun onChannelSynchronizationChange(channel: Channel) = onChannelSynchronizationChange(channel)

    override fun onError(errorInfo: ErrorInfo) = onError(errorInfo)

    override fun onUserUpdated(user: User, reason: User.UpdateReason) = onUserUpdated(user, reason)

    override fun onUserSubscribed(user: User) = onUserSubscribed(user)

    override fun onUserUnsubscribed(user: User) = onUserUnsubscribed(user)

    override fun onClientSynchronization(status: ChatClient.SynchronizationStatus) = onClientSynchronization(status)

    override fun onNewMessageNotification(channelSid: String, messageSid: String, messageIndex: Long) = onNewMessageNotification(channelSid, messageSid, messageIndex)

    override fun onAddedToChannelNotification(channelSid: String) = onAddedToChannelNotification(channelSid)

    override fun onInvitedToChannelNotification(channelSid: String) = onInvitedToChannelNotification(channelSid)

    override fun onRemovedFromChannelNotification(channelSid: String) = onRemovedFromChannelNotification(channelSid)

    override fun onNotificationSubscribed() = onNotificationSubscribed()

    override fun onNotificationFailed(errorInfo: ErrorInfo) = onNotificationFailed(errorInfo)

    override fun onConnectionStateChange(state: ChatClient.ConnectionState) = onConnectionStateChange(state)

    override fun onTokenExpired() = onTokenExpired()

    override fun onTokenAboutToExpire() = onTokenAboutToExpire()
}

inline fun Channel.addListener(
    crossinline onMessageAdded: (message: Message) -> Unit = {},
    crossinline onMessageUpdated: (message: Message, reason: Message.UpdateReason) -> Unit = { _, _ -> Unit },
    crossinline onMessageDeleted: (message: Message) -> Unit = {},
    crossinline onMemberAdded: (member: Member) -> Unit = {},
    crossinline onMemberUpdated: (member: Member, reason: Member.UpdateReason) -> Unit = { _, _ -> Unit },
    crossinline onMemberDeleted: (member: Member) -> Unit = {},
    crossinline onTypingStarted: (channel: Channel, member: Member) -> Unit = { _, _ -> Unit },
    crossinline onTypingEnded: (channel: Channel, member: Member) -> Unit = { _, _ -> Unit },
    crossinline onSynchronizationChanged: (channel: Channel) -> Unit = {}): ChannelListener {

    val listener = createChannelListener(
        onMemberAdded,
        onMemberUpdated,
        onMemberDeleted,
        onMessageAdded,
        onMessageUpdated,
        onMessageDeleted,
        onTypingStarted,
        onTypingEnded,
        onSynchronizationChanged
    )
    addListener(listener)
    return listener
}

inline fun createChannelListener(
    crossinline onMemberAdded: (member: Member) -> Unit = {},
    crossinline onMemberUpdated: (member: Member, updateReason: Member.UpdateReason) -> Unit = { _, _ -> Unit },
    crossinline onMemberDeleted: (member: Member) -> Unit = {},
    crossinline onMessageAdded: (message: Message) -> Unit = {},
    crossinline onMessageUpdated: (message: Message, updateReason: Message.UpdateReason) -> Unit = { _, _ -> Unit },
    crossinline onMessageDeleted: (message: Message) -> Unit = {},
    crossinline onTypingStarted: (channel: Channel, member: Member) -> Unit = { _, _ -> Unit },
    crossinline onTypingEnded: (channel: Channel, member: Member) -> Unit = { _, _ -> Unit },
    crossinline onSynchronizationChanged: (channel: Channel) -> Unit = {}
): ChannelListener = object : ChannelListener {

    override fun onMemberAdded(member: Member) = onMemberAdded(member)

    override fun onMemberUpdated(member: Member, updateReason: Member.UpdateReason) =
        onMemberUpdated(member, updateReason)

    override fun onMemberDeleted(member: Member) = onMemberDeleted(member)

    override fun onMessageAdded(message: Message) = onMessageAdded(message)

    override fun onMessageUpdated(message: Message, updateReason: Message.UpdateReason) =
        onMessageUpdated(message, updateReason)

    override fun onMessageDeleted(message: Message) = onMessageDeleted(message)

    override fun onTypingStarted(channel: Channel, member: Member) =
        onTypingStarted(channel, member)

    override fun onTypingEnded(channel: Channel, member: Member) =
        onTypingEnded(channel, member)

    override fun onSynchronizationChanged(channel: Channel) = onSynchronizationChanged(channel)

}

inline fun Message.Options.withMediaProgressListener(
    crossinline onStarted: () -> Unit = {},
    crossinline onProgress: (uploadedBytes: Long) -> Unit = {},
    crossinline onCompleted: () -> Unit = {}
): Message.Options = withMediaProgressListener(object : ProgressListener() {
    override fun onStarted() = onStarted()

    override fun onProgress(uploadedBytes: Long) = onProgress(uploadedBytes)

    override fun onCompleted(mediaSid: String?) = onCompleted()
})
