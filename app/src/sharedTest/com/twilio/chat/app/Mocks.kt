package com.twilio.chat.app

import com.twilio.chat.Channel
import com.twilio.chat.Message
import com.twilio.chat.app.common.enums.Direction
import com.twilio.chat.app.common.enums.SendStatus
import com.twilio.chat.app.data.localCache.entity.ChannelDataItem
import com.twilio.chat.app.data.localCache.entity.MemberDataItem
import com.twilio.chat.app.data.localCache.entity.MessageDataItem
import com.twilio.chat.app.data.models.ChannelDetailsViewItem
import com.twilio.chat.app.data.models.UserViewItem
import java.util.*
import kotlin.collections.ArrayList

fun createTestChannelDataItem(sid: String = UUID.randomUUID().toString(),
                              friendlyName: String = "",
                              attributes: String = "\"\"",
                              uniqueName: String = "",
                              dateUpdated: Long = 0,
                              dateCreated: Long = 0,
                              createdBy: String = "",
                              membersCount: Long = 0,
                              messagesCount: Long = 0,
                              unconsumedMessagesCount: Long = 0,
                              participatingStatus: Int = 0,
                              type: Int = 0,
                              notificationLevel: Int = 0
) = ChannelDataItem(sid, friendlyName, attributes, uniqueName, dateUpdated, dateCreated, createdBy,
    membersCount, messagesCount, unconsumedMessagesCount, participatingStatus, type, notificationLevel)

fun createTestMessageDataItem(sid: String = UUID.randomUUID().toString(),
                              channelSid: String = UUID.randomUUID().toString(),
                              memberSid: String = UUID.randomUUID().toString(),
                              type: Int = 0,
                              author: String = "",
                              dateCreated: Long = 0,
                              body: String = "",
                              index: Long = 0,
                              attributes: String = "",
                              direction: Int = 0,
                              sendStatus: Int = 0,
                              uuid: String = UUID.randomUUID().toString(),
                              mediaSid: String? = null,
                              mediaFileName: String? = null,
                              mediaType: String? = null,
                              mediaSize: Long? = null,
                              mediaUri: String? = null,
                              mediaDownloadId: Long? = null,
                              mediaDownloadedBytes: Long? = null,
                              mediaDownloading: Boolean = false,
                              mediaUploading: Boolean = false,
                              mediaUploadedBytes: Long? = null,
                              mediaUploadUri: String? = null
) = MessageDataItem(sid, channelSid, memberSid, type, author, dateCreated, body,
    index, attributes, direction, sendStatus, uuid, mediaSid, mediaFileName, mediaType,
    mediaSize, mediaUri, mediaDownloadId, mediaDownloadedBytes, mediaDownloading, mediaUploading,
    mediaUploadedBytes, mediaUploadUri)

fun createTestMemberDataItem(
    sid: String = "",
    identity: String = "",
    channelSid: String = "",
    friendlyName: String = "",
    lastConsumedMessageIndex: Long? = null,
    lastConsumptionTimestamp: String? = null,
    typing: Boolean = false
) = MemberDataItem(sid, identity, channelSid, friendlyName, true,
    lastConsumedMessageIndex, lastConsumptionTimestamp, typing)

fun createTestChannelDetailsViewItem(
    channelName: String = "",
    createdBy: String = "",
    createdOn: String = "",
    isMuted:
    Boolean = false
) = ChannelDetailsViewItem("", channelName, createdBy, createdOn, 0, isMuted)

fun createTestUserViewItem(friendlyName: String = "", identity: String = "")
        = UserViewItem(friendlyName = friendlyName, identity = identity)

fun getMockedChannels(count: Int, name: String,
                      status: Channel.ChannelStatus = Channel.ChannelStatus.NOT_PARTICIPATING,
                      notificationLevel: Channel.NotificationLevel = Channel.NotificationLevel.DEFAULT
): ArrayList<ChannelDataItem> {
    val channels = arrayListOf<ChannelDataItem>()
    for(index in 0 until count) {
        channels.add(createTestChannelDataItem(friendlyName = "${name}_$index",
            participatingStatus = status.value, notificationLevel = notificationLevel.value))
    }
    return channels
}

fun getMockedMessages(count: Int, body: String, channelSid: String, direction: Int = Direction.OUTGOING.value,
                      author: String = "", attributes: String = "", type: Message.Type = Message.Type.TEXT,
                      mediaFileName: String = "", mediaSize: Long = 0, mediaDownloading: Boolean = false,
                      mediaUri: String? = null, mediaDownloadedBytes: Long? = null,
                      sendStatus: SendStatus = SendStatus.UNDEFINED): List<MessageDataItem> {
    val messages = Array(count) { index ->
        createTestMessageDataItem(channelSid = channelSid, index = index.toLong(),
            body = "${body}_$index", direction = direction, author = author, attributes = attributes,
        type = type.value, mediaFileName = mediaFileName, mediaSize = mediaSize, mediaDownloading = mediaDownloading,
        mediaUri = mediaUri, mediaDownloadedBytes = mediaDownloadedBytes, sendStatus = sendStatus.value)
    }
    return messages.toList()
}

fun getMockedMembers(count: Int, name: String): ArrayList<MemberDataItem> {
    val members = arrayListOf<MemberDataItem>()
    for(index in 0 until count) {
        members.add(
            createTestMemberDataItem(friendlyName = "${name}_$index")
        )
    }
    return members
}
