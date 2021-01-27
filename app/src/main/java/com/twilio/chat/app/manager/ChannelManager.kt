package com.twilio.chat.app.manager

import com.google.gson.Gson
import com.twilio.chat.Attributes
import com.twilio.chat.Message
import com.twilio.chat.app.common.DefaultDispatcherProvider
import com.twilio.chat.app.common.DispatcherProvider
import com.twilio.chat.app.common.enums.Direction
import com.twilio.chat.app.common.enums.Reaction
import com.twilio.chat.app.common.enums.SendStatus
import com.twilio.chat.app.common.extensions.*
import com.twilio.chat.app.common.getReactions
import com.twilio.chat.app.common.toMessageDataItem
import com.twilio.chat.app.data.ChatClientWrapper
import com.twilio.chat.app.data.localCache.entity.MessageDataItem
import com.twilio.chat.app.data.models.ReactionAttributes
import com.twilio.chat.app.repository.ChatRepository
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.InputStream
import java.util.*

interface ChannelManager {
    suspend fun sendTextMessage(text: String, uuid: String)
    suspend fun retrySendTextMessage(messageUuid: String)
    suspend fun sendMediaMessage(
        uri: String,
        inputStream: InputStream,
        fileName: String?,
        mimeType: String?,
        messageUuid: String
    )
    suspend fun retrySendMediaMessage(inputStream: InputStream, messageUuid: String)
    suspend fun updateMessageStatus(messageUuid: String, sendStatus: SendStatus)
    suspend fun updateMessageMediaDownloadStatus(
        index: Long,
        downloading: Boolean,
        downloadedBytes: Long,
        downloadedLocation: String?
    )
    suspend fun addRemoveReaction(index: Long, reaction: Reaction)
    suspend fun notifyMessageConsumed(index: Long)
    suspend fun typing()
    suspend fun getMediaContentTemporaryUrl(index: Long): String
    suspend fun setMessageMediaDownloadId(messageIndex: Long, id: Long)
}

class ChannelManagerImpl(
    private val channelSid: String,
    private val chatClient: ChatClientWrapper,
    private val chatRepository: ChatRepository,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ChannelManager {

    override suspend fun sendTextMessage(text: String, uuid: String) {
        val identity = chatClient.getChatClient().myIdentity
        val channel = chatClient.getChatClient().channels.getChannel(channelSid)
        val memberSid = channel.members.getMember(identity).sid
        val attributes = Attributes(uuid)
        val options = Message.options().withBody(text).withAttributes(attributes)
        val message = MessageDataItem(
            "",
            channelSid,
            memberSid,
            Message.Type.TEXT.value,
            identity,
            Date().time,
            text,
            -1,
            attributes.toString(),
            Direction.OUTGOING.value,
            SendStatus.SENDING.value,
            uuid
        )
        chatRepository.insertMessage(message)
        val sentMessage = channel.messages.sendMessage(options).toMessageDataItem(identity, uuid)
        chatRepository.updateMessageByUuid(sentMessage)
    }

    override suspend fun retrySendTextMessage(messageUuid: String) {
        val message = withContext(dispatchers.io()) { chatRepository.getMessageByUuid(messageUuid) } ?: return
        if (message.sendStatus == SendStatus.SENDING.value) return
        chatRepository.updateMessageByUuid(message.copy(sendStatus = SendStatus.SENDING.value))
        val identity = chatClient.getChatClient().myIdentity
        val attributes = Attributes(message.uuid)
        val options = Message.options().withBody(message.body).withAttributes(attributes)
        val sentMessage = chatClient
            .getChatClient()
            .channels
            .getChannel(channelSid)
            .messages
            .sendMessage(options)
            .toMessageDataItem(identity, message.uuid)

        chatRepository.updateMessageByUuid(sentMessage)
    }

    override suspend fun sendMediaMessage(
        uri: String,
        inputStream: InputStream,
        fileName: String?,
        mimeType: String?,
        messageUuid: String
    ) {
        val identity = chatClient.getChatClient().myIdentity
        val channel = chatClient.getChatClient().channels.getChannel(channelSid)
        val memberSid = channel.members.getMember(identity).sid
        val attributes = Attributes(messageUuid)
        val options = getMediaMessageOptions(uri, inputStream, fileName, mimeType, messageUuid)
        val message = MessageDataItem(
            "",
            channelSid,
            memberSid,
            Message.Type.MEDIA.value,
            identity,
            Date().time,
            null,
            -1,
            attributes.toString(),
            Direction.OUTGOING.value,
            SendStatus.SENDING.value,
            messageUuid,
            mediaFileName = fileName,
            mediaUploadUri = uri,
            mediaType = mimeType
        )
        chatRepository.insertMessage(message)
        val sentMessage = channel.messages.sendMessage(options).toMessageDataItem(identity, messageUuid)
        chatRepository.updateMessageByUuid(sentMessage)
    }

    override suspend fun retrySendMediaMessage(
        inputStream: InputStream,
        messageUuid: String
    ) {
        val message = withContext(dispatchers.io()) { chatRepository.getMessageByUuid(messageUuid) } ?: return
        if (message.sendStatus == SendStatus.SENDING.value) return
        if (message.mediaUploadUri == null) {
            Timber.w("Missing mediaUploadUri in retrySendMediaMessage: $message")
            return
        }
        chatRepository.updateMessageByUuid(message.copy(sendStatus = SendStatus.SENDING.value))
        val identity = chatClient.getChatClient().myIdentity
        val options = getMediaMessageOptions(message.mediaUploadUri, inputStream,
            message.mediaFileName, message.mediaType, messageUuid)

        val sentMessage = chatClient
            .getChatClient()
            .channels
            .getChannel(channelSid)
            .messages
            .sendMessage(options)
            .toMessageDataItem(identity, message.uuid)

        chatRepository.updateMessageByUuid(sentMessage)
    }

    private fun getMediaMessageOptions(
        uri: String,
        inputStream: InputStream,
        fileName: String?,
        mimeType: String?,
        messageUuid: String
    ): Message.Options {
        val attributes = Attributes(messageUuid)
        var options = Message.options().withMedia(inputStream, mimeType).withAttributes(attributes)
            .withMediaProgressListener(
                onStarted = {
                    Timber.d("Upload started for $uri")
                    chatRepository.updateMessageMediaUploadStatus(messageUuid, uploading = true)
                },
                onProgress = { uploadedBytes ->
                    Timber.d("Upload progress for $uri: $uploadedBytes bytes")
                    chatRepository.updateMessageMediaUploadStatus(
                        messageUuid,
                        uploadedBytes = uploadedBytes
                    )
                },
                onCompleted = {
                    Timber.d("Upload for $uri complete")
                    chatRepository.updateMessageMediaUploadStatus(messageUuid, uploading = false)
                }
            )
        if (fileName != null) {
            options = options.withMediaFileName(fileName)
        }
        return options
    }

    override suspend fun updateMessageStatus(messageUuid: String, sendStatus: SendStatus) {
        chatRepository.updateMessageStatus(messageUuid, sendStatus.value)
    }

    override suspend fun updateMessageMediaDownloadStatus(
        index: Long,
        downloading: Boolean,
        downloadedBytes: Long,
        downloadedLocation: String?
    ) {
        val message = chatClient.getChatClient().channels.getChannel(channelSid).messages.getMessageByIndex(index)
        chatRepository.updateMessageMediaDownloadStatus(
            messageSid = message.sid,
            downloadedBytes = downloadedBytes,
            downloadLocation = downloadedLocation,
            downloading = downloading
        )
    }

    override suspend fun addRemoveReaction(index: Long, reaction: Reaction) {
        val identity = chatClient.getChatClient().myIdentity
        val message = chatClient
            .getChatClient()
            .channels
            .getChannel(channelSid)
            .messages
            .getMessageByIndex(index)
        val attributes = message.attributes
        val reactions = getReactions("$attributes").toMutableMap()
        val memberSids = reactions.getOrPut(reaction.value, ::emptySet).toMutableSet()

        if (identity in memberSids) {
            memberSids -= identity
        } else {
            memberSids += identity
        }

        reactions[reaction.value] = memberSids
        val reactionAttributes = ReactionAttributes(reactions)
        Timber.d("Updating reactions: $reactions")
        message.setAttributes(Attributes(JSONObject(Gson().toJson(reactionAttributes))))
    }

    override suspend fun notifyMessageConsumed(index: Long) {
        val messages = chatClient.getChatClient().channels.getChannel(channelSid).messages
        if (index > messages.lastConsumedMessageIndex ?: -1) {
            messages.advanceLastConsumedMessageIndex(index)
        }
    }

    override suspend fun typing() {
        chatClient.getChatClient().channels.getChannel(channelSid).typing()
    }

    override suspend fun getMediaContentTemporaryUrl(index: Long): String {
        val message = chatClient.getChatClient().channels.getChannel(channelSid).messages.getMessageByIndex(index)
        return message.media.getContentTemporaryUrl()
    }

    override suspend fun setMessageMediaDownloadId(messageIndex: Long, id: Long) {
        val message = chatClient.getChatClient().channels.getChannel(channelSid).messages.getMessageByIndex(messageIndex)
        chatRepository.updateMessageMediaDownloadStatus(messageSid = message.sid, downloadId = id)
    }
}
