package com.twilio.chat.app.common

import com.google.gson.Gson
import com.twilio.chat.app.common.enums.Direction
import com.twilio.chat.app.common.enums.Reaction
import androidx.core.net.toUri
import com.twilio.chat.*
import com.twilio.chat.app.common.enums.MessageType
import com.twilio.chat.app.common.enums.SendStatus
import com.twilio.chat.app.common.extensions.asDateString
import com.twilio.chat.app.common.extensions.asMessageCount
import com.twilio.chat.app.common.extensions.asTimeString
import com.twilio.chat.app.data.localCache.entity.ChannelDataItem
import com.twilio.chat.app.data.localCache.entity.MemberDataItem
import com.twilio.chat.app.data.localCache.entity.MessageDataItem
import com.twilio.chat.app.data.models.*

fun List<ChannelDescriptor>.toChannelDataItemList() : List<ChannelDataItem> {
    return this.map {
        ChannelDataItem(
            it.sid,
            it.friendlyName,
            it.attributes.toString(),
            it.uniqueName,
            it.dateUpdated.time,
            it.dateCreated.time,
            it.createdBy,
            it.membersCount,
            it.messagesCount,
            it.unconsumedMessagesCount,
            it.status.value
        )
    }
}

fun Channel.toChannelDataItem() : ChannelDataItem {
    return ChannelDataItem(
        this.sid,
        this.friendlyName,
        this.attributes.toString(),
        this.uniqueName,
        this.dateUpdatedAsDate?.time ?: 0,
        this.dateCreatedAsDate?.time ?: 0,
        this.createdBy,
        0,
        0,
        0,
        this.status.value,
        this.type.value,
        this.notificationLevel.value
    )
}

fun Message.toMessageDataItem(currentUserIdentity: String = member.identity, uuid: String = "") : MessageDataItem {
    return MessageDataItem(
        this.sid,
        this.channelSid,
        this.memberSid,
        this.type.value,
        this.author,
        this.dateCreatedAsDate.time,
        this.messageBody ?: "",
        this.messageIndex,
        this.attributes.toString(),
        if (this.author == currentUserIdentity) Direction.OUTGOING.value else Direction.INCOMING.value,
        if (this.author == currentUserIdentity) SendStatus.SENT.value else SendStatus.UNDEFINED.value,
        uuid,
        if (this.type == Message.Type.MEDIA) this.media.sid else null,
        if (this.type == Message.Type.MEDIA) this.media.fileName else null,
        if (this.type == Message.Type.MEDIA) this.media.type else null,
        if (this.type == Message.Type.MEDIA) this.media.size else null
    )
}

fun MessageDataItem.toMessageListViewItem() : MessageListViewItem {
    return MessageListViewItem(
        this.sid,
        this.uuid,
        this.index,
        Direction.fromInt(this.direction),
        this.author,
        this.body ?: "",
        this.dateCreated.asDateString(),
        SendStatus.fromInt(sendStatus),
        getReactions(attributes).asReactionList(),
        MessageType.fromInt(this.type),
        this.mediaSid,
        this.mediaFileName,
        this.mediaType,
        this.mediaSize,
        this.mediaUri?.toUri(),
        this.mediaDownloadId,
        this.mediaDownloadedBytes,
        this.mediaDownloading,
        this.mediaUploading,
        this.mediaUploadedBytes,
        this.mediaUploadUri?.toUri()
    )
}

fun getReactions(attributes: String): Map<String, Set<String>> = try {
    Gson().fromJson(attributes, ReactionAttributes::class.java).reactions
} catch (e: Exception) {
    emptyMap()
}

fun Map<String, Set<String>>.asReactionList(): Map<Reaction, Set<String>> {
    val reactions: MutableMap<Reaction, Set<String>> = mutableMapOf()
    forEach {
        try {
            reactions[Reaction.fromString(it.key)] = it.value
        } catch (e: Exception) {}
    }
    return reactions
}

fun Member.asMemberDataItem(typing : Boolean = false, userDescriptor: UserDescriptor? = null) = MemberDataItem(
    sid = this.sid,
    channelSid = this.channel.sid,
    identity = this.identity,
    friendlyName = userDescriptor?.identity ?: "",
    isOnline = userDescriptor?.isOnline ?: false,
    lastConsumedMessageIndex = this.lastConsumedMessageIndex,
    lastConsumptionTimestamp = this.lastConsumptionTimestamp,
    typing = typing
)

fun User.asUserViewItem() = UserViewItem(
    friendlyName = this.friendlyName,
    identity = this.identity
)

fun ChannelDataItem.asChannelListViewItem() = ChannelListViewItem(
    this.sid,
    this.friendlyName,
    this.dateCreated.asDateString(),
    this.dateUpdated.asTimeString(),
    this.membersCount,
    this.messagesCount.asMessageCount(),
    this.participatingStatus,
    this.type == Channel.ChannelType.PRIVATE.value,
    this.notificationLevel == Channel.NotificationLevel.MUTED.value
)

fun ChannelDataItem.asChannelDetailsViewItem() = ChannelDetailsViewItem(
    this.sid,
    this.friendlyName,
    this.createdBy,
    this.dateCreated.asDateString(),
    this.type,
    this.notificationLevel == Channel.NotificationLevel.MUTED.value
)

fun MemberDataItem.toMemberListViewItem() = MemberListViewItem(
    channelSid = this.channelSid,
    sid = this.sid,
    identity = this.identity,
    friendlyName = this.friendlyName,
    isOnline = this.isOnline
)

fun List<ChannelDataItem>.asChannelListViewItems() = map { it.asChannelListViewItem() }

fun List<Message>.asMessageDataItems(identity: String) = map { it.toMessageDataItem(identity) }

fun List<MessageDataItem>.asMessageListViewItems() = map { it.toMessageListViewItem() }

fun List<MemberDataItem>.asMemberListViewItems() = map { it.toMemberListViewItem() }

fun List<ChannelListViewItem>.merge(oldChannelList: List<ChannelListViewItem>?): List<ChannelListViewItem> {
    val oldChannelMap = oldChannelList?.associate { it.sid to it } ?: return this
    return map { item ->
        val oldItem = oldChannelMap[item.sid] ?: return@map item
        item.copy(isLoading = oldItem.isLoading)
    }
}
