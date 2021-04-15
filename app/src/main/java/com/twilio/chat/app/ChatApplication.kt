package com.twilio.chat.app

import android.app.Application
import android.content.Intent
import androidx.emoji.bundled.BundledEmojiCompatConfig
import androidx.emoji.text.EmojiCompat
import com.google.firebase.FirebaseApp
import com.twilio.chat.ChatClient
import com.twilio.chat.ChatClient.LogLevel
import com.twilio.chat.app.common.LineNumberDebugTree
import com.twilio.chat.app.data.ChatClientWrapper
import com.twilio.chat.app.data.localCache.LocalCacheProvider
import com.twilio.chat.app.repository.ChatRepositoryImpl
import com.twilio.chat.app.ui.SplashActivity
import timber.log.Timber

class ChatApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            ChatClient.setLogLevel(LogLevel.DEBUG)
            Timber.plant(LineNumberDebugTree("Demo"))
        }

        FirebaseApp.initializeApp(this)
        EmojiCompat.init(BundledEmojiCompatConfig(this))
        ChatClientWrapper.createInstance()
        LocalCacheProvider.createInstance(this)
        ChatRepositoryImpl.createInstance(ChatClientWrapper.INSTANCE, LocalCacheProvider.INSTANCE)

        val intent = Intent(this, SplashActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        Timber.d("startActivity SplashActivity")
        startActivity(intent)
    }
}
