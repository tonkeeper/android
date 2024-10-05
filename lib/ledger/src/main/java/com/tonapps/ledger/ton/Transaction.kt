package com.tonapps.ledger.ton

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.MsgAddressInt
import org.ton.block.StateInit
import org.ton.contract.wallet.WalletTransfer

@Parcelize
data class Transaction(
    val destination: @RawValue MsgAddressInt,
    val sendMode: Int,
    val seqno: Int,
    val timeout: Int,
    val bounceable: Boolean,
    val coins: @RawValue Coins,
    val stateInit: @RawValue StateInit? = null,
    val payload: TonPayloadFormat? = null
): Parcelable {
    companion object {
        fun fromWalletTransfer(
            walletTransfer: WalletTransfer,
            seqno: Int,
            timeout: Number
        ): Transaction {
            val payload: TonPayloadFormat? = walletTransfer.body?.let {
                TonPayloadFormat.fromCell(it)
            }
            return Transaction(
                destination = walletTransfer.destination,
                sendMode = walletTransfer.sendMode,
                seqno = seqno,
                timeout = timeout.toInt(),
                bounceable = walletTransfer.bounceable,
                coins = walletTransfer.coins.coins,
                stateInit = walletTransfer.stateInit,
                payload = payload
            )
        }
    }
}
