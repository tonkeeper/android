package com.tonapps.wallet.api.entity

import io.tonendpointapi.models.Notification

data class NotificationEntity(
    val id: String,
    val title: String,
    val caption: String,
    val mode: String,
    val action: ActionEntity?
) {

    data class ActionEntity(
        val type: String,
        val label: String,
        val url: String,
    )

    constructor(notification: Notification) : this(
        id = notification.id,
        title = notification.title,
        caption = notification.caption,
        mode = notification.mode,
        action = notification.action.let { ActionEntity(it.type, it.label, it.url) }
    )
}
