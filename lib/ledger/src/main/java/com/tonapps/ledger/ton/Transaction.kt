package com.tonapps.ledger.ton

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.StateInit

@Parcelize
data class Transaction(
    val destination: @RawValue AddrStd,
    val sendMode: Int,
    val seqno: Int,
    val timeout: Int,
    val bounceable: Boolean,
    val coins: @RawValue Coins,
    val stateInit: @RawValue StateInit? = null,
    val payload: TonPayloadFormat? = null
): Parcelable
