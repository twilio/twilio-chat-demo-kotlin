package com.twilio.chat.app.common.extensions

import com.twilio.chat.Paginator

suspend fun <T> Paginator<T>.requestAllItems(): List<T> {
    val result = ArrayList<T>(items)
    var paginator = this

    while (paginator.hasNextPage()) {
        paginator = paginator.requestNextPage()
        result += paginator.items
    }

    return result
}
