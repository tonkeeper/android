package com.tonapps.tonkeeper.fragment.send

import com.tonapps.tonkeeper.Global
import com.tonapps.tonkeeper.core.Coin
import io.tonapi.models.JettonBalance
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.MsgAddressInt
import org.ton.cell.Cell
import org.ton.contract.wallet.WalletTransfer
import org.ton.contract.wallet.WalletTransferBuilder
import ton.SendMode
import ton.transfer.Transfer

data class TransactionData(
    val address: String? = null,
    val name: String? = null,
    val comment: String? = null,
    val amountRaw: String = "0",
    val max: Boolean = false,
    val jetton: JettonBalance? = null,
    val bounce: Boolean = false,
) {

    val icon: String
        get() = jetton?.jetton?.image ?: Global.tonCoinUrl

    val tokenName: String
        get() = jetton?.jetton?.name ?: "TON"

    val tokenAddress: String
        get() = jetton?.jetton?.address ?: "TON"

    val tokenSymbol: String
        get() = jetton?.jetton?.symbol ?: "TON"

    val isTon: Boolean
        get() = tokenSymbol == "TON"

    val destination: AddrStd
        get() {
            if (isTon) {
                return AddrStd.parse(address!!)
            }
            return AddrStd.parse(jetton!!.walletAddress.address)
        }

    val sendMode: Int
        get() {
            if (max && isTon) {
                return SendMode.CARRY_ALL_REMAINING_BALANCE.value
            }
            return SendMode.PAY_GAS_SEPARATELY.value + SendMode.IGNORE_ERRORS.value
        }

    val coins: Coins
        get() {
            if (isTon) {
                return amount
            }
            return Coins.ofNano(Coin.toNano(0.64f))
        }

    private val decimals: Int
        get() {
            if (isTon) {
                return 9
            }
            return jetton?.jetton?.decimals ?: 9
        }

    val amount: Coins
        get() {
            val value = Coin.bigDecimal(amountRaw, decimals)
            return Coins.ofNano(value.toLong())
        }

    fun buildWalletTransfer(
        responseAddress: MsgAddressInt,
    ): WalletTransfer {
        val builder = WalletTransferBuilder()
        builder.bounceable = bounce
        builder.destination = destination
        builder.body = buildWalletTransferBody(responseAddress)
        builder.sendMode = sendMode
        builder.coins = coins
        return builder.build()
    }

    private fun buildWalletTransferBody(
        responseAddress: MsgAddressInt,
    ): Cell? {
        if (isTon) {
            return Transfer.text(comment)
        }
        return Transfer.jetton(
            coins = amount,
            toAddress = MsgAddressInt.parse(address!!),
            responseAddress = responseAddress,
            body = comment,
        )
    }
}