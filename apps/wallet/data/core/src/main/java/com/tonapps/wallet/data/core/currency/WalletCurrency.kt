package com.tonapps.wallet.data.core.currency

import android.net.Uri
import android.os.Parcelable
import androidx.annotation.DrawableRes
import com.tonapps.extensions.toUriOrNull
import com.tonapps.uikit.flag.getFlagDrawable
import com.tonapps.wallet.api.R
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import androidx.core.net.toUri

@Parcelize
data class WalletCurrency(
    val code: String,
    val title: String,
    val alias: String = "",
    val chain: Chain,
    val iconUrl: String? = null
): Parcelable {

    sealed class Chain(
        open val decimals: Int,
        val name: String
    ): Parcelable {

        @Parcelize
        data class FIAT(
            val countryCode: String
        ): Chain(6, "FIAT")

        @Parcelize
        data class TON(
            val address: String = TON_KEY,
            override val decimals: Int = 9,
        ): Chain(decimals, "TON")

        @Parcelize
        data class TRON(
            val address: String = "TRON",
            override val decimals: Int = 6,
        ): Chain(decimals, "TRON")

        @Parcelize
        data object BTC: Chain(8, "BTC")

        @Parcelize
        data class ETC(
            val address: String = "ETC",
            override val decimals: Int = 12,
        ): Chain(decimals, "ETC")

        @Parcelize
        data class BNB(
            val address: String = "BNB",
            override val decimals: Int = 18,
        ): Chain(decimals, "BNB")

        @Parcelize
        data class Solana(
            val address: String = "SOL",
            override val decimals: Int = 9,
        ): Chain(decimals, "SOL")

        @Parcelize
        data class Unknown(
            val type: String,
            val address: String,
            override val decimals: Int = 6,
        ): Chain(decimals, type.uppercase())
    }

    companion object {

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

        val FIAT = listOf(
            "USD", // United States Dollar
            "EUR", // Euro
            "RUB", // Russian Ruble
            "AED", // United Arab Emirates Dirham
            "UAH", // Ukrainian Hryvnia
            "KZT", // Kazakhstani Tenge
            "UZS", // Uzbekistani sum
            "GBP", // Great Britain Pound
            "CHF", // Swiss Franc
            "CNY", // China Yuan
            "GEL", // Georgian Lari
            "KRW", // South Korean Won
            "IDR", // Indonesian Rupiah
            "INR", // Indian Rupee
            "JPY", // Japanese Yen
            "CAD", // Canadian Dollar
            "ARS", // Argentine Peso
            "BYN", // Belarusian Ruble
            "COP", // Colombian Peso
            "ETB", // Ethiopian Birr
            "ILS", // Israeli Shekel
            "KES", // Kenyan Shilling
            "NGN", // Nigerian Naira
            "UGX", // Ugandan Shilling
            "VES", // Venezuelan Bolivar
            "ZAR", // South African Rand
            "TRY", // Turkish Lira
            "THB", // Thai Baht
            "VND", // Vietnamese Dong
            "BRL", // Brazilian Real
            "BDT", // Bangladeshi Taka
            "AUD", // Australian Dollar
            "HKD", // Hong Kong Dollar
            "SGD", // Singapore Dollar
            "ISK", // Icelandic Króna
            "PHP", // Philippine Peso
            "FJD", // Fijian Dollar
            "AOA", // Angolan Kwanza
            "MGA", // Malagasy Ariary
            "FKP", // Falkland Islands Pound
            "BSD", // Bahamian Dollar
            "PGK", // Papua New Guinean Kina
            "TOP", // Tongan Paʻanga
            "XCD", // East Caribbean Dollar
            "MDL", // Moldovan Leu
            "PEN", // Peruvian Sol
            "BHD", // Bahraini Dinar
            "HNL", // Honduran Lempira
            "GTQ", // Guatemalan Quetzal
            "GHS", // Gibraltar Pound
            "BZD", // Belize Dollar
            "SBD", // Solomon Islands Dollar
            "MRU", // Mauritanian Ouguiya
            "OMR", // Omani Rial
            "KGS", // Kyrgyzstani Som
            "PLN", // Polish Zloty
            "CLP", // Chilean Peso
            "SCR", // Seychellois Rupee
            "DOP", // Dominican Peso
            "LKR", // Sri Lankan Rupee
            "QAR", // Qatari Rial
            "KYD", // Cayman Islands Dollar
            "RWF", // Rwandan Franc
            "AZN", // Azerbaijani Manat
            "CZK", // Czech Koruna
            "CRC", // Costa Rican Colón
            "BGN", // Bulgarian Lev
            "BMD", // Bermudian Dollar
            "DKK", // Danish Krone
            "UYU", // Uruguayan Peso
            "DZD", // Algerian Dinar
            "BAM", // Bosnian Convertible
            "STN", // São Tomé and Príncipe Dobra
            "TMT", // Turkmenistani Manat
            "KHR", // Cambodian Riel
            "BND", // Brunei Dollar
            "MXN", // Mexican Peso
            "NZD", // New Zealand Dollar
            "MKD", // Macedonian Denar
            "JOD", // Jordanian Dinar
            "MWK", // Malawian Kwacha
            "TTD", // Trinidad and Tobago Dollar
            "KMF", // Comorian Franc
            "SRD", // Surinamese Dollar
            "TJS", // Tajikistani Somoni
            "CVE", // Cape Verdean Escudo
            "HUF", // Hungarian Forint
            "PYG", // Paraguayan Guarani
            "SEK", // Swedish Krona
            "ANG", // Netherlands Antillean Guilder
            "MYR", // Malaysian Ringgit
            "TWD", // New Taiwan Dollar
            "SZL", // Swazi Lilangeni
            "RSD", // Serbian Dinar
            "DJF", // Djiboutian Franc
            "AMD", // Armenian Dram
            "PAB", // Panamanian Balboa
            "ZAR", // South African Rand
            "KWD", // Kuwaiti Dinar
            "RON", // Romanian Leu
            "EGP", // Egyptian Pound
        )

        const val USDT_KEY = "USDT"
        const val USDE_KEY = "USDE"
        const val TON_KEY = "TON"
        const val BTC_KEY = "BTC"
        const val ETH_KEY = "ETH"

        private val USDT_TRON_ADDRESS = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
        private val USDT_TON_ADDRESS = "0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe"
        private val USDT_ETH_ADDRESS = "0xdac17f958d2ee523a2206206994597c13d831ec7"

        val USDE_TON_ETHENA_ADDRESS = "0:086fa2a675f74347b08dd4606a549b8fdb98829cb282bc1949d3b12fbaed9dcc"

        val USD = WalletCurrency(
            code = "USD",
            title = CurrencyCountries.getCurrencyTitle("USD"),
            chain = Chain.FIAT("US")
        )

        val EUR = WalletCurrency(
            code = "EUR",
            title = CurrencyCountries.getCurrencyTitle("EUR"),
            chain = Chain.FIAT("EU")
        )

        val DEFAULT = USD

        val TON = WalletCurrency(
            code = TON_KEY,
            title = "Toncoin",
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

        fun createChain(type: String, address: String): Chain {
            return when (type) {
                "jetton" -> Chain.TON(address)
                "erc-20" -> Chain.ETC(address)
                "bep-20" -> Chain.BNB(address)
                "spl" -> Chain.Solana(address)
                else -> Chain.Unknown(type, address)
            }
        }

        private fun createAlias(chainCode: String, tokenCode: String): String {
            if (chainCode.equals(tokenCode, ignoreCase = true) || tokenCode.isBlank()) {
                return ""
            }
            return "${chainCode}_${tokenCode}"
        }

        val USDE_TON_ETHENA = WalletCurrency(
            code = USDE_KEY,
            alias = createAlias(TON_KEY, USDE_KEY),
            title = "Ethena USDe",
            chain = Chain.TON(USDE_TON_ETHENA_ADDRESS, 6)
        )

        val USDT_TRON = WalletCurrency(
            code = USDT_KEY,
            alias = createAlias("TRON", USDT_KEY),
            title = "Tether",
            chain = Chain.TRON(USDT_TRON_ADDRESS, 6)
        )

        val USDT_TON = WalletCurrency(
            code = USDT_KEY,
            alias = createAlias(TON_KEY, USDT_KEY),
            title = "Tether",
            chain = Chain.TON(USDT_TON_ADDRESS, 6)
        )

        val USDT_SPL = WalletCurrency(
            code = USDT_KEY,
            alias = createAlias("SPL", USDT_KEY),
            title = "Tether",
            chain = Chain.Solana("Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB", 6)
        )

        val USDT_ETH = WalletCurrency(
            code = USDT_KEY,
            alias = createAlias(ETH_KEY, USDT_KEY),
            title = "Tether",
            chain = Chain.ETC(USDT_ETH_ADDRESS, 6)
        )

        val USDT_BEP20 = WalletCurrency(
            code = USDT_KEY,
            alias = createAlias("BEP20", USDT_KEY),
            title = "Tether",
            chain = Chain.BNB("0x55d398326f99059fF775485246999027B3197955", 6)
        )

        val USDT_AVALANCHE = WalletCurrency(
            code = USDT_KEY,
            alias = createAlias("AVALANCHE", USDT_KEY),
            title = "Tether",
            chain = Chain.Unknown("AVALANCHE", "0x9702230a8ea53601f5cd2dc00fdbc13d4df4a8c7", 6)
        )

        val USDT_ARBITRUM = WalletCurrency(
            code = USDT_KEY,
            alias = createAlias("ARBITRUM", USDT_KEY),
            title = "Tether",
            chain = Chain.Unknown("ARBITRUM", "0xfd086bc7cd5c481dcc9c85ebe478a1c0b69fcbb9", 6)
        )

        val ALL = FIAT

        fun sort(value: List<WalletCurrency>): List<WalletCurrency> {
            return value.sortedWith(compareBy { currency ->
                val index = popularCurrencies.indexOf(currency.code)
                if (index >= 0) index else Int.MAX_VALUE
            })
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
                return R.drawable.ic_ton_with_bg
            } else if (currency == BTC) {
                return R.drawable.ic_btc_with_bg
            } else if (currency == ETH) {
                return R.drawable.ic_eth_with_bg
            }
            return null
        }

        fun ofOrDefault(code: String?): WalletCurrency {
            return of(code) ?: DEFAULT
        }

        fun isValid(code: String): Boolean {
            return of(code) != null
        }

        fun of(code: String?): WalletCurrency? {
            if (code.isNullOrBlank()) {
                return null
            } else if (code in FIAT) {
                return WalletCurrency(
                    code = code,
                    title = CurrencyCountries.getCurrencyTitle(code),
                    chain = Chain.FIAT(CurrencyCountries.getCountryCode(code))
                )
            } else if (code.equals(TON.code, ignoreCase = true)) {
                return TON
            } else if (code.equals(BTC.code, ignoreCase = true)) {
                return BTC
            } else if (code.equals(ETH.code, ignoreCase = true)) {
                return ETH
            } else if (USDT_TRON.equalsByCode(code)) {
                return USDT_TRON
            } else if (USDT_TON.equalsByCode(code)) {
                return USDT_TON
            } else if (USDT_ETH.equalsByCode(code)) {
                return USDT_ETH
            }
            return null
        }
    }

    @IgnoredOnParcel
    val fiat: Boolean
        get() = chain is Chain.FIAT

    @IgnoredOnParcel
    val isUSDT: Boolean
        get() = code.uppercase().replace("USD₮", USDT_KEY).equals(USDT_KEY, ignoreCase = true)

    @IgnoredOnParcel
    val isTONChain: Boolean
        get() = chain is Chain.TON

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
            chain.name.uppercase().replace("TRON", "TRC20")
        }
    }

    override fun equals(other: Any?): Boolean {
        val currency = other as? WalletCurrency ?: return false
        if (!code.equals(currency.code, true)) {
            return false
        }
        return chain.name.equals(currency.chain.name, true)
    }

    fun containsQuery(query: String): Boolean {
        return code.contains(query, ignoreCase = true) ||
                title.contains(query, ignoreCase = true) ||
                alias.contains(query, ignoreCase = true)
    }

    fun equalsByCode(other: String): Boolean {
        if (other.equals(code, ignoreCase = true) || (alias.isNotBlank() && alias.equals(other, ignoreCase = true))) {
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
}
