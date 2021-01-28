package com.twilio.chat.app.manager

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import com.twilio.chat.NotificationPayload
import com.twilio.chat.app.R
import com.twilio.chat.app.common.extensions.ChatException
import com.twilio.chat.app.common.extensions.registerFCMToken
import com.twilio.chat.app.data.ChatClientWrapper
import com.twilio.chat.app.data.CredentialStorage
import com.twilio.chat.app.ui.ChannelActivity
import com.twilio.chat.app.ui.ChannelListActivity
import timber.log.Timber

private const val NOTIFICATION_CHANNEL_ID = "twilio_notification_id"
private const val NOTIFICATION_NAME = "Twilio Notification"
private const val NOTIFICATION_ID = 1234

interface FCMManager : LifecycleObserver {
    suspend fun onNewToken(token: String)
    suspend fun onMessageReceived(payload: NotificationPayload)
    fun getTargetIntent(type: NotificationPayload.Type, channelSid: String): Intent
    fun showNotification(payload: NotificationPayload)
}

class FCMManagerImpl(
    private val context: Context,
    private val chatClient: ChatClientWrapper,
    private val credentialStorage: CredentialStorage,
    private var isBackgrounded: Boolean = false
) : FCMManager {

    private val notificationManager by lazy { context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override suspend fun onNewToken(token: String) {
        Timber.d("FCM Token received: $token")
        try {
            chatClient.getChatClient().registerFCMToken(token)
            credentialStorage.fcmToken = token
        } catch (e: ChatException) {
            Timber.d("Failed to register FCM token")
        }
    }

    override suspend fun onMessageReceived(payload: NotificationPayload) {
        chatClient.getChatClient().handleNotification(payload)
        Timber.d("Message received: $payload, ${payload.type}, $isBackgrounded")
        // Ignore everything we don't support
        if (payload.type == NotificationPayload.Type.UNKNOWN) return

        if (isBackgrounded) {
            showNotification(payload)
        }
    }

    override fun getTargetIntent(type: NotificationPayload.Type, channelSid: String): Intent {
        return when (type) {
            NotificationPayload.Type.NEW_MESSAGE -> ChannelActivity.getStartIntent(context, channelSid)
            NotificationPayload.Type.ADDED_TO_CHANNEL -> ChannelActivity.getStartIntent(context, channelSid)
            NotificationPayload.Type.INVITED_TO_CHANNEL -> ChannelActivity.getStartIntent(context, channelSid)
            NotificationPayload.Type.REMOVED_FROM_CHANNEL -> ChannelListActivity.getStartIntent(context)
            else -> ChannelListActivity.getStartIntent(context)
        }
    }

    override fun showNotification(payload: NotificationPayload) {
        val intent = getTargetIntent(payload.type, payload.channelSid)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT)

        val title = when (payload.type) {
            NotificationPayload.Type.NEW_MESSAGE -> context.getString(R.string.notification_new_message)
            NotificationPayload.Type.ADDED_TO_CHANNEL -> context.getString(R.string.notification_added_to_channel)
            NotificationPayload.Type.INVITED_TO_CHANNEL -> context.getString(R.string.notification_invited_to_channel)
            NotificationPayload.Type.REMOVED_FROM_CHANNEL -> context.getString(R.string.notification_removed_from_channel)
            else -> context.getString(R.string.notification_generic)
        }

        val notificationBuilder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(payload.body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(Color.rgb(214, 10, 37))

        val soundFileName = payload.sound
        if (context.resources.getIdentifier(soundFileName, "raw", context.packageName) != 0) {
            val sound = Uri.parse("android.resource://${context.packageName}/raw/$soundFileName")
            notificationBuilder.setSound(sound)
            Timber.d("Playing specified sound $soundFileName")
        } else {
            notificationBuilder.setDefaults(Notification.DEFAULT_SOUND)
            Timber.d("Playing default sound")
        }
        val notification = notificationBuilder.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(notificationChannel)
        }
        Timber.d("Showing notification")
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onAppBackgrounded() {
        isBackgrounded = true
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onAppForegrounded() {
        isBackgrounded = false
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
