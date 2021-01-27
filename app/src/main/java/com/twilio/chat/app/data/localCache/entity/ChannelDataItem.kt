package com.twilio.chat.app.data.localCache.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channel_table")
data class ChannelDataItem(
    @PrimaryKey
    val sid: String,
    val friendlyName: String,
    val attributes: String,
    val uniqueName: String,
    val dateUpdated: Long,
    val dateCreated: Long,
    val createdBy: String,
    val membersCount: Long,
    val messagesCount: Long,
    val unconsumedMessagesCount: Long,
    val participatingStatus: Int,
    val type: Int = 0,
    val notificationLevel: Int = 0
)
