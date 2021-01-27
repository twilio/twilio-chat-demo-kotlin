package com.twilio.chat.app.testUtil

import com.twilio.chat.Attributes
import com.twilio.chat.Channel
import com.twilio.chat.Member
import com.twilio.chat.app.data.localCache.entity.MemberDataItem
import org.powermock.api.mockito.PowerMockito

fun MemberDataItem.toMemberMock(channel: Channel): Member {
    val member = PowerMockito.mock(Member::class.java)
    whenCall(member.sid).thenReturn(sid)
    whenCall(member.identity).thenReturn(identity)
    whenCall(member.channel).thenReturn(channel)
    whenCall(member.attributes).thenReturn(Attributes("\"\""))
    whenCall(member.lastConsumedMessageIndex).thenReturn(lastConsumedMessageIndex)
    whenCall(member.lastConsumptionTimestamp).thenReturn(lastConsumptionTimestamp)
    whenCall(member.type).thenReturn(Member.Type.CHAT)
    return member
}
