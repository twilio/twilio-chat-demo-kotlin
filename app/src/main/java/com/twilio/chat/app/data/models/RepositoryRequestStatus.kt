package com.twilio.chat.app.data.models

import com.twilio.chat.app.common.enums.ChatError

sealed class RepositoryRequestStatus {
    object FETCHING : RepositoryRequestStatus()
    object SUBSCRIBING : RepositoryRequestStatus()
    object COMPLETE : RepositoryRequestStatus()
    class Error(val error: ChatError) : RepositoryRequestStatus()
}
