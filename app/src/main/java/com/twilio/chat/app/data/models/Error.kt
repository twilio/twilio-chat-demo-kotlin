package com.twilio.chat.app.data.models

import com.twilio.chat.ErrorInfo
import com.twilio.chat.app.common.enums.ChatError

/**
 * Client creation response containing error info
 */
data class Error(val error: ChatError) : Response() {
    constructor(errorInfo: ErrorInfo) : this(ChatError.fromErrorInfo(errorInfo))
}
