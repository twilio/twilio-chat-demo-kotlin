package com.twilio.chat.app.manager

import com.twilio.chat.Channel
import com.twilio.chat.app.common.extensions.*
import com.twilio.chat.app.data.ChatClientWrapper

interface ChannelListManager {
    suspend fun createChannel(friendlyName: String, type: Channel.ChannelType): String
    suspend fun joinChannel(channelSid: String)
    suspend fun removeChannel(channelSid: String)
    suspend fun leaveChannel(channelSid: String)
    suspend fun muteChannel(channelSid: String)
    suspend fun unmuteChannel(channelSid: String)
    suspend fun renameChannel(channelSid: String, friendlyName: String)
}

class ChannelListManagerImpl(private val chatClient: ChatClientWrapper) : ChannelListManager {

    override suspend fun createChannel(friendlyName: String, type: Channel.ChannelType): String
            = chatClient.getChatClient().channels.createChannel(friendlyName, type).sid

    override suspend fun joinChannel(channelSid: String): Unit
            = chatClient.getChatClient().channels.getChannel(channelSid).join()

    override suspend fun removeChannel(channelSid: String): Unit
            = chatClient.getChatClient().channels.getChannel(channelSid).destroy()

    override suspend fun leaveChannel(channelSid: String): Unit
            = chatClient.getChatClient().channels.getChannel(channelSid).leave()

    override suspend fun muteChannel(channelSid: String): Unit
            = chatClient.getChatClient().channels.getChannel(channelSid).muteChannel()

    override suspend fun unmuteChannel(channelSid: String): Unit
            = chatClient.getChatClient().channels.getChannel(channelSid).unmuteChannel()

    override suspend fun renameChannel(channelSid: String, friendlyName: String)
            = chatClient.getChatClient().channels.getChannel(channelSid).setFriendlyName(friendlyName)

}
