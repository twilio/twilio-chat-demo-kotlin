package com.twilio.chat.app.common

import android.app.Application
import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.RestrictTo.Scope
import com.twilio.chat.app.data.ChatClientWrapper
import com.twilio.chat.app.data.CredentialStorage
import com.twilio.chat.app.manager.*
import com.twilio.chat.app.repository.ChatRepositoryImpl
import com.twilio.chat.app.viewModel.*
import timber.log.Timber

var injector = Injector()
    private set

@RestrictTo(Scope.TESTS)
fun setupTestInjector(testInjector: Injector) {
    injector = testInjector
}

open class Injector {

    private var fcmManagerImpl: FCMManagerImpl? = null

    open fun createLoginViewModel(application: Application): LoginViewModel {
        val credentialStorage = CredentialStorage(application.applicationContext)
        val loginManager = LoginManagerImpl(ChatClientWrapper.INSTANCE, ChatRepositoryImpl.INSTANCE, credentialStorage)

        return LoginViewModel(loginManager, application)
    }

    open fun createSplashViewModel(application: Application): SplashViewModel {
        val credentialStorage = CredentialStorage(application.applicationContext)
        val loginManager = LoginManagerImpl(ChatClientWrapper.INSTANCE, ChatRepositoryImpl.INSTANCE, credentialStorage)

        val viewModel = SplashViewModel(loginManager, application)
        viewModel.initialize()

        return viewModel
    }

    open fun createChannelListViewModel(application: Application): ChannelListViewModel {
        val channelListManager = ChannelListManagerImpl(ChatClientWrapper.INSTANCE)
        val credentialStorage = CredentialStorage(application.applicationContext)
        val userManager = UserManagerImpl(ChatClientWrapper.INSTANCE)
        val loginManager = LoginManagerImpl(ChatClientWrapper.INSTANCE, ChatRepositoryImpl.INSTANCE, credentialStorage)
        return ChannelListViewModel(ChatRepositoryImpl.INSTANCE, channelListManager, userManager, loginManager)
    }

    open fun createChannelViewModel(appContext: Context, channelSid: String): ChannelViewModel {
        val channelManager = ChannelManagerImpl(channelSid, ChatClientWrapper.INSTANCE, ChatRepositoryImpl.INSTANCE)
        return ChannelViewModel(appContext, channelSid, ChatRepositoryImpl.INSTANCE, channelManager)
    }

    open fun createChannelDetailsViewModel(channelSid: String): ChannelDetailsViewModel {
        val channelListManager = ChannelListManagerImpl(ChatClientWrapper.INSTANCE)
        val memberListManager = MemberListManagerImpl(channelSid, ChatClientWrapper.INSTANCE)
        return ChannelDetailsViewModel(channelSid, ChatRepositoryImpl.INSTANCE, channelListManager, memberListManager)
    }

    open fun createMemberListViewModel(channelSid: String): MemberListViewModel {
        val memberListManager = MemberListManagerImpl(channelSid, ChatClientWrapper.INSTANCE)
        return MemberListViewModel(channelSid, ChatRepositoryImpl.INSTANCE, memberListManager)
    }

    open fun createFCMManager(context: Context): FCMManager {
        val credentialStorage = CredentialStorage(context.applicationContext)
        if (fcmManagerImpl == null) {
            fcmManagerImpl = FCMManagerImpl(context, ChatClientWrapper.INSTANCE, credentialStorage)
        }
        return fcmManagerImpl!!
    }
}
