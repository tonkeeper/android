package com.tonapps.tonkeeper

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig
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
import com.ton_keeper.BuildConfig
import com.tonapps.async.Async
import com.tonapps.core.flags.FeatureManager
import com.tonapps.core.flags.RemoteConfig
import com.tonapps.core.flags.TooltipManager
import com.tonapps.extensions.cacheFolder
import com.tonapps.extensions.cacheSharedFolder
import com.tonapps.extensions.pubKey
import com.tonapps.extensions.setLocales
import com.tonapps.log.L
import com.tonapps.log.LoggerConfig
import com.tonapps.core.helper.T
import com.tonapps.mvi.Mvi
import com.tonapps.tonkeeper.core.DevSettings
import com.tonapps.tonkeeper.koin.koinModel
import com.tonapps.tonkeeper.koin.viewModelWalletModule
import com.tonapps.tonkeeper.koin.workerModule
import com.tonapps.wallet.api.apiModule
import com.tonapps.wallet.data.account.accountModule
import com.tonapps.wallet.data.backup.backupModule
import com.tonapps.wallet.data.battery.batteryModule
import com.tonapps.wallet.data.browser.browserModule
import com.tonapps.wallet.data.collectibles.collectiblesModule
import com.tonapps.wallet.data.contacts.contactsModule
import com.tonapps.wallet.data.core.dataModule
import com.tonapps.wallet.data.dapps.dAppsModule
import com.tonapps.wallet.data.events.eventsModule
import com.tonapps.wallet.data.passcode.passcodeModule
import com.tonapps.wallet.data.plugins.pluginsModule
import com.tonapps.wallet.data.purchase.purchaseModule
import com.tonapps.wallet.data.rates.ratesModule
import com.tonapps.wallet.data.rn.rnLegacyModule
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.staking.stakingModule
import com.tonapps.wallet.data.swap.swapModule
import com.tonapps.wallet.data.token.tokenModule
import com.tonapps.trading.tradingModule
import com.tonapps.deposit.depositModule
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.GlobalContext.startKoin
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class AppDelegate : App(), CameraXConfig.Provider, KoinComponent, SingletonImageLoader.Factory {

    private val settingsRepository: SettingsRepository by inject()

    override fun onCreate() {
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

        instance = this
        initLogger(this)
        T.initialize(this)
        FeatureManager.initialize(this, RemoteConfig())
        TooltipManager.initialize(this)
        Mvi.init(Mvi.Config(useThreadCheck = BuildConfig.DEBUG, isFastFail = BuildConfig.DEBUG))

        super.onCreate()
        updateThemes()

        Async.defaultScope().launch {
            DevSettings.checkInstallationVersion(BuildConfig.VERSION_CODE.toLong())
        }

        FirebaseApp.initializeApp(this)

        startKoin {
            androidContext(this@AppDelegate)
            modules(
                koinModel,
                contactsModule,
                workerModule,
                dAppsModule,
                viewModelWalletModule,
                purchaseModule,
                batteryModule,
                stakingModule,
                passcodeModule,
                rnLegacyModule,
                swapModule,
                backupModule,
                dataModule,
                browserModule,
                tradingModule,
                depositModule,
                apiModule,
                accountModule,
                ratesModule,
                tokenModule,
                eventsModule,
                collectiblesModule,
                pluginsModule
            )
            workManagerFactory()
        }

        setLocales(settingsRepository.localeList)
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
            targets = L.defaultTargets(context, DevSettings.isLogsEnabled),
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