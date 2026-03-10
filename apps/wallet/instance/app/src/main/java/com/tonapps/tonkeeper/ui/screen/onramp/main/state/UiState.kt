package com.tonapps.tonkeeper.ui.screen.onramp.main.state

import android.content.Context
import android.os.Parcelable
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.ui.screen.onramp.main.OnRampUtils
import com.tonapps.tonkeeper.ui.screen.onramp.main.entities.ProviderEntity
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.data.core.currency.WalletCurrency
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

sealed class UiState {

    enum class Step {
        Input, Confirm
    }

    data class RateFormatted(val from: CharSequence?, val to: CharSequence?): UiState()

    data class Balance(
        val balance: BalanceEntity? = null,
        val insufficientBalance: Boolean = false,
        val remainingFormat: CharSequence? = null
    ): UiState()

    data class MinAmount(
        val amount: Coins,
        val formatted: CharSequence
    ): UiState()

    @Parcelize
    data class SelectedProvider(
        val sendAmount: Coins,
        val send: WalletCurrency,
        val receive: WalletCurrency,
        val providers: List<ProviderEntity>,
        val selectedProviderId: String?,
    ): Parcelable {

        @IgnoredOnParcel
        val selectedProvider: ProviderEntity? by lazy {
            providers.firstOrNull {
                it.id.equals(selectedProviderId, true)
            } ?: providers.firstOrNull()
        }

        @IgnoredOnParcel
        val receiveAmount: Double
            get() = selectedProvider?.receive ?: 0.0

        @IgnoredOnParcel
        val receiveCoins: Coins by lazy {
            Coins.of(receiveAmount, receive.decimals)
        }

        @IgnoredOnParcel
        val receiveFormat: CharSequence by lazy {
            CurrencyFormatter.format(receive.code, receiveCoins)
        }

        @IgnoredOnParcel
        val sendFormat: CharSequence by lazy {
            CurrencyFormatter.format(send.code, sendAmount)
        }

        fun getPreviewText(context: Context) = selectedProvider?.let {
            OnRampUtils.createProviderTitle(context, it.title)
        }

        fun calculateRate(receiveAmount: Double): Coins {
            val receiveCoins = Coins.of(receiveAmount, receive.decimals)
            return receiveCoins / sendAmount
        }
    }
}