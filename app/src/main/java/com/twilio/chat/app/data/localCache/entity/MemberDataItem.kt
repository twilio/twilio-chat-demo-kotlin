package com.twilio.chat.app.data.localCache.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "member_table")
data class MemberDataItem(
    @PrimaryKey
    val sid: String,
    val identity: String,
    val channelSid: String,
    val friendlyName: String,
    val isOnline: Boolean,
    val lastConsumedMessageIndex: Long?,
    val lastConsumptionTimestamp: String?,
    val typing: Boolean = false
)
