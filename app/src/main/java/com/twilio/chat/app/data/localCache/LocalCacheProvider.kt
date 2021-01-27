package com.twilio.chat.app.data.localCache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.twilio.chat.app.data.localCache.dao.ChannelsDao
import com.twilio.chat.app.data.localCache.dao.MembersDao
import com.twilio.chat.app.data.localCache.dao.MessagesDao
import com.twilio.chat.app.data.localCache.entity.ChannelDataItem
import com.twilio.chat.app.data.localCache.entity.MemberDataItem
import com.twilio.chat.app.data.localCache.entity.MessageDataItem

@Database(entities = [ChannelDataItem::class, MessageDataItem::class, MemberDataItem::class], version = 1, exportSchema = false)
abstract class LocalCacheProvider : RoomDatabase() {

    abstract fun channelsDao(): ChannelsDao

    abstract fun messagesDao(): MessagesDao

    abstract fun membersDao(): MembersDao

    companion object {
        val INSTANCE get() = _instance ?: error("call LocalCacheProvider.createInstance() first")

        private var _instance: LocalCacheProvider? = null

        fun createInstance(context: Context) {
            check(_instance == null) { "LocalCacheProvider singleton instance has been already created" }
            _instance = Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                LocalCacheProvider::class.java
            ).build()
        }
    }
}
