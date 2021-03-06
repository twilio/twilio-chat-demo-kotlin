package com.twilio.chat.app.services

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.twilio.chat.NotificationPayload
import com.twilio.chat.app.common.DefaultDispatcherProvider
import com.twilio.chat.app.common.injector
import kotlinx.coroutines.*
import timber.log.Timber

class FCMListenerService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(DefaultDispatcherProvider().io() + SupervisorJob())
    private fun launch(block: suspend CoroutineScope.() -> Unit) = serviceScope.launch(
        context = CoroutineExceptionHandler { _, e -> Timber.e(e, "Coroutine failed ${e.localizedMessage}") },
        block = block
    )
    private val fcmManager by lazy { injector.createFCMManager(application) }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        launch {
            fcmManager.onNewToken(token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        launch {
            Timber.d("onMessageReceived for FCM from: ${remoteMessage.from}")
            // Check if message contains a data payload.
            if (remoteMessage.data.isNotEmpty()) {
                Timber.d("Data Message Body: ${remoteMessage.data}")
                fcmManager.onMessageReceived(NotificationPayload(remoteMessage.data))
            }
        }
    }
}
