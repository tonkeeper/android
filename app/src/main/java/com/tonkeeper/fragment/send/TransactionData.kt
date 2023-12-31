package com.tonkeeper.fragment.send

import com.tonkeeper.Global
import com.tonkeeper.core.Coin
import io.tonapi.models.JettonBalance
import org.ton.block.Coins
import ton.SupportedTokens

data class TransactionData(
    val address: String? = null,
    val name: String? = null,
    val comment: String? = null,
    val amountRaw: String = "0",
    val max: Boolean = false,
    val jetton: JettonBalance? = null,
) {

    val icon: String
        get() = jetton?.jetton?.image ?: Global.tonCoinUrl

    val tokenName: String
        get() = jetton?.jetton?.name ?: SupportedTokens.TON.code

    val tokenAddress: String
        get() = jetton?.jetton?.address ?: SupportedTokens.TON.code

    val tokenSymbol: String
        get() = jetton?.jetton?.symbol ?: SupportedTokens.TON.code

    val isTon: Boolean
        get() = tokenSymbol == SupportedTokens.TON.code

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

}