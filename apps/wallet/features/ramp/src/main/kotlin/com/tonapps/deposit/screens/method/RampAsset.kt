package com.tonapps.deposit.screens.method

import android.net.Uri
import android.os.Parcelable
import com.tonapps.blockchain.model.legacy.WalletCurrency
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
sealed interface RampAsset : Parcelable {

    val network: String?
    val currencyCode: String
    val isTon: Boolean
    val isStablecoin: Boolean
    val isUsdt: Boolean
    val imageUrl: Uri?
    val toCurrency: WalletCurrency

    @Serializable
    @Parcelize
    data class Currency(val currency: WalletCurrency) : RampAsset {
        override val currencyCode: String get() = currency.code
        override val isTon: Boolean get() = false
        override val isUsdt: Boolean get() = currency.isUSDT
        override val imageUrl: Uri? get() = currency.iconUri
        override val network: String? get() = currency.network
        override val isStablecoin: Boolean get() = currency.isStablecoin
        override val toCurrency: WalletCurrency get() = currency
    }
}
