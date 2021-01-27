package com.twilio.chat.app.data.models

data class ChannelListViewItem(
    val sid: String,
    val name: String,
    val dateCreated: String,
    val dateUpdated: String,
    val memberCount: Long,
    val messageCount: String,
    val participatingStatus: Int,
    val isLocked: Boolean,
    val isMuted: Boolean = false,
    val isLoading: Boolean = false
)
