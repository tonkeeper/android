package com.tonapps.tonkeeper.core.widget.balance

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.App
import com.tonapps.wallet.localization.Localization
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.core.widget.Widget
import com.tonapps.tonkeeper.koin.accountRepository
import com.tonapps.tonkeeper.koin.koin
import com.tonapps.tonkeeper.koin.settingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetBalanceProvider: Widget() {

    companion object {
        fun requestUpdate(context: Context = App.instance) {
            update(context, WidgetBalanceProvider::class.java)
        }
    }

    override fun update(context: Context, manager: AppWidgetManager, id: Int) {
        val koin = context.koin ?: return displayPlaceholderData(context, manager, id)
        val scope = koin.get<CoroutineScope>()
        val accountRepository = context.accountRepository!!
        val settingsRepository = context.settingsRepository!!
        val tokenRepository = koin.get<TokenRepository>()
        scope.launch(Dispatchers.IO) {
            val wallet = accountRepository.selectedWalletFlow.firstOrNull() ?: return@launch displayPlaceholder(context, manager, id)
            val tokens = tokenRepository.get(settingsRepository.currency, wallet.address, wallet.testnet)
            val token = tokens.firstOrNull { it.isTon } ?: return@launch displayPlaceholder(context, manager, id)
            val balanceFormat = CurrencyFormatter.format("TON", token.balance.value)
            val fiatBalance = CurrencyFormatter.formatFiat(settingsRepository.currency.code, token.fiat)
            val entity = WidgetBalanceEntity(
                tonBalance = balanceFormat,
                currencyBalance = fiatBalance,
                walletAddress = wallet.address,
                label = wallet.label.title
            )
            displayData(context, manager, id, entity)
        }
    }

    private fun displayPlaceholderData(
        context: Context,
        manager: AppWidgetManager,
        id: Int
    ) {
        val removeView = RemoteViews(context.packageName, R.layout.widget_balance)
        removeView.setOnClickPendingIntent(R.id.content, defaultPendingIntent)
        removeView.setTextViewText(R.id.address, context.getString(Localization.widget_placeholder))
        manager.updateAppWidget(id, removeView)
    }

    private suspend fun displayPlaceholder(
        context: Context,
        manager: AppWidgetManager,
        id: Int
    ) = withContext(Dispatchers.Main) {
        displayPlaceholderData(context, manager, id)
    }

    private suspend fun displayData(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        entity: WidgetBalanceEntity
    ) = withContext(Dispatchers.Main) {
        val removeView = RemoteViews(context.packageName, R.layout.widget_balance)
        removeView.setOnClickPendingIntent(R.id.content, defaultPendingIntent)
        removeView.setTextViewText(R.id.token, entity.tonBalance)
        removeView.setTextViewText(R.id.currency, entity.currencyBalance)
        removeView.setTextViewText(R.id.address, entity.name)
        manager.updateAppWidget(id, removeView)
    }

}
