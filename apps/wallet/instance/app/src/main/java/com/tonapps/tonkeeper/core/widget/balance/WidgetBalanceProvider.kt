package com.tonapps.tonkeeper.core.widget.balance

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Color
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.App
import com.tonapps.wallet.localization.Localization
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.core.widget.Widget
import com.tonapps.tonkeeper.extensions.getTitle
import com.tonapps.tonkeeper.koin.accountRepository
import com.tonapps.tonkeeper.koin.koin
import com.tonapps.tonkeeper.koin.settingsRepository
import com.tonapps.tonkeeper.manager.assets.AssetsManager
import com.tonapps.tonkeeper.manager.widget.WidgetManager
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.Koin
import uikit.extensions.dp
import uikit.extensions.drawable
import uikit.extensions.sp

class WidgetBalanceProvider: Widget<Widget.Params.Balance>() {

    override fun update(context: Context, manager: AppWidgetManager, id: Int) {
        val koin = context.koin ?: return displayPlaceholderData(context, manager, id)
        val scope = koin.get<CoroutineScope>()
        scope.launch {
            val content = getContent(koin, context, id) ?: return@launch displayPlaceholder(context, manager, id)
            displayData(context, manager, id, content)
        }
    }

    private suspend fun getContent(
        koin: Koin,
        context: Context,
        id: Int
    ): Content.Balance? = withContext(Dispatchers.IO) {
        val params = WidgetManager.getBalanceParams(id) ?: return@withContext null
        val accountRepository = koin.get<AccountRepository>()
        val settingsRepository = koin.get<SettingsRepository>()
        val assetsManager = koin.get<AssetsManager>()
        val wallet = accountRepository.getWalletById(params.walletId) ?: return@withContext null
        val currency = settingsRepository.currency
        val fiatBalance = assetsManager.getTotalBalance(wallet, currency, sorted = true) ?: return@withContext null
        val fiatBalanceFormat = CurrencyFormatter.formatFiat(currency.code, fiatBalance)
        Content.Balance(
            fiatBalance = fiatBalanceFormat,
            walletAddress = wallet.address,
            label = wallet.label.title ?: context.getString(Localization.wallet),
            color = wallet.label.color
        )
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
        content: Content.Balance
    ) = withContext(Dispatchers.Main) {
        val drawable = context.drawable(R.drawable.ic_widget_logo_24, content.color)
        val bitmap = drawable.toBitmap(24.dp, 24.dp)
        val removeView = RemoteViews(context.packageName, R.layout.widget_balance)
        removeView.setOnClickPendingIntent(R.id.content, defaultPendingIntent)
        removeView.setTextViewText(R.id.fiat, content.fiatBalance)
        removeView.setTextViewText(R.id.address, content.name)
        removeView.setImageViewBitmap(R.id.icon, bitmap)
        manager.updateAppWidget(id, removeView)
    }

}
