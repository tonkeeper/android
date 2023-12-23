package com.tonkeeper.fragment.send

import com.tonkeeper.Global
import com.tonkeeper.R
import io.tonapi.models.JettonBalance
import ton.SupportedTokens

data class TransactionData(
    val address: String? = null,
    val name: String? = null,
    val comment: String? = null,
    val amount: Float = 0f,
    val max: Boolean = false,
    val jetton: JettonBalance? = null
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
}