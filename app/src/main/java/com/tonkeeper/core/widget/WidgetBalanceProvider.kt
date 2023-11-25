package com.tonkeeper.core.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.tonkeeper.App
import com.tonkeeper.R
import com.tonkeeper.api.account.AccountRepository
import com.tonkeeper.api.shortAddress
import com.tonkeeper.api.userLikeAddress
import com.tonkeeper.core.Coin
import com.tonkeeper.core.currency.CurrencyManager
import com.tonkeeper.core.currency.from
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ton.SupportedTokens


class WidgetBalanceProvider: Widget() {

    companion object {
        fun requestUpdate(context: Context = App.instance) {
            update(context, WidgetBalanceProvider::class.java)
        }
    }

    private data class Balance(
        val tonBalance: String,
        val currencyBalance: String,
        val walletAddress: String
    )

    private val accountRepository = AccountRepository()

    @OptIn(DelicateCoroutinesApi::class)
    override fun update(context: Context, manager: AppWidgetManager, id: Int) {
        scope.launch(Dispatchers.IO) {
            val wallet = App.walletManager.getWalletInfo() ?: return@launch
            val account = accountRepository.get(wallet.accountId)

            val tonInCurrency = from(SupportedTokens.TON, wallet.accountId)
                .value(account.balance)
                .to(currency)

            val balance = Balance(
                tonBalance = Coin.format(value = account.balance),
                currencyBalance = Coin.format(currency, tonInCurrency),
                walletAddress = wallet.address.userLikeAddress.shortAddress
            )

            withContext(Dispatchers.Main) {
                displayData(context, manager, id, balance)
            }
        }
    }


    private fun displayData(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        balance: Balance
    ) {
        try {
            val removeView = RemoteViews(context.packageName, R.layout.widget_balance)
            removeView.setOnClickPendingIntent(R.id.content, defaultPendingIntent)
            removeView.setTextViewText(R.id.token, balance.tonBalance + " TON")
            removeView.setTextViewText(R.id.currency, balance.currencyBalance)
            removeView.setTextViewText(R.id.address, balance.walletAddress)
            manager.updateAppWidget(id, removeView)
        } catch (ignored: Throwable) {}
    }


}
