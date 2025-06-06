package com.tonapps.tonkeeper.ui.screen.onramp.main.state

import com.tonapps.tonkeeper.core.entities.AssetsEntity
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.core.currency.WalletCurrency

sealed class CurrencyInputState(
    val decimals: Int,
    val address: String,
    val symbol: String,
    val fiat: Boolean,
    val chain: String,
    val network: String,
) {
    data class Fiat(
        val currency: WalletCurrency
    ) : CurrencyInputState(currency.decimals, currency.code, currency.code, currency.fiat, currency.chain.name, "fiat")

    data class TONAsset(
        val token: TokenEntity,
    ) : CurrencyInputState(token.decimals, token.address, token.symbol, false, "TON", if (token.isTon) "native" else "jetton")

    data class Crypto(
        val currency: WalletCurrency
    ) : CurrencyInputState(currency.decimals, currency.code, currency.code, currency.fiat, currency.chain.name, "crypto")

    val isUSD: Boolean
        get() = fiat && symbol.equals("USD", ignoreCase = true)

    val isUSDT: Boolean
        get() = !fiat && OnRampCurrencyInputs.fixSymbol(symbol).equals("USDT", ignoreCase = true)

    val type: String
        get() {
            if (fiat) {
                return "fiat"
            } else if (symbol.equals("TON", ignoreCase = true)) {
                return "native"
            }
            return when (chain.lowercase()) {
                "etc", "erc-20" -> "erc-20"
                "ton", "jetton" -> "jetton"
                "tron", "trc-20" -> "trc-20"
                "sol", "spl" -> "spl"
                "bnb", "bep-20" -> "bep-20"
                "avalanche" -> "avalanche"
                "arbitrum" -> "arbitrum"
                else -> "native"
            }
        }

    val isTON: Boolean
        get() = !fiat && (symbol.equals("TON", ignoreCase = true) || type.equals("jetton", ignoreCase = true))

    val isTron: Boolean
        get() = !fiat && (symbol.equals("TRON", ignoreCase = true) || type.equals("trc-20", ignoreCase = true))

    companion object {

        fun of(assets: AssetsEntity): CurrencyInputState {
            return when (assets) {
                is AssetsEntity.Token -> TONAsset(assets.token.token)
                is AssetsEntity.Currency -> Fiat(assets.currency)
                else -> throw IllegalArgumentException("Unknown assets type: ${assets::class.java}")
            }
        }

        fun findWalletCurrency(vararg states: CurrencyInputState): WalletCurrency? {
            for (state in states) {
                if (state is Fiat) {
                    return state.currency
                }
            }
            return null
        }

        fun findToken(vararg states: CurrencyInputState): TokenEntity? {
            for (state in states) {
                if (state is TONAsset) {
                    return state.token
                }
            }
            return null
        }
    }
}