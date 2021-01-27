package com.twilio.chat.app.data.models

data class MemberListViewItem(
    val sid: String,
    val identity: String,
    val channelSid: String,
    val friendlyName: String,
    val isOnline: Boolean
)
