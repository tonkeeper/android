package com.tonapps.ledger.ton

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.cell.Cell
import java.math.BigInteger

sealed class TonPayloadFormat: Parcelable {

    @Parcelize
    data class Comment(val text: String) : TonPayloadFormat()

    @Parcelize
    data class JettonTransfer(
        val queryId: BigInteger?,
        val coins: @RawValue Coins,
        val receiverAddress: @RawValue AddrStd,
        val excessesAddress: @RawValue AddrStd,
        val customPayload: @RawValue Cell?,
        val forwardAmount: @RawValue Coins,
        val forwardPayload: @RawValue Cell?
    ) : TonPayloadFormat()

    @Parcelize
    data class NftTransfer(
        val queryId: BigInteger?,
        val newOwnerAddress: @RawValue AddrStd,
        val excessesAddress: @RawValue AddrStd,
        val customPayload: @RawValue Cell?,
        val forwardAmount: @RawValue Coins,
        val forwardPayload: @RawValue Cell?
    ) : TonPayloadFormat()
}
