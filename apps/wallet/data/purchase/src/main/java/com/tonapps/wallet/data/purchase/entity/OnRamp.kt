package com.tonapps.wallet.data.purchase.entity

import android.os.Parcelable
import android.util.Log
import com.tonapps.wallet.data.core.currency.WalletCurrency
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
    ): OnRamp()

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
            assets.filter { it.type.equals("fiat", ignoreCase = true) }.map { it.slug }.distinct()
        }

        @IgnoredOnParcel
        val tonAssets: TONAssetsEntity by lazy {
            TONAssetsEntity.of(assets)
        }

        @IgnoredOnParcel
        val externalCurrency: List<WalletCurrency> by lazy {
            val crypto = assets.filter {
                it.type.equals("crypto", ignoreCase = true)
            }.filter { it.image?.ifBlank { null } != null }

            val list = mutableListOf<WalletCurrency>()

            for (item in crypto) {
                val method = (item.inputMethods.firstOrNull() ?: item.outputMethods.firstOrNull()) ?: continue
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

        fun isValidPair(from: String, to: String): AllowedPair? {
            return allowedPairs.find { pair ->
                (pair.from.symbol.equals(from, true) && pair.to.symbol.equals(to, true))
                // || (pair.from.symbol.equals(to, true) && pair.to.symbol.equals(from, true))
            }
        }

    }
}