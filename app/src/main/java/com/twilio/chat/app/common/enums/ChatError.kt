package com.twilio.chat.app.common.enums

import com.twilio.chat.ErrorInfo

enum class ChatError(val code: Int, val message: String) {
    UNKNOWN(-1, "Unknown error"),
    NO_ERROR(55, "No error"),
    INVALID_USERNAME(56, "Username not valid"),
    INVALID_PASSWORD(57, "Password not valid"),
    INVALID_USERNAME_AND_PASSWORD(58, "Username and password not valid"),
    TOKEN_ERROR(59, "Could not get token"),
    GENERIC_ERROR(60, "Could not create client"),
    TOKEN_ACCESS_DENIED(61, "Access denied"),
    EMPTY_CREDENTIALS(62, "No credentials in storage"),
    CHANNEL_JOIN_FAILED(63, "Failed to join channel"),
    CHANNEL_CREATE_FAILED(64, "Failed to create channel"),
    CHANNEL_REMOVE_FAILED(65, "Failed to destroy channel"),
    CHANNEL_LEAVE_FAILED(66, "Failed to leave channel"),
    CHANNEL_FETCH_USER_FAILED(67, "Failed to fetch user channels"),
    CHANNEL_FETCH_PUBLIC_FAILED(68, "Failed to fetch user channels"),
    CHANNEL_MUTE_FAILED(69, "Failed to mute channel"),
    CHANNEL_UNMUTE_FAILED(70, "Failed to unmute channel"),
    CHANNEL_RENAME_FAILED(71, "Failed to rename channel"),
    CHANNEL_GET_FAILED(72, "Failed to get channel"),
    MESSAGE_FETCH_FAILED(73, "Failed to fetch messages"),
    MESSAGE_SEND_FAILED(74, "Failed to send message"),
    REACTION_UPDATE_FAILED(75, "Failed to update reaction"),
    MEMBER_FETCH_FAILED(76, "Failed to fetch members"),
    MEMBER_ADD_FAILED(77, "Failed to add member"),
    MEMBER_REMOVE_FAILED(78, "Failed to remove member"),
    USER_UPDATE_FAILED(79, "Failed to update user"),
    MESSAGE_MEDIA_DOWNLOAD_FAILED(80, "Failed to download media");

    override fun toString() = "Error $code : $message"

    companion object {
        fun fromErrorInfo(errorInfo: ErrorInfo) = values().firstOrNull { it.code == errorInfo.code } ?: UNKNOWN
    }
}
