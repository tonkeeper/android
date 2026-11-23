package com.tonapps.tonkeeper.ui.screen.root

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Browser
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import com.tonapps.async.Async
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.TransferEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.TonSendMode
import com.tonapps.blockchain.ton.TonTransferHelper
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.asCellRef
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.AssetScreen.AssetScreenFrom
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.bus.generated.Events.DeeplinkOpen.DeeplinkOpenFrom
import com.tonapps.bus.generated.Events.EarnFlow.EarnEntryViewFrom
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerDeviceViewFrom
import com.tonapps.bus.generated.Events.LedgerFlow.LedgerMigrationStartFrom
import com.tonapps.bus.generated.Events.MysteryRaffle.MysteryRaffleSource
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowSource
import com.tonapps.bus.generated.Events.Migration.MigrationFrom
import com.tonapps.bus.generated.Events.Perps.PerpsEntryPoint
import com.tonapps.bus.screenrender.ScreenRenderId
import com.tonapps.bus.screenrender.ScreenRenderLaunchStep
import com.tonapps.bus.screenrender.ScreenRenderTracker
import com.tonapps.bus.screenrender.ScreenRenderTrigger
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.core.RNLegacyDelegate
import com.tonapps.core.deeplink.DeepLink
import com.tonapps.core.deeplink.DeepLinkBuilder
import com.tonapps.core.deeplink.DeepLinkRoute
import com.tonapps.core.deeplink.DeeplinkOpenAnalytics
import com.tonapps.core.flags.FeatureManager
import com.tonapps.core.flags.WalletFeature
import com.tonapps.wallet.api.internal.BootConfigOverrides
import com.tonapps.core.navigation.NavigationDelegate
import com.tonapps.core.navigation.PortfolioSearchAssetType
import com.tonapps.core.navigation.PortfolioSearchSort
import com.tonapps.dapp.screens.confirm.DappConfirmFragment
import com.tonapps.deposit.DepositFragment
import com.tonapps.deposit.DepositRoutes
import com.tonapps.deposit.WithdrawFragment
import com.tonapps.deposit.WithdrawRoutes
import com.tonapps.deposit.multicoin.DepositMulticoinFragment
import com.tonapps.deposit.multicoin.DepositMulticoinRoutes
import com.tonapps.deposit.multicoin.WcConfirmFragment
import com.tonapps.deposit.multicoin.WithdrawMulticoinFragment
import com.tonapps.deposit.multicoin.WithdrawMulticoinRoutes
import com.tonapps.deposit.multicoin.confirmTopUpScreen
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrames
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFramesHost
import com.tonapps.deposit.multicoin.screens.qr.ReceiveQrFragment
import com.tonapps.deposit.screens.method.PaymentMethodSectionFilter
import com.tonapps.deposit.screens.method.RampAsset
import com.tonapps.deposit.screens.qr.QrAssetFragment
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.deposit.usecase.emulation.EmulationUseCase
import com.tonapps.deposit.usecase.sign.SignProof
import com.tonapps.deposit.usecase.sign.SignTransaction
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.extensions.getStringValue
import com.tonapps.extensions.toUriOrNull
import com.tonapps.extensions.logError
import com.tonapps.icu.Coins.Companion.isPositive
import com.tonapps.ledger.ton.Transaction
import com.tonapps.perps.PerpsFragment
import com.tonapps.perps.funding.PerpsDepositArrivals
import com.tonapps.portfolio.screens.list.WalletsListFragment
import com.tonapps.portfolio.screens.raffle.RaffleFragment
import com.tonapps.portfolio.screens.manage.AccountsManageFragment
import com.tonapps.portfolio.screens.search.SearchFragment
import com.tonapps.scanner.ScannerFragment
import com.tonapps.swap.SwapFragment
import com.tonapps.swap.SwapRoutes
import com.tonapps.swap.status.SwapStatusToastHost
import com.tonapps.wallet.data.multichain.exchange.status.SwapStatusTracker
import com.tonapps.wallet.data.multichain.legacy.LegacySwapAccounts
import com.tonapps.wallet.data.multichain.legacy.LegacySwapAssets
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.core.DevSettings
import com.tonapps.tonkeeper.core.signer.SignerHelper
import com.tonapps.tonkeeper.extensions.confirmedToast
import com.tonapps.tonkeeper.extensions.getDefaultWalletTransfer
import com.tonapps.tonkeeper.extensions.hasRefer
import com.tonapps.tonkeeper.extensions.hasUtmSource
import com.tonapps.tonkeeper.extensions.isDarkMode
import com.tonapps.tonkeeper.extensions.openAppSettings
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.helper.BrowserHelper
import com.tonapps.tonkeeper.koin.analytics
import com.tonapps.tonkeeper.koin.remoteConfig
import com.tonapps.tonkeeper.manager.earn.EarnUrlResolver
import com.tonapps.tonkeeper.manager.push.PushManager
import com.tonapps.tonkeeper.manager.shortcut.AppShortcutRepository
import com.tonapps.tonkeeper.ui.base.BaseWalletActivity
import com.tonapps.tonkeeper.ui.base.QRCameraScreen
import com.tonapps.tonkeeper.ui.base.WalletFragmentFactory
import com.tonapps.tonkeeper.ui.screen.backup.main.BackupScreen
import com.tonapps.tonkeeper.ui.screen.battery.BatteryScreen
import com.tonapps.tonkeeper.ui.screen.browser.analytics.DappBrowserAnalytics
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppScreenFactory
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppScreen
import com.tonapps.tonkeeper.ui.screen.collectibles.main.CollectiblesScreen
import com.tonapps.tonkeeper.ui.screen.events.compose.details.TxDetailsScreen
import com.tonapps.tonkeeper.ui.screen.external.qr.keystone.sign.KeystoneSignScreen
import com.tonapps.tonkeeper.ui.screen.external.qr.signer.sign.SignerSignScreen
import com.tonapps.tonkeeper.ui.screen.init.InitArgs
import com.tonapps.tonkeeper.ui.screen.init.InitScreen
import com.tonapps.tonkeeper.ui.screen.ledger.proof.LedgerProofScreen
import com.tonapps.tonkeeper.ui.screen.ledger.sign.LedgerSignScreen
import com.tonapps.tonkeeper.ui.screen.main.MainScreen
import com.tonapps.tonkeeper.ui.screen.name.edit.EditNameScreen
import com.tonapps.tonkeeper.ui.screen.nft.NftScreen
import com.tonapps.tonkeeper.ui.screen.send.contacts.main.SendContactsScreen
import com.tonapps.tonkeeper.ui.screen.send.main.SendContact
import com.tonapps.tonkeeper.ui.screen.send.main.SendScreen
import com.tonapps.tonkeeper.ui.screen.send.transaction.SendTransactionScreen
import com.tonapps.tonkeeper.ui.screen.settings.main.SettingsScreen
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingScreen
import com.tonapps.tonkeeper.ui.screen.staking.viewer.StakeViewerScreen
import com.tonapps.tonkeeper.ui.screen.staking.withdraw.StakeWithdrawScreen
import com.tonapps.tonkeeper.ui.screen.start.StartScreen
import com.tonapps.tonkeeper.ui.screen.stories.remote.BATTERY_ONBOARDING_FROM
import com.tonapps.tonkeeper.ui.screen.stories.remote.RemoteStoriesScreen
import com.tonapps.tonkeeper.ui.screen.swap.omniston.OmnistonScreen
import com.tonapps.tonkeeper.ui.screen.token.unverified.TokenUnverifiedScreen
import com.tonapps.tonkeeper.ui.screen.token.viewer.TokenScreen
import com.tonapps.tonkeeper.ui.screen.tonconnect.TonConnectScreen
import com.tonapps.tonkeeper.ui.screen.watchonly.WatchInfoScreen
import com.tonapps.tonkeeper.worker.PushToggleWorker
import com.tonapps.tonkeeperx.R
import com.tonapps.trading.AssetsFragment
import com.tonapps.trading.isTonOrTronAssetId
import com.tonapps.uikit.color.backgroundPageColor
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.attribution.AttributionManager
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.core.Theme
import com.tonapps.wallet.data.core.entity.RawMessageEntity
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import com.tonapps.wallet.data.dapps.confirm.ConfirmSheetQueue
import com.tonapps.wallet.data.dapps.entities.AppEntity
import com.tonapps.wallet.data.events.tx.model.TxEvent
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.passcode.LockScreen
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.passcode.PasscodeReason
import com.tonapps.tonkeeper.ui.screen.networks.NewNetworksLock
import androidx.compose.ui.platform.ComposeView
import ui.theme.MoonTheme
import com.tonapps.wallet.data.passcode.dialog.PasscodeDialog
import com.tonapps.wallet.data.passcode.ui.PasscodeView
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.features.events.screens.EventsFragment
import com.tonapps.wallet.features.ledger.progress.LedgerProgressPresenter
import com.tonapps.wallet.features.ledger.wallet.LedgerWalletFragment
import com.tonapps.tonkeeper.ui.screen.events.compose.history.TxEventsScreen
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.ton.bitstring.BitString
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.cell.Cell
import org.ton.kotlin.crypto.PublicKeyEd25519
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.findFragment
import uikit.extensions.gestureNavigationEnabled
import uikit.extensions.runAnimation
import uikit.extensions.withAlpha
import uikit.glass.GlassConfig
import uikit.navigation.Navigation.Companion.navigation
import java.math.BigInteger
import uikit.extensions.swallowClicks
import com.tonapps.migration.MigrationFragment
import com.tonapps.tonkeeper.extensions.failureToast
import kotlinx.coroutines.flow.MutableStateFlow
import uikit.widget.ToastFeedback

@Suppress("LargeClass")
class RootActivity : BaseWalletActivity(),
    DappConfirmFragment.Delegate,
    DepositFragment.Delegate,
    WithdrawFragment.Delegate,
    RNLegacyDelegate,
    SignProof.Delegate,
    SignTransaction.Delegate,
    AssetsFragment.Delegate,
    NavigationDelegate {

    private var cachedRootViewModel: RootViewModel? = null

    private var pushSettingsOpened = false

    private var batteryOnboardingJob: Job? = null

    override val viewModel: RootViewModel
        get() = createOrGetViewModel()

    override val isLightNavigationBars: Boolean
        get() = settingsRepository.isLightTheme

    private val legacyRN: RNLegacy by inject()
    private val appShortcutRepository by inject<AppShortcutRepository>()
    private val settingsRepository by inject<SettingsRepository>()
    private val confirmSheetQueue by inject<ConfirmSheetQueue>()
    private val tokenRepository by inject<TokenRepository>()
    private val ledgerProgressPresenter by inject<LedgerProgressPresenter>()
    private val accountRepository by inject<AccountRepository>()
    private val batteryRepository by inject<BatteryRepository>()
    private val unifiedAccountRepository by inject<UnifiedAccountRepository>()
    private val swapStatusTracker by inject<SwapStatusTracker>()
    private val legacySwapAccounts by inject<LegacySwapAccounts>()
    private val environment by inject<Environment>()
    private val emulationUseCase by inject<EmulationUseCase>()
    private val passcodeManager by inject<PasscodeManager>()
    private val attributionManager by inject<AttributionManager>()
    private val earnUrlResolver by inject<EarnUrlResolver>()
    private val dappScreenFactory by inject<DAppScreenFactory>()
    private val perpsDepositArrivals by inject<PerpsDepositArrivals>()
    private val moonPayFrames by inject<MoonPayFrames>()
    private val lockScreenShown = MutableStateFlow(false)
    private val googlePlayUpdateHelper by lazy(LazyThreadSafetyMode.NONE) {
        GooglePlayUpdateHelper(
            activity = this,
            viewModel = viewModel,
            environment = environment,
        )
    }
    private val inAppReviewHelper by lazy(LazyThreadSafetyMode.NONE) {
        InAppReviewHelper(
            activity = this,
            environment = environment,
        )
    }
    private lateinit var uiHandler: Handler
    private var isLaunchLockShown = false
    private var isIntentConsumed = false

    // Only a sheet that is going away frees the queue: one destroyed together with the activity, by a
    // configuration change or by the system, comes back restored.
    private val confirmSheetCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
            if (f is ConfirmSheetQueue.Sheet && (f.isRemoving || isFinishing)) {
                f.queuedRequestId?.let(confirmSheetQueue::onSheetDestroyed)
            }
        }
    }

    private val screenBreadcrumbCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
            if (f !is BaseFragment) {
                return
            }
            val screenName = f.fragmentName
            Firebase.crashlytics.log("screen: $screenName")
            Firebase.crashlytics.setCustomKey("lastScreen", screenName)
        }
    }

    private lateinit var lockView: View
    private lateinit var lockPasscodeView: PasscodeView
    private lateinit var newNetworksView: ComposeView
    private lateinit var migrationLoaderContainer: View
    private lateinit var migrationLoaderIcon: View

    private fun tryToApplyStaticFeatureFlags(source: Intent = intent) {
        source.getStringExtra(EXTRA_FEATURE_FLAGS)?.let(FeatureManager::applyStaticOverrides)
        source.getStringExtra(EXTRA_BOOT_FLAGS)?.let { BootConfigOverrides.apply(this, it) }
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable && source.getStringExtra(EXTRA_PERPS_TERMS_ACCEPTED).toBoolean()) {
            settingsRepository.perpsTermsAccepted = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        tryToApplyStaticFeatureFlags()
        GlassConfig.latch(WalletFeature.LiquidGlass.value)
        val theme = settingsRepository.theme
        setTheme(theme)
        if (GlassConfig.isEnabled) {
            getTheme().applyStyle(uikit.R.style.ThemeOverlay_App_Glass, true)
        }
        supportFragmentManager.fragmentFactory = WalletFragmentFactory()
        supportFragmentManager.registerFragmentLifecycleCallbacks(screenBreadcrumbCallbacks, true)
        supportFragmentManager.registerFragmentLifecycleCallbacks(confirmSheetCallbacks, false)
        attributionManager.withPrivateLinkHidden(this) {
            super.onCreate(savedInstanceState)
            attributionManager.onLauncherActivityCreated(this)
        }
        ScreenRenderTracker.shared.launchMark(ScreenRenderLaunchStep.SceneConnect)

        if (theme.isSystem) {
            setAppearanceLight(!isDarkMode)
        } else {
            setAppearanceLight(theme.light)
        }
        legacyRN.setActivity(this)
        uiHandler = Handler(mainLooper)
        ledgerProgressPresenter.attach(this)

        isIntentConsumed = savedInstanceState?.getBoolean(STATE_INTENT_CONSUMED) == true
        val isLaunchedFromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        val isRedelivery = savedInstanceState != null || isLaunchedFromHistory
        val isRelaunchedFromRecents = isLaunchedFromHistory && savedInstanceState == null
        val isIntentHandled = intent.getBooleanExtra(EXTRA_INTENT_HANDLED, false)
        if (isRedelivery && intent.data?.let(attributionManager::isAttributionLink) == true) {
            intent.data = null
        } else if (!isIntentConsumed && !isRelaunchedFromRecents && !isIntentHandled) {
            if (savedInstanceState != null) {
                PUSH_EXTRA_KEYS.forEach(intent::removeExtra)
            }
            handleIntent(
                intent = intent,
                isColdStart = true,
                isRedelivery = isRedelivery,
            )
            intent.putExtra(EXTRA_INTENT_HANDLED, true)
        }
        lifecycleScope.launch {
            passcodeManager.lockscreenHiddenFlow.first { it }
            isIntentConsumed = true
        }

        lockView = findViewById(R.id.lock)
        lockView.swallowClicks()
        newNetworksView = findViewById(R.id.new_networks)
        newNetworksView.swallowClicks()
        newNetworksView.setContent {
            MoonTheme(colorScheme = environment.theme) {
                NewNetworksLock(passcodeManager)
            }
        }
        findViewById<ComposeView>(R.id.swap_status).setContent {
            MoonTheme(colorScheme = environment.theme) {
                SwapStatusToastHost(swapStatusTracker)
            }
        }
        findViewById<ComposeView>(R.id.moonpay_frames).setContent {
            MoonTheme(colorScheme = environment.theme) {
                MoonPayFramesHost(moonPayFrames, concealed = lockScreenShown)
            }
        }
        lockPasscodeView = findViewById(R.id.lock_passcode)
        lockPasscodeView.doOnCheck = {
            passcodeManager.lockscreenCheck(this, it)
        }

        migrationLoaderContainer = findViewById(R.id.migration_loader_container)
        migrationLoaderContainer.swallowClicks()
        migrationLoaderIcon = findViewById(R.id.migration_loader_icon)

        ViewCompat.setOnApplyWindowInsetsListener(lockView) { _, insets ->
            val statusInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            lockView.updatePadding(top = statusInsets.top, bottom = navInsets.bottom)
            insets
        }

        collectFlow(viewModel.hasWalletFlow) { init(it) }
        collectFlow(viewModel.eventFlow) { event(it) }
        collectFlow(viewModel.inAppReviewRequestFlow) { inAppReviewHelper.requestReview() }
        collectFlow(viewModel.lockscreenFlow, ::pinState)
        collectFlow(perpsDepositArrivals.arrivals.flowWithLifecycle(lifecycle)) {
            confirmedToast(getString(Localization.perps_deposit_completed), ToastFeedback.Success)
        }
        // Gated on STARTED so a URL submitted by ShortcutDeeplinkActivity while this activity is
        // in the background stays buffered in the channel until the task is actually in front,
        // matching the old handleIntent timing.
        collectFlow(confirmSheetQueue.current.filterNotNull().flowWithLifecycle(lifecycle), ::showConfirmSheet)
        collectFlow(appShortcutRepository.pendingDeeplinkFlow.flowWithLifecycle(lifecycle)) {
            viewModel.openShortcutDApp(it)
        }
        collectFlow(attributionManager.deepLinkFlow.flowWithLifecycle(lifecycle)) { attributionLink ->
            val uri = DeepLink.fixBadUri(attributionLink.appLink)
            val from = if (attributionLink.isDeferred) {
                DeeplinkOpenFrom.DeferredLink
            } else {
                DeeplinkOpenFrom.UniversalLink
            }
            DeeplinkOpenAnalytics.report(uri, attributionLink.isColdStart, from)
            if (DeepLinkRoute.resolve(uri) !is DeepLinkRoute.Unknown) {
                processDeepLink(uri, false, null, fromExternal = true, waitForWallet = true)
            }
        }

        App.applyConfiguration(resources.configuration)
        remoteConfig?.fetchAndActivate()
    }

    override fun openDapp(walletId: String, app: AppEntity, dAppUrl: Uri, source: String, utm: UtmParameters) {
        viewModel.openDappScreen(walletId, app, dAppUrl, source, utm)
    }

    override fun onOpenToken(token: TokenEntity, eventsOnly: Boolean) {
        lifecycleScope.launch {
            val wallet = accountRepository.getSelectedWallet() ?: return@launch
            navigation?.add(
                TokenScreen.newInstance(
                    wallet,
                    token.address,
                    token.name,
                    token.symbol,
                    eventsOnly = eventsOnly,
                )
            )
        }
    }

    override fun onOpenTxDetails(tx: TxEvent, actionIndex: Int) {
        lifecycleScope.launch {
            val wallet = accountRepository.getSelectedWallet() ?: return@launch
            navigation?.add(TxDetailsScreen.newInstance(wallet, tx, actionIndex))
        }
    }

    override fun onOpenUrl(url: String) {
        BrowserHelper.open(this, url)
    }

    override fun onOpenSwap(fromAssetId: String?, toAssetId: String?, fromToken: String, toToken: String) {
        lifecycleScope.launch {
            val wallet = unifiedAccountRepository.getSelectedWallet() ?: return@launch
            if (wallet.type == WalletType.Multichain) {
                navigation?.add(
                    SwapFragment.create(
                        initial = SwapRoutes.Swap(sellAssetId = fromAssetId, buyAssetId = toAssetId),
                    ),
                )
                return@launch
            }

            val assetId = fromAssetId ?: toAssetId
            if (assetId != null && !assetId.isTonOrTronAssetId()) {
                toast(Localization.confirmation_error_unsupported_asset)
                return@launch
            }

            val isTronSwap = fromToken == TokenEntity.TRX.address ||
                    fromToken == TokenEntity.TRON_USDT.address ||
                    toToken == TokenEntity.TRX.address ||
                    toToken == TokenEntity.TRON_USDT.address
            if (isTronSwap) {
                openLegacyTronSwap(wallet.id, fromToken, toToken)
                return@launch
            }

            navigation?.add(OmnistonScreen.newInstance(wallet, fromToken, toToken))
        }
    }

    // GRAM ↔ USDT TRC20 runs through the multichain swap for a legacy wallet it can sign for. Anything
    // else on TRON (TRX, wallets without keys) has no swap, and its screens offer none, as on iOS.
    private suspend fun openLegacyTronSwap(walletId: String, fromToken: String, toToken: String) {
        val usdt = TokenEntity.TRON_USDT.address
        val gram = TokenEntity.TON.address
        val usdtPair = (fromToken == usdt && toToken == gram) || (fromToken == gram && toToken == usdt)
        // Reachable only when the screen offered a swap before TRON was switched off.
        if (!usdtPair || legacySwapAccounts.find(walletId) == null) {
            toast(Localization.confirmation_error_unsupported_asset)
            return
        }

        val sellAssetId = when (fromToken) {
            usdt -> LegacySwapAssets.USDT_TRC20_ASSET_ID
            else -> LegacySwapAssets.TON_ASSET_ID
        }
        navigation?.add(SwapFragment.create(initial = SwapRoutes.Swap(sellAssetId = sellAssetId)))
    }

    override fun onOpenSend(assetId: String, tokenAddress: String) {
        lifecycleScope.launch {
            // TODO move to ViewModel
            val wallet = unifiedAccountRepository.getSelectedWallet() ?: return@launch
            if (wallet.type == WalletType.Multichain) {
                navigation?.add(
                    WithdrawMulticoinFragment.create(
                        walletId = wallet.id,
                        initial = WithdrawMulticoinRoutes.Send(assetId = assetId),
                        analyticsFrom = Events.WithdrawFlow.WithdrawFlowFrom.JettonScreen,
                    ),
                )
            } else {
                navigation?.add(
                    SendScreen.newInstance(
                        wallet = wallet,
                        tokenAddress = tokenAddress,
                        type = SendScreen.Companion.Type.Default,
                        from = Events.SendNative.SendNativeFrom.JettonScreen,
                    ),
                )
            }
        }
    }

    override fun onOpenReceive(assetId: String, token: TokenEntity) {
        lifecycleScope.launch {
            val wallet = unifiedAccountRepository.getSelectedWallet() ?: return@launch
            if (wallet.type == WalletType.Multichain) {
                navigation?.add(
                    ReceiveQrFragment.create(
                        walletId = wallet.id,
                        assetId = assetId,
                        analyticsFrom = Events.DepositFlow.DepositFlowFrom.JettonScreen,
                    )
                )
            } else if (assetId.isTonOrTronAssetId()) {
                navigation?.add(
                    QrAssetFragment.newInstance(
                        token = token,
                        walletId = wallet.id,
                        analyticsFrom = Events.DepositFlow.DepositFlowFrom.JettonScreen,
                    ),
                )
            } else {
                toast(Localization.confirmation_error_unsupported_asset)
            }
        }
    }

    override fun onOpenStaking() {
        lifecycleScope.launch {
            val wallet = unifiedAccountRepository.getSelectedWallet() ?: return@launch
            navigation?.add(
                StakingScreen.newInstance(wallet = wallet, from = "asset_details"),
            )
        }
    }

    override fun onOpenUnverifiedInfo() {
        navigation?.add(TokenUnverifiedScreen.newInstance())
    }

    override fun onSellToCard(assetId: String) {
        openCashRamp(assetId, RampType.RampOff) {
            WithdrawFragment.create(
                initial = WithdrawRoutes.WithdrawMethod(
                    sectionFilter = PaymentMethodSectionFilter.CashOnly,
                    assetId = assetId,
                ),
                analyticsFrom = Events.WithdrawFlow.WithdrawFlowFrom.JettonScreen,
            )
        }
    }

    override fun onBuyWithCard(assetId: String) {
        openCashRamp(assetId, RampType.RampOn) {
            DepositFragment.create(
                initial = DepositRoutes.Buy(
                    sectionFilter = PaymentMethodSectionFilter.CashOnly,
                    assetId = assetId,
                ),
                analyticsFrom = Events.DepositFlow.DepositFlowFrom.JettonScreen,
            )
        }
    }

    private fun openCashRamp(assetId: String, rampType: RampType, legacyScreen: () -> BaseFragment) {
        lifecycleScope.launch {
            val wallet = unifiedAccountRepository.getSelectedWallet() ?: return@launch
            when {
                wallet.type == WalletType.Multichain -> navigation?.add(
                    DepositMulticoinFragment.create(
                        walletId = wallet.id,
                        rampType = rampType,
                        initial = DepositMulticoinRoutes.Buy(assetId),
                        analyticsFrom = Events.DepositFlow.DepositFlowFrom.JettonScreen,
                    )
                )
                wallet.isWatchOnly -> navigation?.add(WatchInfoScreen.newInstance(wallet))
                assetId.isTonOrTronAssetId() -> navigation?.add(legacyScreen())
                else -> toast(Localization.confirmation_error_unsupported_asset)
            }
        }
    }

    override fun onOpenDeposit(from: Events.DepositFlow.DepositFlowFrom, walletId: String?) {
        lifecycleScope.launch {
            val wallet = when (walletId) {
                null -> unifiedAccountRepository.getSelectedWallet()
                else -> unifiedAccountRepository.getTonWalletById(walletId)
            } ?: return@launch
            if (wallet.type == WalletType.Multichain) {
                navigation?.add(DepositMulticoinFragment.create(walletId = wallet.id, analyticsFrom = from))
            } else {
                navigation?.add(DepositFragment.create(walletId = wallet.id, analyticsFrom = from))
            }
        }
    }

    override fun onOpenReceivingAddress() {
        val walletId = unifiedAccountRepository.getSelectedWalletId() ?: return
        navigation?.add(
            DepositMulticoinFragment.create(
                walletId = walletId,
                initial = DepositMulticoinRoutes.Receive,
                analyticsFrom = Events.DepositFlow.DepositFlowFrom.WalletScreen,
            )
        )
    }

    override fun onOpenWithdraw(from: Events.WithdrawFlow.WithdrawFlowFrom) {
        lifecycleScope.launch {
            val wallet = unifiedAccountRepository.getSelectedWallet() ?: return@launch
            if (wallet.type == WalletType.Multichain) {
                navigation?.add(WithdrawMulticoinFragment.create(walletId = wallet.id, analyticsFrom = from))
            } else {
                navigation?.add(WithdrawFragment.create(analyticsFrom = from))
            }
        }
    }

    override fun onOpenAssetDetails(assetId: String, previewName: String, previewImageUrl: String, from: AssetScreenFrom) {
        navigation?.add(AssetsFragment.newInstance(assetId, from, previewName, previewImageUrl))
    }

    override fun onOpenPerpMarket(marketIndex: Int, symbol: String, entryPoint: PerpsEntryPoint) {
        viewModel.openPerps(PerpsFragment.newInstance(marketIndex, symbol, entryPoint))
    }

    override fun onOpenPerps(entryPoint: PerpsEntryPoint) {
        viewModel.openPerps(PerpsFragment.newInstance(entryPoint))
    }

    override fun onOpenPerpsBasics() {
        viewModel.openPerpsBasics()
    }

    override fun onOpenEarn(from: EarnEntryViewFrom) {
        openEarnDApp(earnUrlResolver.base, from)
    }

    override fun onOpenEarnVault(network: String, vaultId: String, from: EarnEntryViewFrom) {
        openEarnDApp(earnUrlResolver.vaultUrl(network, vaultId), from)
    }

    private fun openEarnDApp(url: Uri, from: EarnEntryViewFrom) {
        lifecycleScope.launch {
            val wallet = unifiedAccountRepository.getSelectedWallet() ?: return@launch
            navigation?.add(
                dappScreenFactory.create(
                    wallet = wallet,
                    title = getString(Localization.earn_title),
                    url = url,
                    iconUrl = "",
                    source = from.key,
                )
            )
        }
    }

    override fun onOpenAccountsManage() {
        navigation?.add(AccountsManageFragment())
    }

    override fun onOpenPortfolioSearch(
        initialSort: PortfolioSearchSort?,
        initialNetwork: Network.Type?,
        initialAssetType: PortfolioSearchAssetType?,
    ) {
        navigation?.add(SearchFragment.newInstance(initialSort, initialNetwork, initialAssetType))
    }

    override fun onOpenWalletsList(perpsWalletsOnly: Boolean) {
        navigation?.add(WalletsListFragment.newInstance(perpsWalletsOnly))
    }

    override fun onOpenWalletLabelEdit(walletId: String) {
        navigation?.add(EditNameScreen.newInstance(walletId))
    }

    override fun onOpenAddWallet() {
        navigation?.add(InitScreen.newInstance(InitArgs.Type.AddWallet))
    }

    override fun onOpenSwap() {
        navigation?.add(SwapFragment.create())
    }

    override fun onOpenConfirm(request: ConfirmRequest) {
        navigation?.add(WcConfirmFragment.newInstance(request))
    }

    override fun onOpenStake() {
        lifecycleScope.launch {
            val wallet = unifiedAccountRepository.getSelectedWallet() ?: return@launch
            navigation?.add(
                StakingScreen.newInstance(wallet = wallet, from = "wallet"),
            )
        }
    }

    override fun onOpenStakeViewer(poolAddress: String, poolName: String) {
        lifecycleScope.launch {
            val wallet = unifiedAccountRepository.getSelectedWallet() ?: return@launch
            navigation?.add(
                StakeViewerScreen.newInstance(
                    wallet = wallet,
                    address = poolAddress,
                    name = poolName,
                )
            )
        }
    }

    override fun onOpenStakeWithdraw(poolAddress: String) {
        lifecycleScope.launch {
            val wallet = unifiedAccountRepository.getSelectedWallet() ?: return@launch
            navigation?.add(StakeWithdrawScreen.newInstance(wallet, poolAddress))
        }
    }

    override fun onOpenSettings() {
        navigation?.add(SettingsScreen.newInstance("portfolio"))
    }

    override fun onOpenBackup() {
        navigation?.add(BackupScreen.newInstance(WalletFlowSource.WalletSetupSection))
    }

    override fun onOpenMigration() {
        navigation?.add(MigrationFragment.newInstance(MigrationFrom.Setup))
    }

    override fun onOpenLedgerDevice(walletId: String, from: LedgerDeviceViewFrom) {
        navigation?.add(LedgerWalletFragment.newInstance(walletId, from))
    }

    override fun onOpenLedgerImport(payload: String, deviceName: String, migrationFrom: LedgerMigrationStartFrom?) {
        val args = InitArgs(
            type = InitArgs.Type.LedgerMultichain,
            name = deviceName,
            publicKey = null,
            ledgerConnectData = null,
            accounts = null,
            keystone = null,
            importStartedTracked = true,
            ledgerImport = payload,
            ledgerMigrationFrom = migrationFrom?.key,
        )
        add(InitScreen.newInstance(args))
    }

    override fun onEnableBiometry() {
        lifecycleScope.launch {
            try {
                val passcode = passcodeManager.requestValidPasscode(this@RootActivity, PasscodeReason.ENABLE_BIOMETRY)
                legacyRN.setupBiometry(passcode)
                settingsRepository.biometric = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logError(e)
                failureToast(Localization.unknown_error)
            }
        }
    }

    override fun onEnablePush() {
        lifecycleScope.launch {
            val canAsk = ActivityCompat.shouldShowRequestPermissionRationale(
                this@RootActivity,
                Manifest.permission.POST_NOTIFICATIONS,
            ) || !settingsRepository.pushPermissionRequested
            when {
                NotificationManagerCompat.from(this@RootActivity).areNotificationsEnabled() -> {
                    val wallet = unifiedAccountRepository.getSelectedWallet() ?: return@launch
                    PushToggleWorker.run(this@RootActivity, wallet, PushManager.State.Enable)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && canAsk -> {
                    settingsRepository.pushPermissionRequested = true
                    ActivityCompat.requestPermissions(
                        this@RootActivity,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        PUSH_PERMISSION_REQUEST_CODE,
                    )
                }
                else -> {
                    pushSettingsOpened = true
                    openAppSettings()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PUSH_PERMISSION_REQUEST_CODE) {
            return
        }
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            onEnablePush()
        }
    }

    // The multichain history knows only multichain wallets; a legacy one, back from a swap, has its own.
    override fun onOpenHistory(trigger: ScreenRenderTrigger) {
        ScreenRenderTracker.shared.begin(ScreenRenderId.History, trigger, isFirstOpen = true)
        lifecycleScope.launch {
            val wallet = unifiedAccountRepository.getSelectedWallet() ?: return@launch
            if (wallet.type == WalletType.Multichain) {
                navigation?.add(EventsFragment())
            } else {
                navigation?.add(TxEventsScreen.newInstance(wallet))
            }
        }
    }

    override fun onOpenPerpsHistory(awaitsFundingActivity: Boolean) {
        navigation?.add(EventsFragment.perpsHistory(awaitsFundingActivity))
    }

    override fun onOpenAssetHistory(assetId: String) {
        navigation?.add(EventsFragment.newInstance(assetId))
    }

    override fun onOpenScanner() {
        navigation?.add(ScannerFragment.newInstance())
    }

    override fun onOpenCollectibles() {
        navigation?.add(CollectiblesScreen.newInstance(from = "wallet"))
    }

    override fun onOpenNft(nft: NftEntity) {
        navigation?.add(NftScreen.newInstance(nft))
    }

    override fun onOpenNft(address: String) {
        navigation?.add(NftScreen.newInstance(address))
    }

    override fun onOpenLink(url: String) {
        openURL(url)
    }

    override fun onOpenRaffle(walletId: String, raffleId: String) {
        navigation?.add(
            RaffleFragment.newInstance(
                walletId = walletId,
                raffleId = raffleId,
                source = MysteryRaffleSource.WalletMain.key,
            )
        )
    }

    override fun onOpenBatteryFromWallet() {
        batteryOnboardingJob?.cancel()
        val top = supportFragmentManager.fragments.lastOrNull()
        val tab = (top as? MainScreen)?.visibleTabFragment
        batteryOnboardingJob = lifecycleScope.launch {
            val wallet = unifiedAccountRepository.getSelectedWallet() ?: return@launch
            if (!wallet.supportsBattery) {
                return@launch
            }
            val stories = viewModel.loadBatteryOnboarding(wallet)
            val wanted = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                supportFragmentManager.fragments.lastOrNull() === top &&
                (top as? MainScreen)?.visibleTabFragment === tab &&
                unifiedAccountRepository.getSelectedWallet()?.id == wallet.id
            if (!wanted) {
                return@launch
            }
            if (stories != null) {
                navigation?.add(RemoteStoriesScreen.newInstance(stories, BATTERY_ONBOARDING_FROM))
            } else {
                navigation?.add(BatteryScreen.newInstance(wallet, from = BatteryNativeFrom.Wallet))
            }
        }
    }

    override fun onOpenBattery(walletId: String?, from: BatteryNativeFrom) {
        lifecycleScope.launch {
            val wallet = walletId?.let { unifiedAccountRepository.getTonWalletById(it) }
                ?: unifiedAccountRepository.getSelectedWallet()
                ?: return@launch
            if (!wallet.supportsBattery) {
                return@launch
            }
            navigation?.add(BatteryScreen.newInstance(wallet, from = from))
        }
    }

    override fun onOpenTopUp(request: ConfirmRequest, assetId: String) {
        lifecycleScope.launch {
            navigation?.add(confirmTopUpScreen(request, assetId, legacySwapAccounts))
        }
    }

    override fun onProcessDeeplink(value: String, fromQR: Boolean): Boolean {
        val uri = DeepLinkBuilder.preprocess(value) ?: if (fromQR) {
            viewModel.qrScannerExtendsUri(value)
        } else {
            null
        }
        if (uri == null) {
            toast(Localization.invalid_link)
            return false
        }
        return processDeepLink(uri, true, null, fromQR = fromQR)
    }

    override fun navigateTaskBack() {
//        Toaster.show(RString.WalletConnectRedirection)
        Async.globalScope(Dispatchers.Main.immediate).launch {
            moveTaskToBack(false)
        }
    }

    override fun onOpenProvider(url: String) {
        BrowserHelper.open(this, url)
    }

    override fun onShowError(message: String) {
        navigation?.toast(message)
    }

    override fun onBuyTon(from: Events.DepositFlow.DepositFlowFrom) {
        val asset = RampAsset.Currency(WalletCurrency.TON)
        navigation?.add(DepositFragment.create(DepositRoutes.Buy(asset), analyticsFrom = from))
    }

    override fun onGetTrx(from: Events.DepositFlow.DepositFlowFrom) {
        navigation?.add(
            QrAssetFragment.newInstance(
                com.tonapps.blockchain.model.legacy.TokenEntity.TRX,
                analyticsFrom = from,
            )
        )
    }

    override fun onRechargeBattery() {
        lifecycleScope.launch {
            val wallet = unifiedAccountRepository.getSelectedWallet() ?: return@launch
            if (!wallet.supportsBattery) {
                return@launch
            }
            navigation?.add(BatteryScreen.newInstance(wallet, from = BatteryNativeFrom.InsufficientFunds))
        }
    }

    override fun onOpenAddressBook(onResult: (String) -> Unit) {
        lifecycleScope.launch {
            val wallet = accountRepository.getSelectedWallet() ?: return@launch
            val requestKey = "contacts_address_book_${System.currentTimeMillis()}"
            setFragmentResultListener(requestKey) { bundle ->
                val contact = bundle.getParcelableCompat<SendContact>("contact")
                if (contact != null) {
                    onResult(contact.address)
                }
            }
            add(SendContactsScreen.newInstance(wallet, requestKey))
        }
    }

    override suspend fun onRequestPasscode(reason: PasscodeReason): String? {
        return PasscodeDialog.request(this, reason)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        val currentConfig = newBase.resources.configuration
        var newConfig: Configuration? = null
        if (DevSettings.ignoreSystemFontSize) {
            newConfig = Configuration(currentConfig)
            if (newConfig.fontScale >= 1.0f) {
                newConfig.fontScale = 1f
            }
        } else if (currentConfig.fontScale >= 1.2f) {
            newConfig = Configuration(currentConfig)
            newConfig.fontScale = 1.2f
        }
        newConfig?.let {
            applyOverrideConfiguration(it)
        }
    }

    override fun onResume() {
        attributionManager.withPrivateLinkHidden(this) { super.onResume() }
        viewModel.connectTonConnectBridge()
        if (pushSettingsOpened) {
            pushSettingsOpened = false
            if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                onEnablePush()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.disconnectTonConnectBridge()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            lifecycleScope.launch {
                passcodeManager.lockscreenRetryWithoutPasscode()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        googlePlayUpdateHelper.onStart()
        passcodeManager.restoreSessionOnForeground()
    }

    override fun onStop() {
        googlePlayUpdateHelper.onStop()
        super.onStop()
    }

    private suspend fun pinState(state: LockScreen.State) {
        // The lock is an overlay, so stop the covered fragments from reopening their keyboard over the numpad.
        lockKeyboard(state != LockScreen.State.None)
        reportLaunchLock(state)
        lockPasscodeView.setLoading(state == LockScreen.State.Migrating)
        if (state == LockScreen.State.NewChains) {
            // Part of the lock screen: shown over it first, so the wallet never shows in between.
            lockScreenShown.value = true
            newNetworksView.visibility = View.VISIBLE
            lockView.visibility = View.GONE
            lockPasscodeView.clear()
        } else if (state == LockScreen.State.None) {
            lockScreenShown.value = false
            newNetworksView.visibility = View.GONE
            if (lockView.visibility == View.VISIBLE) {
                lockPasscodeView.setSuccess()
            }
            lockView.visibility = View.GONE
            lockPasscodeView.clear()
            passcodeManager.lockscreenHidden()
        } else if (state is LockScreen.State.Error) {
            lockPasscodeView.setError()
            lockScreenShown.value = true
            lockView.visibility = View.VISIBLE
        } else if (state == LockScreen.State.Migrating) {
            lockScreenShown.value = true
            lockView.visibility = View.VISIBLE
        } else {
            if (passcodeManager.lockscreenRetryWithoutPasscode()) {
                return
            }

            lockScreenShown.value = true
            lockView.visibility = View.VISIBLE
            val reason = passcodeManager.lockscreenReason()
            lockPasscodeView.setReason(reason)
            if (passcodeManager.isBiometricRequest(this)) {
                if (passcodeManager.confirmationByBiometric(this, reason)) {
                    passcodeManager.lockscreenBiometric()
                } else {
                    toast(Localization.authorization_required)
                }
            }
        }
    }

    private fun lockKeyboard(locked: Boolean) {
        val focusability = if (locked) {
            ViewGroup.FOCUS_BLOCK_DESCENDANTS
        } else {
            ViewGroup.FOCUS_BEFORE_DESCENDANTS
        }
        findViewById<ViewGroup?>(uikit.R.id.root_container)?.descendantFocusability = focusability
        findViewById<ViewGroup?>(uikit.R.id.sheet_container)?.descendantFocusability = focusability
        if (locked) {
            currentFocus?.clearFocus()
            WindowInsetsControllerCompat(window, window.decorView)
                .hide(WindowInsetsCompat.Type.ime())
        }
    }

    private fun createOrGetViewModel(): RootViewModel {
        return cachedRootViewModel ?: createViewModel()
    }

    private fun createViewModel(): RootViewModel {
        return viewModel<RootViewModel>().value.also {
            cachedRootViewModel = it
        }
    }

    override fun migrationLoader(show: Boolean) {
        super.migrationLoader(show)
        if (show) {
            migrationLoaderContainer.visibility = View.VISIBLE
            migrationLoaderContainer.setBackgroundColor(backgroundPageColor.withAlpha(.64f))
            migrationLoaderIcon.runAnimation(R.anim.gear_loading)
        } else {
            migrationLoaderContainer.visibility = View.GONE
            migrationLoaderIcon.clearAnimation()
        }
    }

    override fun isNeedRemoveModals(fragment: BaseFragment): Boolean {
        if (fragment is QRCameraScreen || fragment is LedgerSignScreen) {
            return false
        }
        return super.isNeedRemoveModals(fragment)
    }

    override fun onDestroy() {
        super.onDestroy()
        cachedRootViewModel = null
        viewModelStore.clear()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        App.applyConfiguration(newConfig)
        if (settingsRepository.theme.isSystem) {
            ActivityCompat.recreate(this)
        }
    }

    private fun setTheme(theme: Theme) {
        if (!theme.isSystem) {
            setTheme(theme.resId)
        } else if (isDarkMode) {
            setTheme(uikit.R.style.Theme_App_Dark)
        } else {
            setTheme(uikit.R.style.Theme_App_Light)
        }
    }

    fun setAppearanceLight(light: Boolean) {
        val isLightTheme = settingsRepository.isLightTheme
        var lightNavigationBars = light
        if (isLightTheme && !gestureNavigationEnabled) {
            lightNavigationBars = true
        }

        with(windowInsetsController) {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = lightNavigationBars
        }
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(R.layout.activity_root)
    }

    fun event(event: RootEvent) {
        when (event) {
            RootEvent.CheckGooglePlayUpdate -> googlePlayUpdateHelper.checkForUpdates()
            is RootEvent.Singer -> add(
                InitScreen.newInstance(
                    if (event.qr) {
                        InitArgs.Type.SignerQR
                    } else {
                        InitArgs.Type.Signer
                    },
                    event.publicKey,
                    event.name,
                    importStartedTracked = true,
                )
            )

            is RootEvent.Ledger -> add(
                InitScreen.newInstance(
                    type = InitArgs.Type.Ledger,
                    ledgerConnectData = event.connectData,
                    accounts = event.accounts,
                    importStartedTracked = true,
                )
            )

            is RootEvent.Transfer -> {
                lifecycleScope.launch {
                    openSend(
                        targetAddress = event.address,
                        source = event.source,
                        utm = event.utm,
                        tokenAddress = event.jettonAddress,
                        amount = event.amount,
                        text = event.text,
                        wallet = event.wallet,
                        bin = event.bin,
                        initStateBase64 = event.initStateBase64,
                        validUnit = event.validUnit,
                    )
                }
            }

            is RootEvent.CloseCurrentTonConnect -> closeCurrentTonConnect {}
            is RootEvent.OpenDAppByShortcut -> openDAppByShortcut(event.wallet, event.url)
            else -> {}
        }
    }

    // The current sheet may already be there, restored after a recreation.
    private fun showConfirmSheet(requestId: String) {
        val shown = supportFragmentManager.fragments.any {
            it is ConfirmSheetQueue.Sheet && it.queuedRequestId == requestId
        }
        if (!shown) {
            add(WcConfirmFragment.newInstance(requestId))
        }
    }

    private fun openDAppByShortcut(wallet: WalletEntity, uri: Uri) {
        removeByClass({
            add(
                dappScreenFactory.create(
                    wallet = wallet,
                    title = uri.host ?: "unknown",
                    url = uri,
                    iconUrl = "",
                    source = DappBrowserAnalytics.SHORTCUT_DAPP_SOURCE,
                )
            )
        }, DAppScreen::class.java)
    }

    private fun closeCurrentTonConnect(runnable: Runnable) {
        removeByClass(runnable, SendTransactionScreen::class.java, TonConnectScreen::class.java)
    }

    private fun replaceCurrentTonConnect(action: () -> Unit) {
        var callbackExecuted = false
        closeCurrentTonConnect {
            callbackExecuted = true
            action()
        }
        if (!callbackExecuted) {
            action()
        }
    }

    private suspend fun getJettonForwardAmount(
        wallet: WalletEntity,
        message: RawMessageEntity,
        jettonMasterAddress: String,
        validUntil: Long,
    ): com.tonapps.icu.Coins {
        try {
            val transfer =
                message.getDefaultWalletTransfer(TonSendMode.PAY_GAS_SEPARATELY.value + TonSendMode.IGNORE_ERRORS.value)

            val emulated = emulationUseCase(
                message = accountRepository.messageBody(
                    wallet,
                    validUntil,
                    listOf(transfer)
                ),
                params = true
            )

            val transferCost = batteryRepository.getConfig(wallet.network).transferCost(jettonMasterAddress)
            return if (emulated.extra.isRefund) {
                transferCost
            } else {
                emulated.extra.value + transferCost
            }
        } catch (_: Throwable) {
            return TransferEntity.POINT_ONE_TON
        }
    }

    @SuppressLint("UseKtx")
    private suspend fun openSign(
        wallet: WalletEntity,
        source: DeepLink.Source,
        utm: UtmParameters,
        targetAddress: String,
        tokenAddress: String?,
        amountNano: BigInteger,
        bin: Cell?,
        initStateBase64: String?,
        comment: String? = null,
        validUnit: Long
    ) {
        val message = if (tokenAddress != null) {
            val tokens =
                tokenRepository.get(settingsRepository.currency, wallet.accountId, wallet.network)
                    ?: emptyList()
            val token = tokens.find {
                it.address.equalsAddress(tokenAddress)
            } ?: throw IllegalStateException("Token not found")
            val message = RawMessageEntity(
                addressValue = token.balance.walletAddress,
                amount = TransferEntity.BASE_FORWARD_AMOUNT.toBigInteger(),
                stateInitValue = initStateBase64,
                payloadValue = TonTransferHelper.jetton(
                    toAddress = AddrStd(targetAddress),
                    responseAddress = wallet.contract.address,
                    queryId = TransferEntity.newWalletQueryId(),
                    forwardPayload = bin ?: asCellRef(comment),
                    coins = Coins.ZERO // TODO TONSDK
                ).base64()
            )
            message.copy(amount = getJettonForwardAmount(wallet, message, token.address, validUnit).toBigInteger())
        } else {
            RawMessageEntity(
                addressValue = targetAddress,
                amount = amountNano,
                stateInitValue = initStateBase64,
                payloadValue = bin?.base64() ?: asCellRef(comment)?.base64()
            )
        }

        val request = SignRequestEntity.Builder()
            .setFrom(wallet.contract.address)
            .setValidUntil(validUnit)
            .addMessage(message)
            .setTestnet(wallet.testnet)
            .build("tonkeeper://signRaw/".toUri())

        val screen = SendTransactionScreen.newInstance(
            wallet, request, PasscodeReason.CONFIRM_TRANSFER,
            sendNativeFrom = source.analytic,
            utm = utm,
        )
        add(screen)
    }

    private fun openDirectSend(builder: SendScreen.Companion.Builder) {
        removeByClass({
            add(builder.build())
        }, SendScreen::class.java)
    }

    private suspend fun openSend(
        wallet: WalletEntity,
        source: DeepLink.Source,
        utm: UtmParameters,
        targetAddress: String? = null,
        tokenAddress: String?,
        amount: com.tonapps.icu.Coins?,
        text: String? = null,
        nftAddress: String? = null,
        bin: Cell? = null,
        initStateBase64: String? = null,
        validUnit: Long,
    ) {
        if ((bin != null || initStateBase64 != null) && !amount.isPositive()) {
            toast(Localization.invalid_link)
            return
        }

        val fragment = supportFragmentManager.findFragment<SendScreen>()

        if (targetAddress != null && amount.isPositive() && nftAddress.isNullOrBlank()) {
            val isScam = viewModel.isScamAddress(targetAddress, wallet.network)
            if (isScam) {
                toast(Localization.scam_address_error)
            } else if (bin != null || initStateBase64 != null) {
                try {
                    openSign(
                        wallet = wallet,
                        source = source,
                        utm = utm,
                        targetAddress = targetAddress,
                        tokenAddress = tokenAddress,
                        amountNano = amount?.toBigInteger()!!,
                        bin = bin,
                        initStateBase64 = initStateBase64,
                        comment = text,
                        validUnit = validUnit
                    )
                } catch (ignored: Throwable) {
                    toast(Localization.invalid_link)
                }
            } else {
                openDirectSend(
                    SendScreen.Companion.Builder(wallet, source.analytic, utm)
                        .setTargetAddress(targetAddress)
                        .setTokenAddress(tokenAddress)
                        .setAmount(amount)
                        .setText(text)
                        .setType(SendScreen.Companion.Type.Direct)
                )
            }
        } else if (fragment == null) {
            add(
                SendScreen.newInstance(
                    wallet = wallet,
                    targetAddress = targetAddress,
                    tokenAddress = tokenAddress,
                    amount = amount,
                    text = text,
                    nftAddress = nftAddress,
                    bin = bin,
                    type = SendScreen.Companion.Type.Default,
                    from = source.analytic,
                    utm = utm,
                )
            )
        } else {
            runOnUiThread {
                fragment.initializeBus(source.analytic, utm)

                fragment.initializeArgs(
                    targetAddress = targetAddress,
                    tokenAddress = tokenAddress,
                    amount = amount,
                    text = text,
                    bin = bin,
                    type = SendScreen.Companion.Type.Default,
                )
            }
        }
    }

    // The time the user spends on the lock is left out of the cold launch timing.
    private fun reportLaunchLock(state: LockScreen.State) {
        val tracker = ScreenRenderTracker.shared
        if (state != LockScreen.State.None) {
            isLaunchLockShown = true
            tracker.launchMark(ScreenRenderLaunchStep.PasscodeShown)
            tracker.pauseLaunch()
        } else if (isLaunchLockShown) {
            isLaunchLockShown = false
            tracker.resumeLaunch()
            tracker.launchMark(ScreenRenderLaunchStep.PasscodeEntered)
        }
    }

    fun init(hasWallet: Boolean) {
        ScreenRenderTracker.shared.launchMark(ScreenRenderLaunchStep.RootStart)
        if (hasWallet) {
            setMainFragment()
        } else {
            setIntroFragment()
        }
    }

    private fun setIntroFragment() {
        ScreenRenderTracker.shared.cancelLaunch()
        setPrimaryFragment(StartScreen.newInstance(), runnable = {
            lockView.visibility = View.GONE
            passcodeManager.lockscreenHidden()
        })
    }

    private fun setMainFragment() {
        ScreenRenderTracker.shared.launchMark(ScreenRenderLaunchStep.MainStart)
        ScreenRenderTracker.shared.beginLaunch()
        setPrimaryFragment(MainScreen.newInstance())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_INTENT_CONSUMED, isIntentConsumed)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        tryToApplyStaticFeatureFlags(intent)
        handleIntent(intent, isColdStart = false, isRedelivery = false)
        intent.putExtra(EXTRA_INTENT_HANDLED, true)
    }

    private fun handleIntent(intent: Intent, isColdStart: Boolean, isRedelivery: Boolean) {
        if (intent.action == ACTION_INSTALL_DOWNLOADED_APK) {
            val token = intent.getStringExtra(EXTRA_APK_INSTALL_TOKEN)
            intent.action = null
            viewModel.installDownloadedAPK(token)
            return
        }

        // Legacy pins created before ShortcutDeeplinkActivity still deliver this extra here; it is
        // only opened after validating the URL against our own pinned shortcuts (forge-proof).
        intent.extras?.getStringValue(ShortcutDeeplinkActivity.EXTRA_DAPP_DEEPLINK)?.let {
            viewModel.openLegacyShortcutDApp(it)
            return
        }

        val uri = intent.data ?: intent.getStringExtra("link")?.toUriOrNull()
        if (0 >= DevSettings.firstLaunchDate) {
            DevSettings.firstLaunchDeeplink = uri?.toString() ?: ""
        } else if (uri?.hasRefer() == true || uri?.hasUtmSource() == true) {
            analytics?.openRefDeeplink(uri.toString())
        }
        val extras = intent.extras
        if (extras != null && !extras.isEmpty && viewModel.processIntentExtras(extras)) {
            PUSH_EXTRA_KEYS.forEach(intent::removeExtra)
            return
        } else if (uri != null && !attributionManager.interceptLink(uri, isColdStart)) {
            val link = DeepLink.fixBadUri(uri)
            if (!isRedelivery) {
                DeeplinkOpenAnalytics.report(link, isColdStart)
            }
            processDeepLink(
                link,
                false,
                intent.getStringExtra(Browser.EXTRA_APPLICATION_ID),
                fromExternal = true,
            )
        }
    }

    override fun add(fragment: BaseFragment) {
        if (fragment is SendTransactionScreen || fragment is TonConnectScreen) {
            replaceCurrentTonConnect { super.add(fragment) }
        } else {
            super.add(fragment)
        }
    }

    override fun openURL(url: String) {
        if (url.isBlank()) {
            return
        }
        val uri = url.toUriOrNull() ?: return
        if (DeepLinkRoute.isInApp(uri)) {
            processDeepLink(uri, true, null)
        } else {
            runOnUiThread {
                openExternalLink(uri)
            }
        }
    }

    private fun openExternalLink(uri: Uri) {
        return if (uri.host == "t.me" || uri.scheme == "tg") {
            openTelegramLink(uri)
        } else if (uri.scheme == "mailto") {
            openEmail(uri)
        } else {
            BrowserHelper.open(this, uri)
        }
    }

    private fun openTelegramLink(uri: Uri) {
        if (!safeStartActivity(Intent(Intent.ACTION_VIEW, uri))) {
            BrowserHelper.open(this, uri)
        }
    }

    private fun openEmail(uri: Uri) {
        val intent = Intent(Intent.ACTION_SENDTO, uri)
        safeStartActivity(intent)
    }

    private fun safeStartActivity(intent: Intent): Boolean {
        try {
            startActivity(intent)
            return true
        } catch (e: Throwable) {
            failureToast(Localization.unknown_error)
            return false
        }
    }

    fun processDeepLink(
        uri: Uri,
        internal: Boolean,
        fromPackageName: String?,
        fromQR: Boolean = false,
        fromExternal: Boolean = false,
        waitForWallet: Boolean = false,
    ): Boolean {
        return viewModel.processDeepLink(
            uri,
            fromQR,
            getReferrer(),
            internal,
            fromPackageName,
            fromExternal = fromExternal,
            waitForWallet = waitForWallet,
        )
    }

    override fun openLedgerScreen(
        domain: String,
        timestamp: BigInteger,
        payload: String,
        walletId: String
    ): Pair<BaseFragment, String> {
        return LedgerProofScreen.newInstance(
            domain, timestamp, payload, walletId
        ) to LedgerProofScreen.SIGNED_PROOF
    }

    override fun showLedgerSignScreen(
        transaction: Transaction,
        walletId: String,
        transactionIndex: Int,
        transactionCount: Int
    ): Pair<BaseFragment, String> {
        return LedgerSignScreen.newInstance(
            transaction, walletId, transactionIndex, transactionCount
        ) to LedgerSignScreen.SIGNED_MESSAGE
    }

    override fun showKeystoneSignScreen(
        requestId: String,
        unsignedBody: String,
        isTransaction: Boolean,
        address: String,
        keystone: WalletEntity.Keystone
    ): Pair<BaseFragment, BaseFragment.ResultContract<ByteArray, BitString>> {
        return KeystoneSignScreen.newInstance(
            requestId, unsignedBody, isTransaction, address, keystone
        ).let { it to it.contract }
    }

    override fun newInstance(
        publicKey: PublicKeyEd25519,
        unsignedBody: Cell,
        walletVersion: WalletVersion,
        network: TonNetwork,
        label: String
    ): Pair<BaseFragment, BaseFragment.ResultContract<Uri, BitString>> {
        return SignerSignScreen.newInstance(
            publicKey, unsignedBody, walletVersion, network, label
        ).let { it to it.contract }
    }

    override suspend fun invoke(
        context: Context,
        publicKey: PublicKeyEd25519,
        body: Cell,
        walletVersion: WalletVersion,
        network: TonNetwork,
    ): BitString? {
        return SignerHelper.invoke(context, publicKey, body, walletVersion, network)
    }

    companion object {
        private const val PUSH_PERMISSION_REQUEST_CODE = 5461
        private val PUSH_EXTRA_KEYS = listOf("type", "wallet_id", "link", "deeplink", "dapp_url", "push_id")
        private const val EXTRA_INTENT_HANDLED = "com.tonapps.tonkeeper.extra.INTENT_HANDLED"
        private const val STATE_INTENT_CONSUMED = "intent_consumed"
        const val ACTION_INSTALL_DOWNLOADED_APK = "com.tonapps.tonkeeper.action.INSTALL_DOWNLOADED_APK"
        const val EXTRA_APK_INSTALL_TOKEN = "apk_install_token"
        const val EXTRA_FEATURE_FLAGS = "featureFlags"
        const val EXTRA_BOOT_FLAGS = "bootFlags"
        const val EXTRA_PERPS_TERMS_ACCEPTED = "perpsTermsAccepted"
    }
}