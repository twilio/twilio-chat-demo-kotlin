package com.twilio.chat.app.ui

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.firebase.messaging.RemoteMessage
import com.twilio.chat.NotificationPayload
import com.twilio.chat.app.data.ChatClientWrapper
import com.twilio.chat.app.data.CredentialStorage
import com.twilio.chat.app.manager.FCMManager
import com.twilio.chat.app.manager.FCMManagerImpl
import com.twilio.chat.app.testUtil.verifyActivityVisible
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val NOTIFICATION_WAIT_TIME = 5000L

@RunWith(AndroidJUnit4::class)
class FCMNotificationTest {

    private lateinit var credentialStorage: CredentialStorage
    private lateinit var fcmManager: FCMManager

    @Before
    fun setUp() {
        ChatClientWrapper.recreateInstance()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        credentialStorage = CredentialStorage(context)
        fcmManager = FCMManagerImpl(context, ChatClientWrapper.INSTANCE, credentialStorage)
    }

    @Test
    fun notificationNewMessage() {
        val messageBody = "New Message Notification"
        val bundle = Bundle().apply {
            putString("channel_id", "channel_id")
            putString("twi_body", messageBody)
            putString("twi_message_type", "twilio.channel.new_message")
        }
        val remoteMessage = RemoteMessage(bundle)
        clickOnNotification(remoteMessage)
        verifyActivityVisible<ChannelActivity>()
    }

    @Test
    fun notificationAddedToChannel() {
        val messageBody = "Added to channel"
        val bundle = Bundle().apply {
            putString("channel_id", "channel_id")
            putString("twi_body", messageBody)
            putString("twi_message_type", "twilio.channel.added_to_channel")
        }
        val remoteMessage = RemoteMessage(bundle)
        clickOnNotification(remoteMessage)
        verifyActivityVisible<ChannelActivity>()
    }

    @Test
    fun notificationInvitedToChannel() {
        val messageBody = "Invited to channel"
        val bundle = Bundle().apply {
            putString("channel_id", "channel_id")
            putString("twi_body", messageBody)
            putString("twi_message_type", "twilio.channel.invited_to_channel")
        }
        val remoteMessage = RemoteMessage(bundle)
        clickOnNotification(remoteMessage)
        verifyActivityVisible<ChannelActivity>()
    }

    @Test
    fun notificationRemovedFromChannel() {
        val messageBody = "Removed from channel"
        val bundle = Bundle().apply {
            putString("channel_id", "channel_id")
            putString("twi_body", messageBody)
            putString("twi_message_type", "twilio.channel.removed_from_channel")
        }
        val remoteMessage = RemoteMessage(bundle)
        clickOnNotification(remoteMessage)
        verifyActivityVisible<ChannelListActivity>()
    }

    private fun clickOnNotification(remoteMessage: RemoteMessage) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val payload = NotificationPayload(remoteMessage.data)
        fcmManager.showNotification(payload)
        device.openNotification()
        val notification = device.wait(Until.findObject(By.textContains(payload.body)), NOTIFICATION_WAIT_TIME)
        notification.click()
    }
}
