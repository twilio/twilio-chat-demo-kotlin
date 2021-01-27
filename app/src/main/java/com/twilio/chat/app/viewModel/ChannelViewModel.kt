package com.twilio.chat.app.viewModel

import android.app.DownloadManager
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import androidx.paging.PagedList
import com.twilio.chat.app.common.SingleLiveEvent
import com.twilio.chat.app.common.enums.ChatError
import com.twilio.chat.app.common.enums.Reaction
import com.twilio.chat.app.common.enums.SendStatus
import com.twilio.chat.app.common.extensions.*
import com.twilio.chat.app.data.models.MessageListViewItem
import com.twilio.chat.app.data.models.RepositoryRequestStatus
import com.twilio.chat.app.manager.ChannelManager
import com.twilio.chat.app.repository.ChatRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.InputStream
import java.util.*

const val MESSAGE_COUNT = 50

class ChannelViewModel(
    private val appContext: Context,
    val channelSid: String,
    private val chatRepository: ChatRepository,
    private val channelManager: ChannelManager
) : ViewModel() {

    val channelName = SingleLiveEvent<String>()
    val messageItems = chatRepository.getMessages(channelSid, MESSAGE_COUNT)
        .onEach { repositoryResult ->
            if (repositoryResult.requestStatus is RepositoryRequestStatus.Error) {
                onMessageError.postValue(ChatError.MESSAGE_FETCH_FAILED)
            }
        }
        .asLiveData(viewModelScope.coroutineContext)
        .map { it.data }
    val onMessageError = SingleLiveEvent<ChatError>()
    val onMessageSent = SingleLiveEvent<Unit>()
    var selectedMessageIndex: Long = -1
    val typingMembersList = chatRepository.getTypingMembers(channelSid)
        .map { members -> members.map { member -> member.identity } }
        .distinctUntilChanged()
        .asLiveData(viewModelScope.coroutineContext)

    private val messagesObserver : Observer<PagedList<MessageListViewItem>>  =
        Observer {
            it.forEach { message ->
                if (message.mediaDownloading && message.mediaDownloadId != null) {
                    if (updateMessageMediaDownloadState(message.index, message.mediaDownloadId)) {
                        observeMessageMediaDownload(message.index, message.mediaDownloadId)
                    }
                }
            }
        }

    init {
        Timber.d("init: $channelSid")
        viewModelScope.launch {
            getChannelResult()
        }
        messageItems.observeForever(messagesObserver)
    }

    override fun onCleared() {
        messageItems.removeObserver(messagesObserver)
    }

    private suspend fun getChannelResult() {
        chatRepository.getChannel(channelSid).collect { result ->
            if (result.requestStatus is RepositoryRequestStatus.Error) {
                onMessageError.value = ChatError.CHANNEL_GET_FAILED
                return@collect
            }
            channelName.value = result.data?.friendlyName
        }
    }

    fun sendTextMessage(message: String) = viewModelScope.launch {
        val messageUuid = UUID.randomUUID().toString()
        try {
            channelManager.sendTextMessage(message, messageUuid)
            onMessageSent.call()
            Timber.d("Message sent: $messageUuid")
        } catch (e: ChatException) {
            channelManager.updateMessageStatus(messageUuid, SendStatus.ERROR)
            onMessageError.value = ChatError.MESSAGE_SEND_FAILED
        }
    }

    fun resendTextMessage(messageUuid: String) = viewModelScope.launch {
        try {
            channelManager.retrySendTextMessage(messageUuid)
            onMessageSent.call()
            Timber.d("Message re-sent: $messageUuid")
        } catch (e: ChatException) {
            channelManager.updateMessageStatus(messageUuid, SendStatus.ERROR)
            onMessageError.value = ChatError.MESSAGE_SEND_FAILED
        }
    }

    fun sendMediaMessage(uri: String, inputStream: InputStream, fileName: String?, mimeType: String?) = viewModelScope.launch {
        val messageUuid = UUID.randomUUID().toString()
        try {
            channelManager.sendMediaMessage(uri, inputStream, fileName, mimeType, messageUuid)
            onMessageSent.call()
            Timber.d("Media message sent: $messageUuid")
        } catch (e: ChatException) {
            channelManager.updateMessageStatus(messageUuid, SendStatus.ERROR)
            onMessageError.value = ChatError.MESSAGE_SEND_FAILED
        }
    }

    fun resendMediaMessage(inputStream: InputStream, messageUuid: String) = viewModelScope.launch {
        try {
            channelManager.retrySendMediaMessage(inputStream, messageUuid)
            onMessageSent.call()
            Timber.d("Media re-sent: $messageUuid")
        } catch (e: ChatException) {
            channelManager.updateMessageStatus(messageUuid, SendStatus.ERROR)
            onMessageError.value = ChatError.MESSAGE_SEND_FAILED
        }
    }

    fun handleMessageDisplayed(messageIndex: Long) = viewModelScope.launch {
        try {
            channelManager.notifyMessageConsumed(messageIndex)
        } catch (e: ChatException) {
            // Ignored
        }
    }

    fun typing() = viewModelScope.launch {
        Timber.d("Typing in channel $channelSid")
        channelManager.typing()
    }

    fun addRemoveReaction(reaction: Reaction) = viewModelScope.launch {
        try {
            channelManager.addRemoveReaction(selectedMessageIndex, reaction)
        } catch (e: ChatException) {
            onMessageError.value = ChatError.REACTION_UPDATE_FAILED
        }
    }

    suspend fun getMediaMessageFileSource(messageIndex: Long): String? {
        try {
            return channelManager.getMediaContentTemporaryUrl(messageIndex)
        } catch (chatException: ChatException) {
            onMessageError.value = chatException.error
        }
        return null
    }

    fun updateMessageMediaDownloadStatus(
        messageIndex: Long,
        downloading: Boolean,
        downloadedBytes: Long,
        downloadedLocation: String? = null
    ) = viewModelScope.launch {
        channelManager.updateMessageMediaDownloadStatus(messageIndex, downloading, downloadedBytes, downloadedLocation)
    }

    fun startMessageMediaDownload(messageIndex: Long, fileName: String?) = viewModelScope.launch {
        Timber.d("Start file download for message index $messageIndex")

        val sourceUri =
            Uri.parse(getMediaMessageFileSource(messageIndex) ?: return@launch)
        val downloadManager =
            appContext.getSystemService(AppCompatActivity.DOWNLOAD_SERVICE) as DownloadManager
        val downloadRequest = DownloadManager.Request(sourceUri).apply {
            setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_DOWNLOADS,
                fileName ?: sourceUri.pathSegments.last()
            )
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        }
        val downloadId = downloadManager.enqueue(downloadRequest)
        Timber.d("Download enqueued with ID: $downloadId")

        channelManager.setMessageMediaDownloadId(messageIndex, downloadId)
        observeMessageMediaDownload(messageIndex, downloadId)
    }

    private fun observeMessageMediaDownload(messageIndex: Long, downloadId: Long) {
        val downloadManager = appContext.getSystemService(AppCompatActivity.DOWNLOAD_SERVICE) as DownloadManager
        val downloadCursor = downloadManager.queryById(downloadId)
        val downloadObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (!updateMessageMediaDownloadState(messageIndex, downloadId)) {
                    Timber.d("Download $downloadId completed")
                    downloadCursor.unregisterContentObserver(this)
                    downloadCursor.close()
                }
            }
        }
        downloadCursor.registerContentObserver(downloadObserver)
    }

    /**
     * Notifies the view model of the current download state
     * @return true if the download is still in progress
     */
    private fun updateMessageMediaDownloadState(messageIndex: Long, downloadId: Long) : Boolean {
        val downloadManager = appContext.getSystemService(AppCompatActivity.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = downloadManager.queryById(downloadId)

        if (!cursor.moveToFirst()) {
            cursor.close()
            return false
        }

        val status = cursor.getInt(DownloadManager.COLUMN_STATUS)
        val downloadInProgress = status != DownloadManager.STATUS_FAILED && status != DownloadManager.STATUS_SUCCESSFUL
        val downloadedBytes = cursor.getLong(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
        Timber.d("Download status changed. Status: $status, downloaded bytes: $downloadedBytes")

        updateMessageMediaDownloadStatus(messageIndex, downloadInProgress, downloadedBytes)

        when (status) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                val downloadedFile = cursor.getString(DownloadManager.COLUMN_LOCAL_URI).toUri().toFile()
                val downloadedLocation = FileProvider.getUriForFile(appContext, "com.twilio.chat.app.fileprovider", downloadedFile).toString()
                updateMessageMediaDownloadStatus(messageIndex, false, downloadedBytes, downloadedLocation)
            }
            DownloadManager.STATUS_FAILED -> {
                onMessageError.value = ChatError.MESSAGE_MEDIA_DOWNLOAD_FAILED
                Timber.w("Message media download failed. Failure reason: %s", cursor.getString(DownloadManager.COLUMN_REASON))
            }
        }

        cursor.close()
        return downloadInProgress
    }
}
