@file:Suppress("IncorrectScope")

package com.twilio.chat.app.testUtil

import com.twilio.chat.Attributes
import com.twilio.chat.Member
import com.twilio.chat.Message
import com.twilio.chat.app.common.extensions.asDateString
import com.twilio.chat.app.data.localCache.entity.MessageDataItem
import org.powermock.api.mockito.PowerMockito
import java.util.*

fun MessageDataItem.toMessageMock(member: Member): Message {
    val media = PowerMockito.mock(Message.Media::class.java)
    whenCall(media.sid).thenReturn(mediaSid)
    whenCall(media.fileName).thenReturn(mediaFileName)
    whenCall(media.type).thenReturn(mediaType)

    val message = PowerMockito.mock(Message::class.java)

    whenCall(message.sid).thenReturn(sid)
    whenCall(message.author).thenReturn(author)
    whenCall(message.channelSid).thenReturn(channelSid)
    whenCall(message.dateCreated).thenReturn(dateCreated.asDateString())
    whenCall(message.dateCreatedAsDate).thenReturn(Date(dateCreated))
    whenCall(message.memberSid).thenReturn(memberSid)
    whenCall(message.type).thenReturn(Message.Type.fromInt(type))
    whenCall(message.attributes).thenReturn(Attributes(attributes))
    whenCall(message.messageBody).thenReturn(body)
    whenCall(message.messageIndex).thenReturn(index)
    whenCall(message.member).thenReturn(member)
    whenCall(message.media).thenReturn(media)

    return message
}
