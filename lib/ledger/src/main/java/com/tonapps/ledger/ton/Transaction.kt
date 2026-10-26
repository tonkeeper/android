package com.tonapps.ledger.ton

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import org.ton.block.Coins
import org.ton.block.MsgAddress
import org.ton.block.StateInit
import org.ton.contract.wallet.WalletTransfer

@Parcelize
@TypeParceler<MsgAddress, MsgAddressParceler>
@TypeParceler<Coins, CoinsParceler>
@TypeParceler<StateInit?, NullableStateInitParceler>
data class Transaction(
    val destination: MsgAddress,
    val sendMode: Int,
    val seqno: Int,
    val timeout: Int,
    val bounceable: Boolean,
    val coins: Coins,
    val stateInit: StateInit? = null,
    val payload: TonPayloadFormat? = null
): Parcelable {
    companion object {
        fun fromWalletTransfer(
            walletTransfer: WalletTransfer,
            seqno: Int,
            timeout: Number
        ): Transaction {
            val payload: TonPayloadFormat? = walletTransfer.messageData.body.let {
                TonPayloadFormat.fromCell(it)
            }
            return Transaction(
                destination = walletTransfer.destination,
                sendMode = walletTransfer.sendMode,
                seqno = seqno,
                timeout = timeout.toInt(),
                bounceable = walletTransfer.bounceable,
                coins = walletTransfer.coins.coins,
                stateInit = walletTransfer.messageData.stateInit?.value,
                payload = payload
            )
        }
    }
}
