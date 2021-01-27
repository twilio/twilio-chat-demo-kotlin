package com.twilio.chat.app.common.enums

enum class Direction(val value: Int) {
    INCOMING(0),
    OUTGOING(1);

    companion object {
        private val valuesMap = values().associateBy { it.value }
        fun fromInt(value: Int) = valuesMap[value] ?: error("Invalid value $value for Direction")
    }
}
