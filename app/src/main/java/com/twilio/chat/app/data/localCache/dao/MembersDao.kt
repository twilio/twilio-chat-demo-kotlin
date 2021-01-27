package com.twilio.chat.app.data.localCache.dao

import androidx.room.*
import com.twilio.chat.app.data.localCache.entity.MemberDataItem
import kotlinx.coroutines.flow.Flow

@Dao
interface MembersDao {

    // Get all Members for channel
    @Query("SELECT * FROM member_table WHERE channelSid = :channelSid ORDER BY friendlyName")
    fun getAllMembers(channelSid: String): Flow<List<MemberDataItem>>

    // Get all Members for channel who are typing
    @Query("SELECT * FROM member_table WHERE channelSid = :channelSid AND typing")
    fun getTypingMembers(channelSid: String): Flow<List<MemberDataItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(member: MemberDataItem)

    @Delete
    fun delete(member: MemberDataItem)
}
