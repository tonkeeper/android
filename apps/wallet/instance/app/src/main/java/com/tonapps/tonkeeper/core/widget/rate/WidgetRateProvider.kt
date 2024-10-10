package com.tonapps.tonkeeper.core.widget.rate

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.common.ResizeOptions
import com.facebook.imagepipeline.request.ImageRequestBuilder
import com.tonapps.extensions.circle
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.core.widget.Widget
import com.tonapps.tonkeeper.extensions.getBitmap
import com.tonapps.tonkeeper.extensions.getDiffColor
import com.tonapps.tonkeeper.koin.koin
import com.tonapps.tonkeeper.manager.widget.WidgetManager
import com.tonapps.wallet.localization.Localization
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.Koin
import uikit.extensions.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class WidgetRateProvider: Widget<Widget.Params.Rate>() {

    override fun update(context: Context, manager: AppWidgetManager, id: Int) {
        val koin = context.koin ?: return
        val scope = koin.get<CoroutineScope>()
        scope.launch {
            val content = getContent(koin, context, id) ?: return@launch
            displayData(context, manager, id, content)
        }
    }

    private suspend fun getContent(
        koin: Koin,
        context: Context,
        id: Int
    ): Content.Rate? = withContext(Dispatchers.IO) {
        val params = WidgetManager.getRateParams(id) ?: return@withContext null
        val accountRepository = koin.get<AccountRepository>()
        val settingsRepository = koin.get<SettingsRepository>()
        val ratesRepository = koin.get<RatesRepository>()
        val currency = settingsRepository.currency
        val wallet = accountRepository.getWalletById(params.walletId) ?: return@withContext null
        val tokenRepository = koin.get<TokenRepository>()
        val token = if (params.jettonAddress.equals("TON", ignoreCase = true)) {
            tokenRepository.getTON(currency, wallet.accountId, wallet.testnet, true)?.balance?.token
        } else {
            tokenRepository.getToken(params.jettonAddress, wallet.testnet)
        }?: return@withContext null
        val rate = ratesRepository.getRates(currency, token.address).rate(token.address) ?: return@withContext null
        val price = rate.value
        val priceFormat = CurrencyFormatter.formatFiat(currency.code, price)

        Content.Rate(
            tokenName = token.symbol,
            tokenPrice = priceFormat,
            tokenIcon = loadTokenIcon(token.imageUri),
            diff24h = rate.diff.diff24h,
            updatedDate = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Calendar.getInstance().time)
        )
    }

    private suspend fun loadTokenIcon(uri: Uri): Bitmap {
        val size = 16.dp
        val imageRequest = ImageRequestBuilder.newBuilderWithSource(uri)
            .setResizeOptions(ResizeOptions.forSquareSize(size))
            .build()

        val bitmap = Fresco.getImagePipeline().getBitmap(imageRequest) ?: Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        return bitmap.circle()
    }

    private suspend fun displayData(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        content: Content.Rate
    ) = withContext(Dispatchers.Main) {
        val removeView = RemoteViews(context.packageName, R.layout.widget_rate)
        removeView.setTextViewText(R.id.symbol, content.tokenName)
        removeView.setOnClickPendingIntent(R.id.content, defaultPendingIntent)
        removeView.setTextViewText(R.id.update_date, context.getString(Localization.widget_updated, content.updatedDate))
        removeView.setTextViewText(R.id.price, content.tokenPrice.withCustomSymbol(context))
        removeView.setTextViewText(R.id.diff, content.diff24h)
        removeView.setImageViewBitmap(R.id.icon, content.tokenIcon)
        manager.updateAppWidget(id, removeView)
    }

}