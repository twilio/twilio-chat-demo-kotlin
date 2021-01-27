package com.twilio.chat.app.manager

import com.twilio.chat.app.common.extensions.setFriendlyName
import com.twilio.chat.app.data.ChatClientWrapper

interface UserManager {
    suspend fun setFriendlyName(friendlyName:String)
}

class UserManagerImpl(private val chatClient: ChatClientWrapper) : UserManager {

    override suspend fun setFriendlyName(friendlyName: String)
            = chatClient.getChatClient().users.myUser.setFriendlyName(friendlyName)

}
