package com.tonapps.wallet.data.backup.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BackupEntity(
    val id: Long,
    val source: Source = Source.LOCAL,
    val walletId: Long,
    val date: Long,
): Parcelable {

    enum class Source {
        LOCAL
    }
}