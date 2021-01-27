package com.twilio.chat.app.testUtil

import com.twilio.chat.User
import org.powermock.api.mockito.PowerMockito

fun createUserMock(friendlyName: String = "", identity: String = ""): User {
    val user = PowerMockito.mock(User::class.java)
    whenCall(user.identity).thenReturn(identity)
    whenCall(user.friendlyName).thenReturn(friendlyName)
    return user
}
