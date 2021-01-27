package com.twilio.chat.app.data.models

import com.twilio.chat.ChatClient

/**
 * Client creation response containing successfully created chat client
 */
data class Client(val chatClient: ChatClient) : Response()
