package com.twilio.chat.app.common.extensions

import java.text.SimpleDateFormat
import java.util.*

fun Long.asTimeString(): String = SimpleDateFormat("h:m a", Locale.getDefault()).format(Date(this))

fun Long.asDateString(): String = SimpleDateFormat("dd-mm-yyyy", Locale.getDefault()).format(Date(this))

fun Long.asMessageCount(): String = if (this > 99) "99+" else this.toString()
