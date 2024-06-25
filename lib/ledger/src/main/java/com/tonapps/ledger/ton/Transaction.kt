package com.tonapps.ledger.ton

import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.StateInit

data class Transaction(
    val to: AddrStd,
    val sendMode: Int,
    val seqno: Int,
    val timeout: Int,
    val bounce: Boolean,
    val amount: Coins,
    val stateInit: StateInit? = null,
    val payload: TonPayloadFormat? = null
)
