package com.tonapps.tonkeeper.ui.screen.onramp.main

import android.content.Context
import com.tonapps.extensions.getParcelable
import com.tonapps.extensions.prefs
import com.tonapps.extensions.putParcelable
import com.tonapps.extensions.putString
import com.tonapps.wallet.data.core.currency.WalletCurrency

class OnRampSettings(context: Context) {

    private companion object {
        private const val FROM_CURRENCY_KEY = "from_currency"
        private const val TO_CURRENCY_KEY = "to_currency"
        private const val PAYMENT_METHOD_KEY = "payment_method"
    }

    private val prefs = context.prefs("onrmap_screen")

    fun setFromCurrency(currency: WalletCurrency) = prefs.putParcelable(FROM_CURRENCY_KEY, currency)

    fun getFromCurrency(): WalletCurrency? = prefs.getParcelable(FROM_CURRENCY_KEY)

    fun setToCurrency(currency: WalletCurrency) = prefs.putParcelable(TO_CURRENCY_KEY, currency)

    fun getToCurrency(): WalletCurrency? = prefs.getParcelable(TO_CURRENCY_KEY)

    fun setPaymentMethod(method: String) = prefs.putString(PAYMENT_METHOD_KEY, method)

    fun getPaymentMethod(): String? = prefs.getString(PAYMENT_METHOD_KEY, null)
}