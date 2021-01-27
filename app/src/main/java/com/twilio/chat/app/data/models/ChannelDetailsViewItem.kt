package com.twilio.chat.app.data.models

data class ChannelDetailsViewItem(
    val channelSid: String,
    val channelName: String,
    val createdBy: String,
    val dateCreated: String,
    val type: Int,
    val isMuted: Boolean = false
)
