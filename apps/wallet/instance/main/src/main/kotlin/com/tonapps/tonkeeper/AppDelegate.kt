package com.tonapps.tonkeeper

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.lifecycle.ProcessLifecycleInitializer
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.startup.AppInitializer
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.util.DebugLogger
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.ton_keeper.BuildConfig
import com.tonapps.async.Async
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.core.IssueHelper
import com.tonapps.bus.core.tracer.AnalyticException
import com.tonapps.chainkit.core.log.AndroidInitializer
import com.tonapps.core.flags.FeatureManager
import com.tonapps.core.flags.InAppReviewManager
import com.tonapps.core.flags.RemoteConfig
import com.tonapps.core.flags.TooltipManager
import com.tonapps.core.flags.WalletFeatureKey
import com.tonapps.core.helper.T
import com.tonapps.dapp.wcModule
import com.tonapps.deposit.depositModule
import com.tonapps.extensions.AppContext
import com.tonapps.extensions.cacheFolder
import com.tonapps.extensions.cacheSharedFolder
import com.tonapps.extensions.packageInfo
import com.tonapps.extensions.pubKey
import com.tonapps.extensions.setLocales
import com.tonapps.extensions.versionCodeCompat
import com.tonapps.log.L
import com.tonapps.log.LoggerConfig
import com.tonapps.migration.migrationModule
import com.tonapps.mvi.Mvi
import com.tonapps.network.SafeProxySelector
import com.tonapps.perps.perpsModule
import com.tonapps.portfolio.portfolioModule
import com.tonapps.tonkeeper.api.appApiModule
import com.tonapps.tonkeeper.core.DevSettings
import com.tonapps.tonkeeper.koin.koinModel
import com.tonapps.tonkeeper.koin.viewModelWalletModule
import com.tonapps.tonkeeper.koin.workerModule
import com.tonapps.trading.tradingModule
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.api.apiModule
import com.tonapps.wallet.chainKitModule
import com.tonapps.wallet.data.account.accountModule
import com.tonapps.wallet.data.attribution.AttributionConfig
import com.tonapps.wallet.data.attribution.AttributionManager
import com.tonapps.wallet.data.attribution.attributionModule
import com.tonapps.wallet.data.backup.backupModule
import com.tonapps.wallet.data.banner.bannerModule
import com.tonapps.wallet.data.battery.batteryModule
import com.tonapps.wallet.data.browser.browserModule
import com.tonapps.wallet.data.cache.cacheModule
import com.tonapps.wallet.data.collectibles.collectiblesModule
import com.tonapps.wallet.data.contacts.contactsModule
import com.tonapps.wallet.data.core.dataModule
import com.tonapps.wallet.data.dapps.dAppsModule
import com.tonapps.wallet.data.dapps.source.DappsDatabase
import com.tonapps.wallet.data.events.eventsModule
import com.tonapps.wallet.data.multichain.exchange.mcExchangeModule
import com.tonapps.wallet.data.multichain.mcWalletModule
import com.tonapps.wallet.data.passcode.passcodeModule
import com.tonapps.wallet.data.plugins.pluginsModule
import com.tonapps.wallet.data.purchase.purchaseModule
import com.tonapps.wallet.data.earn.earnModule
import com.tonapps.wallet.data.raffle.raffleModule
import com.tonapps.wallet.data.rates.ratesModule
import com.tonapps.wallet.data.rn.rnLegacyModule
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.staking.stakingModule
import com.tonapps.wallet.data.swap.swapModule
import com.tonapps.wallet.data.token.tokenModule
import com.tonapps.wallet.features.events.eventsFeatureModule
import com.tonapps.wc.WcInitializer
import com.tonapps.wc.models.WcWalletConfig
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.component.KoinComponent
import org.koin.core.context.GlobalContext.startKoin
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class AppDelegate : App(), CameraXConfig.Provider, KoinComponent, SingletonImageLoader.Factory {

    override fun onCreate() {
        SafeProxySelector.install(this)
        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//            StrictMode.setVmPolicy(
//                StrictMode.VmPolicy.Builder()
//                .detectLeakedClosableObjects()
//                .detectLeakedRegistrationObjects()
//                .detectActivityLeaks()
//                .penaltyLog()
//                .penaltyListener(Executors.newSingleThreadExecutor()) {
//                    L.e( it.cause ?: IllegalStateException("Unknown StrictMode error"), "StrictMode.VmPolicy: $it")
//                }.build())
        }

        AppContext.initialize(this)
        instance = this
        FirebaseApp.initializeApp(this)
        FirebaseMessaging.getInstance().setDeliveryMetricsExportToBigQuery(true)
        AnalyticsHelper.Default.applyInstallKeys(this)
        initChainKitContext()
        FeatureManager.initialize(this, RemoteConfig())
        initLogger(this)
        AnalyticsHelper.Default.start(this, WalletFeatureKey.ANALYTICS_PERSISTENT_CACHE)
        validateDatabases()
        T.initialize(this)
        initWc()
        TooltipManager.initialize(this)
        InAppReviewManager.initialize(this)
        Mvi.init(Mvi.Config(useThreadCheck = BuildConfig.DEBUG, isFastFail = BuildConfig.DEBUG))
        disableComposeSmartSelection()
        ChainKitProvider.init()
        setFormatterLocale(resources.configuration.locales[0])

        super.onCreate()
        updateThemes()

        Async.defaultScope().launch {
            DevSettings.checkInstallationVersion(BuildConfig.VERSION_CODE.toLong())
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(AnalyticProcessLifecycleObserver(this))

        val koin = startKoin {
            androidContext(this@AppDelegate)
            modules(
                koinModel,
                contactsModule,
                workerModule,
                dAppsModule,
                purchaseModule,
                batteryModule,
                stakingModule,
                passcodeModule,
                rnLegacyModule,
                swapModule,
                chainKitModule(this@AppDelegate, isLogging = true),
                backupModule,
                wcModule,
                dataModule,
                browserModule,
                bannerModule,
                raffleModule,
                earnModule,
                tradingModule,
                portfolioModule,
                migrationModule,
                perpsModule,
                depositModule,
                appApiModule,
                apiModule,
                accountModule,
                ratesModule,
                tokenModule,
                eventsModule,
                eventsFeatureModule,
                collectiblesModule,
                pluginsModule,
                cacheModule,
                attributionModule,
                mcWalletModule,
                mcExchangeModule,
                viewModelWalletModule,
            )
            workManagerFactory()
        }.koin

        initAttribution(koin.get())

        // The same list the platform gets, so the formatter never resolves a different locale.
        val localeList = koin.get<SettingsRepository>().localeList
        setFormatterLocale(localeList.get(0) ?: resources.configuration.locales[0])
        setLocales(localeList)
    }

    private fun initWc() {
        WcInitializer.tryToInit(
            this,
            WcWalletConfig(
                projectId = "d9e3b2252349d4b1f5b32d77c9c77082",
                appName = "Keeper",
                appDescription = "Keeper is a self-custody crypto wallet that supports 7+ networks. Buy, swap, send, trade and stake from one app, backed by a single recovery phrase that never leaves your device. Formerly Tonkeeper, with 77+ million downloads.",
                appUrl = "https://keeperwallet.com/",
                icon = "",
                relayUrl = null,
                redirect = null,
            ),
            onError = {
                AnalyticsHelper.Default.captureException(
                    AnalyticException.WalletConnect("WalletConnect init failed", it)
                )
            },
        )
    }

    private fun initAttribution(attributionManager: AttributionManager) {
        attributionManager.initialize(
            application = this,
            config = AttributionConfig(
                branchKey = "key_live_evAHVEIYXLiLyUU2dg8BupjkxCbVjapg",
                linkrunnerToken = "SmUhAMEH4C2ZdJgBrRXn5d79",
                isDebug = BuildConfig.DEBUG,
            ),
        )
    }

    private fun initChainKitContext() {
        val appInitializer = AppInitializer.getInstance(this)
        appInitializer.initializeComponent(AndroidInitializer::class.java)
        if (!appInitializer.isEagerlyInitialized(AndroidInitializer::class.java)) {
            reportSkippedChainKitInitializer(appInitializer)
        }
    }

    private fun reportSkippedChainKitInitializer(appInitializer: AppInitializer) {
        try {
            val hasStartupProviderRun = appInitializer.isEagerlyInitialized(ProcessLifecycleInitializer::class.java)
            IssueHelper.recordException(
                IllegalStateException(
                    "ChainKit AndroidInitializer was not run by InitializationProvider, " +
                        "startup provider ran: $hasStartupProviderRun, ${installedPackageState()}"
                )
            )
        } catch (_: Exception) {
        }
    }

    private fun installedPackageState(): String {
        val installedPackage = try {
            packageInfo
        } catch (_: PackageManager.NameNotFoundException) {
            return "installed package not found, running versionCode: ${BuildConfig.VERSION_CODE}"
        }
        val secondsSinceUpdate = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - installedPackage.lastUpdateTime)
        return "installed versionCode: ${installedPackage.versionCodeCompat}, " +
            "running versionCode: ${BuildConfig.VERSION_CODE}, " +
            "seconds since update: $secondsSinceUpdate"
    }

    private fun validateDatabases() {
        DappsDatabase.validate()
    }

    @OptIn(ExperimentalFoundationApi::class)
    private fun disableComposeSmartSelection() {
        ComposeFoundationFlags.isSmartSelectionEnabled = false
    }

    private fun initLogger(context: Context) {
        L.initialize(
            config = LoggerConfig(
                logsDir = context.cacheFolder("log"),
                sharedDir = context.cacheSharedFolder("log"),
                executor = ThreadPoolExecutor(
                    // TODO Move to ExecutorsFactory
                    0,
                    1,
                    200L,
                    TimeUnit.MILLISECONDS,
                    LinkedBlockingQueue(),
                ),
                scope = GlobalScope, // TODO replace on Async
                pubKeyProvider = { pubKey(context) }, // TODO provide aync encryption key
            ),
            targets = L.defaultTargets(context, DevSettings.isLogsEnabled, DevSettings.isLogcatEnabled),
            crashTarget = L.defaultCrashTarget(context),
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyConfiguration(newConfig)
    }

    override fun getCameraXConfig(): CameraXConfig {
        return CameraXConfig.Builder
            .fromConfig(Camera2Config.defaultConfig())
            .setMinimumLoggingLevel(Log.ERROR)
            .build()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(this, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheFolder("image_cache"))
                    .maxSizeBytes(100 * 1014 * 1024) // 100 MB
                    .build()
            }
            .crossfade(true)
            .allowHardware(true)
            .logger(DebugLogger())
            .build()
    }
}