package com.tonapps.wallet.data.dapps.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppNotificationsEntity(
    val accountId: String,
    val notifications: List<AppPushEntity> = emptyList(),
): Parcelable {

    val apps: List<AppEntity>
        get() = notifications.map { it.from }.distinctBy { it.host }

    val isEmpty: Boolean
        get() = notifications.isEmpty()
}