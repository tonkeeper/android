package com.tonapps.wallet.data.multichain.exchange

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import io.exchangeapi.models.CrossSwapCalldataPayloadType

sealed interface SwapPayloadInfo {

    val to: String
    val amount: BigInteger
    val data: String?
    val mode: Int?
    val fee: Fee?

    // `exact` bakes the sell amount into provider calldata, so the amount must reach
    // the chain untouched — any later trimming desyncs it from the calldata.
    val calldataType: CrossSwapCalldataPayloadType

    val isExactCalldata: Boolean
        get() = calldataType == CrossSwapCalldataPayloadType.exact

    data class Data(
        override val to: String,
        override val amount: BigInteger,
        override val data: String?,
        override val calldataType: CrossSwapCalldataPayloadType,
        override val mode: Int? = null,
        override val fee: Fee? = null,
    ) : SwapPayloadInfo

    data class Approve(
        override val to: String,
        override val amount: BigInteger,
        override val data: String,
        override val fee: Fee,
        override val calldataType: CrossSwapCalldataPayloadType,
        override val mode: Int? = null,
    ) : SwapPayloadInfo
}


