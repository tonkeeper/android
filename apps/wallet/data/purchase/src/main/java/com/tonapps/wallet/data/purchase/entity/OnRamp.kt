@file:SuppressLint("UnsafeOptInUsageError")
package com.tonapps.wallet.data.purchase.entity

import android.annotation.SuppressLint
import android.os.Parcelable
import com.tonapps.extensions.lazyUnsafe
import com.tonapps.blockchain.model.legacy.WalletCurrency
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Parcelize
sealed class OnRamp: Parcelable {

    @Serializable
    @Parcelize
    data class SymbolWrapper(val symbol: String): OnRamp()

    @Serializable
    @Parcelize
    data class Method(
        val image: String,
        val type: String
    ): OnRamp() {

        @IgnoredOnParcel
        val title: String by lazyUnsafe {
            when (type) {
                "card" -> "Credit Card"
                "revolut" -> "Revolut"
                "google_pay" -> "Google Pay"
                "paypal" -> "PayPal"
                else -> type.replace("_", " ")
                    .replaceFirstChar { it.uppercaseChar() }
            }
        }


    }

    @Serializable
    @Parcelize
    data class PaymentMethodMerchant(
        val merchant: String,
        val methods: List<Method>
    ): OnRamp()

    @Serializable
    @Parcelize
    data class Limits(
        val min: Double,
        val max: Double? = null
    ): OnRamp()

    @Serializable
    @Parcelize
    data class SlugWrapper(
        val slug: String,
        val limits: Limits? = null
    ): OnRamp()

    @Serializable
    @Parcelize
    data class PaymentMethod(
        val type: String,
        val address: String? = null
    ): OnRamp()

    @Serializable
    @Parcelize
    data class AllowedPair(
        val from: SymbolWrapper,
        val to: SymbolWrapper,
        val merchants: List<SlugWrapper>
    ): OnRamp() {

        @IgnoredOnParcel
        val min: Double by lazy {
            merchants.mapNotNull { it.limits?.min }.minOrNull() ?: 0.0
        }

        @IgnoredOnParcel
        val max: Double? by lazy {
            merchants.mapNotNull { it.limits?.max }.maxOrNull()
        }
    }

    @Serializable
    @Parcelize
    data class Merchant(
        @SerialName("input_methods")
        val inputMethods: List<String>,
        val name: String,
        @SerialName("output_methods")
        val outputMethods: List<String>,
        val slug: String
    ): OnRamp()

    @Serializable
    @Parcelize
    data class Asset(
        @SerialName("input_methods")
        val inputMethods: List<PaymentMethod>,
        @SerialName("output_methods")
        val outputMethods: List<PaymentMethod>,
        val slug: String,
        val type: String,
        val image: String? = null
    ): OnRamp()

    @Serializable
    @Parcelize
    data class Data(
        @SerialName("allowed_pairs")
        val allowedPairs: List<AllowedPair>,
        val assets: List<Asset>,
        val merchants: List<Merchant>,
    ): OnRamp() {

        @IgnoredOnParcel
        val fiat: OnRampCurrencies by lazy {
            OnRampCurrencies.fiat(assets)
        }

        @IgnoredOnParcel
        val availableFiatSlugs: List<String> by lazy {
            assets.filter { it.type.equals("fiat", ignoreCase = true) }
                .map { it.slug }
                .distinct()
        }

        @IgnoredOnParcel
        val tonAssets: TONAssetsEntity by lazy {
            TONAssetsEntity.of(assets)
        }

        @IgnoredOnParcel
        val externalCurrency: List<WalletCurrency> by lazy {
            val crypto = assets
                .filter {
                    it.type.equals("crypto", ignoreCase = true) && !it.image.isNullOrBlank()
                }

            val list = mutableListOf<WalletCurrency>()

            for (item in crypto) {
                val method = item.inputMethods.firstOrNull()
                    ?: item.outputMethods.firstOrNull()
                    ?: continue

                if (method.type == "native") {
                    val currency = WalletCurrency.of(item.slug) ?: continue

                    if (currency.isTONChain) {
                        continue
                    }

                    list.add(currency)
                } else {
                    val chainAddress = method.address ?: continue
                    val chain = WalletCurrency.createChain(method.type, chainAddress)

                    if (chain is WalletCurrency.Chain.TON) {
                        continue
                    }

                    val currency = WalletCurrency(
                        code = item.slug,
                        title = item.slug,
                        chain = chain,
                        iconUrl = item.image
                    )

                    list.add(currency)
                }
            }

            list.add(1, WalletCurrency.USDT_ARBITRUM)
            list.add(1, WalletCurrency.USDT_AVALANCHE)
            list.add(1, WalletCurrency.USDT_BEP20)
            list.add(1, WalletCurrency.USDT_SPL)
            list.add(1, WalletCurrency.USDT_ETH)
            list.add(1, WalletCurrency.USDT_TRON)

            WalletCurrency.sort(list)
        }

        fun findValidPairs(from: String, to: String): Pairs {
            val pairs = allowedPairs.filter { pair ->
                (pair.from.symbol.equals(from, true) && pair.to.symbol.equals(to, true))
            }
            val merchantSlugs = pairs.map { it.merchants }.flatten().map { it.slug }
            val availableMerchants = merchants.filter { merchantSlugs.contains(it.slug) }
            return Pairs(pairs, availableMerchants)
        }
    }

    data class Pairs(
        val pairs: List<AllowedPair>,
        val merchants: List<Merchant>
    ) {

        val pair: AllowedPair? by lazy {
            pairs.firstOrNull()
        }

        val min: Double by lazy {
            pairs.minOfOrNull { it.min } ?: 0.0
        }
    }
}