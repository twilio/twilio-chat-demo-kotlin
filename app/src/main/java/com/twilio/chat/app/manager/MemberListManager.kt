package com.twilio.chat.app.manager

import com.twilio.chat.app.common.extensions.*
import com.twilio.chat.app.data.ChatClientWrapper

interface MemberListManager {
    suspend fun addMember(identity: String)
    suspend fun removeMember(identity: String)
}

class MemberListManagerImpl(
    private val channelSid: String,
    private val chatClient: ChatClientWrapper
) : MemberListManager {

    override suspend fun addMember(identity: String) {
        val channel = chatClient.getChatClient().channels.getChannel(channelSid)
        channel.waitForSynchronization()
        channel.members.addMember(identity)
    }

    override suspend fun removeMember(identity: String) {
        val channel = chatClient.getChatClient().channels.getChannel(channelSid)
        channel.waitForSynchronization()
        channel.members.removeMember(identity)
    }
}
