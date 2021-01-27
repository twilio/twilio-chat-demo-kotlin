package com.twilio.chat.app.data.models

data class RepositoryResult<T>(
    val data: T,
    val requestStatus: RepositoryRequestStatus
)
