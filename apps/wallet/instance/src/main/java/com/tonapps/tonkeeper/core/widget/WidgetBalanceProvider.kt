package com.tonapps.tonkeeper.core.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.wallet.localization.Localization
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.api.account.AccountRepository
import com.tonapps.tonkeeper.api.shortAddress
import com.tonapps.blockchain.Coin
import com.tonapps.tonkeeper.core.currency.ton
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetBalanceProvider: Widget() {

    companion object {
        fun requestUpdate(context: Context = com.tonapps.tonkeeper.App.instance) {
            update(context, WidgetBalanceProvider::class.java)
        }
    }

    private data class Balance(
        val tonBalance: CharSequence,
        val currencyBalance: CharSequence,
        val walletAddress: String
    )

    private val accountRepository = AccountRepository()

    @OptIn(DelicateCoroutinesApi::class)
    override fun update(context: Context, manager: AppWidgetManager, id: Int) {
        scope.launch(Dispatchers.IO) {
            val wallet = com.tonapps.tonkeeper.App.walletManager.getWalletInfo()
            if (wallet == null) {
                displayPlaceholderData(context, manager, id)
                return@launch
            }

            var response = accountRepository.getFromCloud(wallet.accountId, wallet.testnet)
            if (response == null) {
                response = accountRepository.get(wallet.accountId, wallet.testnet)
            }
            val account = response?.data ?: return@launch

            val tonInCurrency = wallet.ton(account.balance)
                .convert(currency.code)

            val amount = Coin.toCoins(account.balance)

            val balance = Balance(
                tonBalance = CurrencyFormatter.format("TON", amount),
                currencyBalance = CurrencyFormatter.formatFiat(currency.code, tonInCurrency),
                walletAddress = wallet.address.shortAddress
            )

            displayData(context, manager, id, balance)
        }
    }

    private suspend fun displayPlaceholderData(
        context: Context,
        manager: AppWidgetManager,
        id: Int
    ) = withContext(Dispatchers.Main) {
        val removeView = RemoteViews(context.packageName, R.layout.widget_balance)
        removeView.setOnClickPendingIntent(R.id.content, defaultPendingIntent)
        removeView.setTextViewText(R.id.address, context.getString(Localization.widget_placeholder))
        manager.updateAppWidget(id, removeView)
    }

    private suspend fun displayData(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        balance: Balance
    ) = withContext(Dispatchers.Main) {
        val removeView = RemoteViews(context.packageName, R.layout.widget_balance)
        removeView.setOnClickPendingIntent(R.id.content, defaultPendingIntent)
        removeView.setTextViewText(R.id.token, balance.tonBalance)
        removeView.setTextViewText(R.id.currency, balance.currencyBalance)
        removeView.setTextViewText(R.id.address, balance.walletAddress)
        manager.updateAppWidget(id, removeView)
    }


}
