package com.tonkeeper.core.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import com.tonkeeper.App
import com.tonkeeper.R
import com.tonkeeper.api.account.AccountRepository
import com.tonkeeper.api.shortAddress
import com.tonkeeper.core.Coin
import com.tonkeeper.core.currency.from
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ton.SupportedTokens
import ton.extensions.toUserFriendly


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
            val wallet = App.walletManager.getWalletInfo()
            if (wallet == null) {
                displayPlaceholderData(context, manager, id)
                return@launch
            }

            val account = accountRepository.get(wallet.accountId)

            val tonInCurrency = from(SupportedTokens.TON, wallet.accountId)
                .value(account.balance)
                .to(currency)

            val balance = Balance(
                tonBalance = Coin.format(value = account.balance),
                currencyBalance = Coin.format(currency, tonInCurrency),
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
        removeView.setTextViewText(R.id.address, context.getString(R.string.widget_placeholder))
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
        removeView.setTextViewText(R.id.token, balance.tonBalance + " TON")
        removeView.setTextViewText(R.id.currency, balance.currencyBalance)
        removeView.setTextViewText(R.id.address, balance.walletAddress)
        manager.updateAppWidget(id, removeView)
    }


}
