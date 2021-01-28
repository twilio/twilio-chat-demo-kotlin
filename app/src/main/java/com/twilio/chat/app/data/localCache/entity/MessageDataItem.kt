package com.twilio.chat.app.data.localCache.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "message_table")
data class MessageDataItem(
    @PrimaryKey
    val sid: String,
    val channelSid: String,
    val memberSid: String,
    val type: Int,
    val author: String,
    val dateCreated: Long,
    val body: String?,
    val index: Long,
    val attributes: String,
    val direction: Int,
    val sendStatus: Int,
    val uuid: String,
    val mediaSid: String? = null,
    val mediaFileName: String? = null,
    val mediaType: String? = null,
    val mediaSize: Long? = null,
    val mediaUri: String? = null,
    val mediaDownloadId: Long? = null,
    val mediaDownloadedBytes: Long? = null,
    val mediaDownloading: Boolean = false,
    val mediaUploading: Boolean = false,
    val mediaUploadedBytes: Long? = null,
    val mediaUploadUri: String? = null
)
