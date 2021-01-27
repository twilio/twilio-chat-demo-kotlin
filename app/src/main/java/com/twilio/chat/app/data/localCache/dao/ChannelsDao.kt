package com.twilio.chat.app.data.localCache.dao

import androidx.room.*
import com.twilio.chat.app.data.localCache.entity.ChannelDataItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelsDao {

    // Get all Channels
    @Query("SELECT * FROM channel_table")
    fun getAll(): List<ChannelDataItem>

    // Get Public Channels
    @Query("SELECT * FROM channel_table WHERE type = 0")
    fun getPublicChannels(): Flow<List<ChannelDataItem>>

    // Get User Channels
    @Query("SELECT * FROM channel_table WHERE participatingStatus = 1")
    fun getUserChannels(): Flow<List<ChannelDataItem>>

    // Get Channel by sid
    @Query("SELECT * FROM channel_table WHERE sid = :sid")
    fun getChannel(sid: String): Flow<ChannelDataItem?>

    // Insert Channel list
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(channelDataItemList: List<ChannelDataItem>)

    // Insert single Channel
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(channelDataItem: ChannelDataItem)

    // Update Channel
    @Query("UPDATE channel_table SET type = :type, participatingStatus = :status, notificationLevel = :level, friendlyName = :friendlyName WHERE sid = :sid")
    fun update(sid: String, type: Int, status: Int, level: Int, friendlyName: String)

    @Query("UPDATE channel_table SET membersCount = :membersCount WHERE sid = :sid")
    fun updateMemberCount(sid: String, membersCount: Long)

    @Query("UPDATE channel_table SET messagesCount = :messagesCount WHERE sid = :sid")
    fun updateMessagesCount(sid: String, messagesCount: Long)

    @Query("UPDATE channel_table SET unconsumedMessagesCount = :unconsumedMessagesCount WHERE sid = :sid")
    fun updateUnconsumedMessagesCount(sid: String, unconsumedMessagesCount: Long)

    // Delete Channel
    @Query("DELETE FROM channel_table WHERE sid = :sid")
    fun delete(sid: String)

    // Delete Gone User Channels
    @Query("DELETE FROM channel_table WHERE participatingStatus = 1 AND sid NOT IN (:sids)")
    fun deleteUserChannelsNotIn(sids: List<String>)

    fun deleteGoneUserChannels(newChannels: List<ChannelDataItem>) = deleteUserChannelsNotIn(newChannels.map { it.sid })

    // Delete Gone Public Channels
    @Query("DELETE FROM channel_table WHERE type = 0 AND sid NOT IN (:sids)")
    fun deletePublicChannelsNotIn(sids: List<String>)

    fun deleteGonePublicChannels(newChannels: List<ChannelDataItem>) = deletePublicChannelsNotIn(newChannels.map { it.sid })

}
