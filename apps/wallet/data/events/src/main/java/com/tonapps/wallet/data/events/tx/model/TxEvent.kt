package com.tonapps.wallet.data.events.tx.model

import android.os.Parcelable
import com.tonapps.icu.Coins
import com.tonapps.wallet.api.entity.value.Blockchain
import com.tonapps.wallet.api.entity.value.Timestamp
import com.tonapps.wallet.data.events.ActionType
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class TxEvent(
    val hash: String,
    val lt: Long,
    val timestamp: Timestamp,
    val actions: List<TxAction>,
    val isScam: Boolean,
    val inProgress: Boolean,
    val progress: Float,
    val blockchain: Blockchain,
    val extra: Extra,
): Parcelable {

    @Parcelize
    sealed class Extra: Parcelable {
        data class Refund(val value: Coins) : Extra()
        data class Fee(val value: Coins) : Extra()
        data class Battery(val charges: Int = 0) : Extra()
    }

    val isTron: Boolean
        get() = blockchain == Blockchain.TRON

    val hasEncryptedText: Boolean
        get() = actions.any { it.hasEncryptedText }

    @IgnoredOnParcel
    val id: String by lazy {
        if (blockchain == Blockchain.TON) {
            hash
        } else {
            "$blockchain:$hash"
        }
    }

    val isOut: Boolean
        get() = actions.size == 1 && actions.first().isOut

    val spam: Boolean
        get() = if (isOut) false else isScam

    fun containsActionType(vararg types: ActionType): Boolean {
        return actions.any { action ->
            action.type in types
        }
    }
}