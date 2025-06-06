package com.tonapps.tonkeeper.worker

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.facebook.drawee.backends.pipeline.Fresco
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.extensions.circle
import com.tonapps.extensions.isLocal
import com.tonapps.extensions.short12
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.extensions.loadDrawable
import com.tonapps.tonkeeper.extensions.loadSquare
import com.tonapps.tonkeeper.extensions.setOnClickIntent
import com.tonapps.tonkeeper.manager.assets.AssetsManager
import com.tonapps.tonkeeper.manager.widget.WidgetEntity
import com.tonapps.tonkeeper.manager.widget.WidgetManager
import com.tonapps.tonkeeper.manager.widget.WidgetParams
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.currency.WalletCurrency
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.rates.entity.RateEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uikit.extensions.activity
import uikit.extensions.dp
import uikit.extensions.drawable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class WidgetUpdaterWorker(
    private val context: Context,
    private val workParam: WorkerParameters,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val ratesRepository: RatesRepository,
    private val tokenRepository: TokenRepository,
    private val assetsManager: AssetsManager,
): CoroutineWorker(context, workParam) {

    private val appWidgetManager: AppWidgetManager by lazy {
        AppWidgetManager.getInstance(context)
    }

    private val currency: WalletCurrency
        get() = settingsRepository.currency

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val widgets = WidgetManager.getWidgets(context).reversed()
            if (widgets.isEmpty()) {
                throw IllegalStateException("Widgets not found")
            }
            updateRates(widgets)
            for (widget in widgets) {
                when (widget.type) {
                    WidgetManager.TYPE_RATE -> updateRateWidget(widget)
                    WidgetManager.TYPE_BALANCE -> updateBalanceWidget(widget)
                    else -> throw IllegalStateException("Unknown widget type=${widget.type}")
                }
            }
            Result.success()
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            Result.failure()
        }
    }

    private suspend fun updateRates(widgets: List<WidgetEntity>) = withContext(Dispatchers.IO) {
        val tokens = widgets.map { it.params }.filterIsInstance<WidgetParams.Rate>().map { it.jettonAddress.toRawAddress() }
        ratesRepository.load(currency, tokens.distinct().toMutableList())
    }

    private suspend fun getRates(tokenAddress: String): RateEntity? {
        return ratesRepository.getRates(currency, tokenAddress).rate(tokenAddress)
    }

    private suspend fun updateRateWidget(widget: WidgetEntity) {
        val params = widget.params as WidgetParams.Rate
        val wallet = getWallet(params.walletId)
        val token = getToken(wallet, params.jettonAddress) ?: throw IllegalStateException("Token not found params=${params}; wallet=${wallet}")
        val rate = getRates(token.address) ?: throw IllegalStateException("Rate not found token=${token}")

        updateRateWidget(
            widgetId = widget.id,
            jettonAddress = token.address,
            symbol = token.symbol,
            price = CurrencyFormatter.formatFiat(currency.code, rate.value),
            diff = rate.diff.diff24h,
            updatedDate = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Calendar.getInstance().time),
            icon = loadTokenBitmap(token.imageUri)
        )
    }

    private suspend fun updateRateWidget(
        widgetId: Int,
        jettonAddress: String,
        symbol: String,
        price: CharSequence,
        diff: String,
        updatedDate: String,
        icon: Bitmap
    ) = withContext(Dispatchers.Main) {
        val removeView = RemoteViews(context.packageName, R.layout.widget_rate)
        removeView.setTextViewText(R.id.symbol, symbol)
        removeView.setOnClickIntent(context, android.R.id.background, Uri.parse("tonkeeper://token/$jettonAddress"))
        removeView.setTextViewText(R.id.update_date, context.getString(Localization.widget_updated, updatedDate))
        removeView.setTextViewText(R.id.price, price.withCustomSymbol(context))
        removeView.setTextViewText(R.id.diff, diff)
        removeView.setImageViewBitmap(R.id.icon, icon)
        appWidgetManager.updateAppWidget(widgetId, removeView)
    }

    private suspend fun loadTokenBitmap(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val size = 16.dp
        val bitmap = (if (uri.isLocal) {
            uri.loadDrawable(context)?.toBitmap(size, size)?.circle()
        } else {
            Fresco.getImagePipeline().loadSquare(uri, size)
        })?.circle()

        bitmap ?: Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    }

    private suspend fun updateBalanceWidget(widget: WidgetEntity) {
        val params = widget.params as WidgetParams.Balance
        val wallet = getWallet(params.walletId) ?: throw IllegalStateException("Wallet not found params=${params}")
        val balance = assetsManager.requestTotalBalance(wallet, currency, sorted = true, refresh = true) ?: throw IllegalStateException("Balance not found params=${params}; wallet=${wallet}")
        val balanceFormat = CurrencyFormatter.formatFiat(currency.code, balance)
        val drawable = context.drawable(R.drawable.ic_widget_logo_24, wallet.label.color)

        updateBalanceWidget(
            widgetId = widget.id,
            walletId = wallet.id,
            label = wallet.label.title?.toString() ?: wallet.address.short12,
            balance = balanceFormat,
            icon = drawable.toBitmap(24.dp, 24.dp)
        )
    }

    private suspend fun updateBalanceWidget(
        widgetId: Int,
        walletId: String,
        label: String,
        balance: CharSequence,
        icon: Bitmap,
    ) = withContext(Dispatchers.Main) {
        val removeView = RemoteViews(context.packageName, R.layout.widget_balance)
        removeView.setOnClickIntent(context, android.R.id.background, Uri.parse("tonkeeper://pick/$walletId"))
        removeView.setTextViewText(R.id.fiat, balance)
        removeView.setTextViewText(R.id.address, label)
        removeView.setImageViewBitmap(R.id.icon, icon)
        appWidgetManager.updateAppWidget(widgetId, removeView)
    }

    private suspend fun getToken(wallet: WalletEntity?, tokenAddress: String): TokenEntity? {
        if (wallet == null) {
            return tokenRepository.getToken(tokenAddress)
        }
        return tokenRepository.getToken(wallet.accountId, wallet.testnet, tokenAddress)
    }

    private suspend fun getWallet(id: String): WalletEntity? {
        return accountRepository.getWalletById(id) ?: accountRepository.getSelectedWallet()
    }

    companion object {

        private const val NAME = "WidgetUpdaterWorker"

        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }

        fun start(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<WidgetUpdaterWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInputData(Data.EMPTY)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
        }

        fun update(context: Context) {
            val activity = context.activity
            if (activity == null) {
                updateInternal(context)
            } else {
                activity.runOnUiThread {
                    updateInternal(context)
                }
            }
        }

        private fun updateInternal(context: Context) {
            if (WidgetManager.hasWidgets(context)) {
                start(context)
            }
        }
    }

}