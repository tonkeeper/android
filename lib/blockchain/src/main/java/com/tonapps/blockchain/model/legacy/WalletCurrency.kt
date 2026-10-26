package com.tonapps.blockchain.model.legacy

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.core.net.toUri
import com.tonapps.blockchain.contract.TokenType
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.extensions.toUriOrNull
import com.tonapps.lib.blockchain.R
import com.tonapps.uikit.flag.getFlagDrawable
import com.tonapps.uikit.icon.UIKitIcon
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@SuppressLint("UnsafeOptInUsageError")
@Serializable
@Parcelize
@Suppress("ClassOrdering")
data class WalletCurrency(
    val code: String,
    val title: String,
    val alias: String = "",
    val chain: Chain,
    val iconUrl: String? = null,
    val isFiat: Boolean = chain is Chain.FIAT,
    val isStablecoin: Boolean = false, // TODO remove later
    val isToken: Boolean = false, // TODO replace to asset type
) : Parcelable {

    @IgnoredOnParcel
    @Transient
    val tokenType: TokenType? by lazy {
        if (!isToken) {
            return@lazy null
        }

        when (chain) {
            is Chain.TON -> TokenType.Defined.JETTON
            is Chain.TRON -> TokenType.Defined.TRC20
            is Chain.ETHEREUM -> TokenType.Defined.ERC20
            is Chain.ETC -> TokenType.Defined.ERC20
            is Chain.BNB -> TokenType.Defined.BEP20
            is Chain.Solana -> TokenType.Defined.SPL
            is Chain.Unknown -> TokenType.Arbitrary(chain.type.uppercase())
            is Chain.BTC -> null
            is Chain.FIAT -> null
        }
    }

    @IgnoredOnParcel
    @Transient
    val network: String? by lazy { // TODO shitty name
        val token = tokenType
        when {
            token != null -> token.value
            chain is Chain.Unknown -> chain.type
            chain is Chain.FIAT -> null
            else -> "native"
        }
    }

    @Serializable
    sealed class Chain(
        open val decimals: Int = 0,
        val symbol: String = "",
        val name: String = symbol,
    ) : Parcelable {

        @Serializable
        @Parcelize
        data class FIAT(
            val countryCode: String
        ) : Chain(6, "FIAT")

        @Serializable
        @Parcelize
        data class TON(
            val address: String = TON_CHAIN_KEY,
            @Transient override val decimals: Int = 9,
        ) : Chain(decimals, "TON")

        @Serializable
        @Parcelize
        data class TRON(
            val address: String = "TRON",
            @Transient override val decimals: Int = 6,
        ) : Chain(6, "TRX", "Tron")

        @Serializable
        @Parcelize
        data object BTC : Chain(8, "BTC", "Bitcoin")

        @Serializable
        @Parcelize
        data class ETHEREUM(
            val address: String = "ETH",
            @Transient override val decimals: Int = 18,
        ) : Chain(decimals, "ETH", "Ethereum")

        @Serializable
        @Parcelize
        data class ETC(
            val address: String = "ETC",
            @Transient override val decimals: Int = 12,
        ) : Chain(decimals, "ETC", "Ethereum Classic")

        @Serializable
        @Parcelize
        data class BNB(
            val address: String = "BNB",
            @Transient override val decimals: Int = 18,
        ) : Chain(decimals, "BNB", "Binance Coin")

        @Serializable
        @Parcelize
        data class Solana(
            val address: String = "SOL",
            @Transient override val decimals: Int = 9,
        ) : Chain(decimals, "SOL", "Solana")

        @Serializable
        @Parcelize
        data class Unknown(
            val type: String,
            val address: String,
            @Transient override val decimals: Int = 6,
        ) : Chain(decimals, type.uppercase())
    }

    companion object {
        private val fiatCodeRegex = Regex("[A-Z]{3}")

        private val popularCurrencies = listOf(
            "USD",
            "EUR",
            "GBP",
            "JPY",
            "CHF",
            "CNY",
            "INR",
            "UAH",
            "RUB",
            "AUD",
            "CAD",
            "HKD",
            "SGD",
            "BTC",
            "USDT",
            "ETH",
        )

        const val USDT_KEY = "USDT"
        const val USDE_KEY = "USDE"
        const val TS_USDE_KEY = "TS_USDE"
        const val TON_CHAIN_KEY = "TON"
        const val GRAM_KEY = "GRAM"
        const val BTC_KEY = "BTC"
        const val ETH_KEY = "ETH"

        private val USDT_TRON_ADDRESS = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
        val USDT_TON_ADDRESS = "0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe"
        private val USDT_ETH_ADDRESS = "0xdac17f958d2ee523a2206206994597c13d831ec7"

        val USDE_TON_ETHENA_ADDRESS =
            "0:086fa2a675f74347b08dd4606a549b8fdb98829cb282bc1949d3b12fbaed9dcc"
        val TS_USDE_TON_ETHENA_ADDRESS =
            "0:d0e545323c7acb7102653c073377f7e3c67f122eb94d430a250739f109d4a57d"

        val USD = WalletCurrency(
            code = "USD",
            title = CurrencyCountries.getCurrencyTitle("USD"),
            chain = Chain.FIAT("US")
        )

        val DEFAULT = USD

        val TON = WalletCurrency(
            code = GRAM_KEY,
            title = "Gram",
            chain = Chain.TON()
        )

        val BTC = WalletCurrency(
            code = BTC_KEY,
            title = "Bitcoin",
            chain = Chain.BTC
        )

        val ETH = WalletCurrency(
            code = ETH_KEY,
            title = "Ethereum",
            chain = Chain.ETC()
        )

        fun simple(code: String, decimals: Int, name: String, imageUrl: String): WalletCurrency {
            val chain = Chain.Unknown(name, code, decimals)
            return WalletCurrency(
                code = code,
                title = name,
                chain = chain,
                iconUrl = imageUrl
            )
        }

        fun unknownChain(
            type: String = "unknown",
            address: String = "unknown"
        ) = Chain.Unknown(type, address)

        fun unknown(
            code: String = "unknown",
            name: String = "unknown",
            imageUrl: String? = null,
            chain: Chain.Unknown = unknownChain()
        ): WalletCurrency {
            return WalletCurrency(
                code = code,
                title = name,
                chain = chain,
                iconUrl = imageUrl
            )
        }

        // TODO remove chain keep
        fun createChain(type: String, address: String): Chain {
            return when (TokenType.from(type)) {
                TokenType.Defined.JETTON -> Chain.TON(address)
                TokenType.Defined.ERC20 -> Chain.ETHEREUM(address)
                TokenType.Defined.BEP20 -> Chain.BNB(address)
                TokenType.Defined.SPL -> Chain.Solana(address)
                TokenType.Defined.TRC20 -> Chain.TRON(address)
                else -> Chain.Unknown(type, address)
            }
        }

        private fun createAlias(chainCode: String, tokenCode: String): String {
            if (chainCode.equals(tokenCode, ignoreCase = true) || tokenCode.isBlank()) {
                return ""
            }
            return "${chainCode}_$tokenCode"
        }

        val USDE_TON_ETHENA = WalletCurrency(
            code = USDE_KEY,
            alias = createAlias(TON_CHAIN_KEY, USDE_KEY),
            title = "Ethena USDe",
            chain = Chain.TON(USDE_TON_ETHENA_ADDRESS, 6),
            isToken = true,
        )

        val TS_USDE_TON_ETHENA = WalletCurrency(
            code = TS_USDE_KEY,
            alias = createAlias(TON_CHAIN_KEY, TS_USDE_KEY),
            title = "Staked USDe",
            chain = Chain.TON(TS_USDE_TON_ETHENA_ADDRESS, 6),
            isToken = true,
        )

        val USDT_TRON = WalletCurrency(
            code = USDT_KEY,
            alias = createAlias("TRON", USDT_KEY),
            title = "Tether",
            chain = Chain.TRON(USDT_TRON_ADDRESS, 6),
            isToken = true,
        )

        val USDT_TON = WalletCurrency(
            code = USDT_KEY,
            alias = createAlias(TON_CHAIN_KEY, USDT_KEY),
            title = "Tether",
            chain = Chain.TON(USDT_TON_ADDRESS, 6),
            isToken = true,
        )

        val USDT_SPL = WalletCurrency(
            code = USDT_KEY,
            alias = createAlias("SPL", USDT_KEY),
            title = "Tether",
            chain = Chain.Solana("Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB", 6),
            isToken = true,
        )

        val USDT_ETH = WalletCurrency(
            code = USDT_KEY,
            alias = createAlias(ETH_KEY, USDT_KEY),
            title = "Tether",
            chain = Chain.ETC(USDT_ETH_ADDRESS, 6),
            isToken = true,
        )

        val USDT_BEP20 = WalletCurrency(
            code = USDT_KEY,
            alias = createAlias("BEP20", USDT_KEY),
            title = "Tether",
            chain = Chain.BNB("0x55d398326f99059fF775485246999027B3197955", 6),
            isToken = true,
        )

        val USDT_AVALANCHE = WalletCurrency(
            code = USDT_KEY,
            alias = createAlias("AVALANCHE", USDT_KEY),
            title = "Tether",
            chain = Chain.Unknown("AVALANCHE", "0x9702230a8ea53601f5cd2dc00fdbc13d4df4a8c7", 6),
            isToken = true,
        )

        val USDT_ARBITRUM = WalletCurrency(
            code = USDT_KEY,
            alias = createAlias("ARBITRUM", USDT_KEY),
            title = "Tether",
            chain = Chain.Unknown("ARBITRUM", "0xfd086bc7cd5c481dcc9c85ebe478a1c0b69fcbb9", 6),
            isToken = true,
        )

        fun sort(value: List<WalletCurrency>): List<WalletCurrency> {
            return value.sortedWith(
                compareBy { currency ->
                    val index = popularCurrencies.indexOf(currency.code)
                    if (index >= 0) {
                        index
                    } else {
                        Int.MAX_VALUE
                    }
                }
            )
        }

        @DrawableRes
        fun getDrawableRes(currency: WalletCurrency): Int? {
            val chan = currency.chain
            if (chan is Chain.FIAT) {
                return getFlagDrawable(chan.countryCode)
            } else if (currency == USDE_TON_ETHENA) {
                return R.drawable.ic_udse_ethena_with_bg
            } else if (currency.code.equals(USDT_KEY, ignoreCase = true)) {
                return R.drawable.ic_usdt_with_bg
            } else if (currency == TON) {
                return UIKitIcon.ic_gram_with_bg
            } else if (currency == BTC) {
                return R.drawable.ic_btc_with_bg
            } else if (currency == ETH) {
                return R.drawable.ic_eth_with_bg
            }
            return null
        }

        fun ofOrDefault(code: String?): WalletCurrency {
            return of(code) ?: fiatOrNull(code) ?: DEFAULT
        }

        fun isValid(code: String): Boolean {
            return of(code) != null
        }

        fun fiat(code: String): WalletCurrency {
            return WalletCurrency(
                code = code,
                title = CurrencyCountries.getCurrencyTitle(code),
                chain = Chain.FIAT(CurrencyCountries.getCountryCode(code)),
                isFiat = true,
                isToken = false,
            )
        }

        fun of(code: String?): WalletCurrency? {
            if (code == "US") {
                return of("USD")
            }
            if (code.isNullOrBlank()) {
                return null
            }
            val upper = code.trim().uppercase()
            return when {
                upper == GRAM_KEY || upper == TON_CHAIN_KEY -> TON
                upper == BTC_KEY -> BTC
                upper == ETH_KEY -> ETH
                USDT_TRON.equalsByCode(code) -> USDT_TRON
                USDT_TON.equalsByCode(code) -> USDT_TON
                USDT_ETH.equalsByCode(code) -> USDT_ETH
                isFiatCode(upper) -> fiat(upper)
                else -> null
            }
        }

        private fun isFiatCode(code: String): Boolean {
            return CurrencyCountries.getCountryCode(code).isNotEmpty()
        }

        fun fiatOrNull(code: String?): WalletCurrency? {
            val upper = code?.trim()?.uppercase() ?: return null
            if (!fiatCodeRegex.matches(upper)) {
                return null
            }
            return fiat(upper)
        }
    }

    @IgnoredOnParcel
    val fiat: Boolean
        get() = chain is Chain.FIAT

    @IgnoredOnParcel
    val isUSDT: Boolean
        get() = code.uppercase()
            .replace("USD₮", USDT_KEY)
            .equals(USDT_KEY, ignoreCase = true)

    @IgnoredOnParcel
    val isTONChain: Boolean
        get() = chain is Chain.TON

    @IgnoredOnParcel
    val isTronChain: Boolean
        get() = chain is Chain.TRON

    @IgnoredOnParcel
    val isJetton: Boolean
        get() = if (isTONChain && code == GRAM_KEY) {
            false
        } else if (isTONChain) {
            true
        } else {
            false
        }

    @IgnoredOnParcel
    val decimals: Int
        get() = chain.decimals

    @IgnoredOnParcel
    val drawableRes: Int? by lazy {
        getDrawableRes(this)
    }

    @IgnoredOnParcel
    val isCustom: Boolean
        get() = !iconUrl.isNullOrBlank()

    @IgnoredOnParcel
    val iconUri: Uri?
        get() = iconUrl?.toUriOrNull() ?: drawableRes?.let {
            "res:/$it".toUri()
        }

    @IgnoredOnParcel
    val chainName: String? by lazy {
        if ((!isUSDT && isTONChain) || fiat) {
            null
        } else {
            chain.symbol.uppercase().replace("TRON", "TRC20")
        }
    }

    @IgnoredOnParcel
    val address: String by lazy {
        when (chain) {
            is Chain.TON -> chain.address
            is Chain.TRON -> chain.address
            is Chain.ETC -> chain.address
            is Chain.BNB -> chain.address
            else -> code
        }
    }

    @IgnoredOnParcel
    val symbol: String by lazy {
        /*if (chain is Chain.FIAT) {
            code
        } else if (chain is Chain.TON) {
            chain.address
        } else {
            chain.name // code
        }*/
        code
    }

    @IgnoredOnParcel
    val key: String by lazy {
        if (fiat) {
            "fiat:$code"
        } else if (isUSDT) {
            "stablecoin:$code"
        } else if (isTONChain && code == GRAM_KEY) {
            "crypto:TON"
        } else if (isTONChain) {
            "crypto:TON:$address"
        } else {
            "crypto:$code"
        }
    }

    @IgnoredOnParcel
    val tokenQuery: String by lazy {
        if (isTONChain) {
            address
        } else {
            code
        }
    }

    override fun equals(other: Any?): Boolean {
        val currency = other as? WalletCurrency ?: return false
        if (!code.equals(currency.code, true)) {
            return false
        }
        return chain.symbol.equals(currency.chain.symbol, true)
    }

    fun containsQuery(query: String): Boolean {
        return address.contains(query, ignoreCase = true) ||
                code.contains(query, ignoreCase = true) ||
                title.contains(query, ignoreCase = true) ||
                alias.contains(query, ignoreCase = true)
    }

    fun equalsByCode(other: String): Boolean {
        if (other.equals(code, ignoreCase = true) || (
                    alias.isNotBlank() && alias.equals(
                        other,
                        ignoreCase = true
                    )
                    )
        ) {
            return true
        }
        if (chain is Chain.TON && other.equals(chain.address, ignoreCase = true)) {
            return true
        }
        if (chain is Chain.TRON && other.equals(chain.address, ignoreCase = true)) {
            return true
        }
        if (chain is Chain.ETC && other.equals(chain.address, ignoreCase = true)) {
            return true
        }
        return false
    }

    override fun hashCode(): Int {
        var result = code.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + alias.hashCode()
        result = 31 * result + chain.hashCode()
        result = 31 * result + (iconUrl?.hashCode() ?: 0)
        result = 31 * result + fiat.hashCode()
        result = 31 * result + isUSDT.hashCode()
        result = 31 * result + isTONChain.hashCode()
        result = 31 * result + isTronChain.hashCode()
        result = 31 * result + decimals
        result = 31 * result + (drawableRes ?: 0)
        result = 31 * result + isCustom.hashCode()
        result = 31 * result + (iconUri?.hashCode() ?: 0)
        result = 31 * result + (chainName?.hashCode() ?: 0)
        result = 31 * result + address.hashCode()
        result = 31 * result + this@WalletCurrency.symbol.hashCode()
        return result
    }
}

fun WalletCurrency.toAssetId(testnet: Boolean): String {
    require(isTONChain) { "WalletCurrency.toAssetId supports TON chain only" }
    val network = if (testnet) {
        "testnet"
    } else {
        "mainnet"
    }
    if (code.equals(WalletCurrency.GRAM_KEY, ignoreCase = true) && !isToken) {
        return "ton/$network/coin"
    }
    val addr = address
    val raw = try {
        addr.toRawAddress()
    } catch (_: Throwable) {
        addr
    }
    return "ton/$network/jetton/$raw"
}