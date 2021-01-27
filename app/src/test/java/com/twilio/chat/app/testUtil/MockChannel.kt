package com.twilio.chat.app.testUtil

import com.twilio.chat.Attributes
import com.twilio.chat.Channel
import com.twilio.chat.Channel.ChannelStatus
import com.twilio.chat.Channel.ChannelType
import com.twilio.chat.Channel.SynchronizationStatus.ALL
import com.twilio.chat.ChannelListener
import com.twilio.chat.app.common.extensions.asDateString
import com.twilio.chat.app.data.localCache.entity.ChannelDataItem
import org.mockito.ArgumentCaptor
import org.powermock.api.mockito.PowerMockito
import java.util.*

fun ChannelDataItem.toChannelMock(
    synchronizationStatus: Channel.SynchronizationStatus = ALL,
    attributes: String = "",
    channelListenerCaptor : ArgumentCaptor<ChannelListener>? = null
): Channel {
    val channel = PowerMockito.mock(Channel::class.java)

    whenCall(channel.sid).thenReturn(sid)
    whenCall(channel.friendlyName).thenReturn(friendlyName)
    whenCall(channel.uniqueName).thenReturn(uniqueName)
    whenCall(channel.dateUpdated).thenReturn(dateUpdated.asDateString())
    whenCall(channel.dateUpdatedAsDate).thenReturn(Date(dateUpdated))
    whenCall(channel.dateCreated).thenReturn(dateCreated.asDateString())
    whenCall(channel.dateCreatedAsDate).thenReturn(Date(dateCreated))
    whenCall(channel.createdBy).thenReturn(createdBy)
    whenCall(channel.type).thenReturn(ChannelType.fromInt(type))
    whenCall(channel.synchronizationStatus).thenReturn(synchronizationStatus)
    whenCall(channel.status).thenReturn(ChannelStatus.fromInt(participatingStatus))
    whenCall(channel.attributes).thenReturn(Attributes(attributes))
    whenCall(channel.notificationLevel).thenReturn(Channel.NotificationLevel.fromInt(notificationLevel))

    if (channelListenerCaptor != null) {
        whenCall(channel.addListener(channelListenerCaptor.capture())).then {  }
    }

    return channel
}
