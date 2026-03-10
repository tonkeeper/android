package com.tonapps.wallet.data.events.tx

import android.os.Parcelable
import com.tonapps.wallet.data.events.tx.model.TxEvent
import kotlinx.parcelize.Parcelize

@Parcelize
data class TxEvents(
    val events: List<TxEvent>
): Parcelable