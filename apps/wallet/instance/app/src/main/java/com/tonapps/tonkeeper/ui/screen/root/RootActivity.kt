package com.tonapps.tonkeeper.ui.screen.root

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.Browser
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.tonapps.blockchain.ton.TonTransferHelper
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.ton.extensions.toAccountId
import com.tonapps.extensions.currentTimeSeconds
import com.tonapps.extensions.getStringValue
import com.tonapps.extensions.isPositive
import com.tonapps.extensions.toUriOrNull
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.core.DevSettings
import com.tonapps.tonkeeper.core.entities.TransferEntity
import com.tonapps.tonkeeper.deeplink.DeepLink
import com.tonapps.tonkeeper.extensions.getDefaultWalletTransfer
import com.tonapps.tonkeeper.extensions.hasRefer
import com.tonapps.tonkeeper.extensions.hasUtmSource
import com.tonapps.tonkeeper.extensions.isDarkMode
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.helper.BrowserHelper
import com.tonapps.tonkeeper.koin.remoteConfig
import com.tonapps.tonkeeper.ui.base.BaseWalletActivity
import com.tonapps.tonkeeper.ui.base.QRCameraScreen
import com.tonapps.tonkeeper.ui.base.WalletFragmentFactory
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppScreen
import com.tonapps.tonkeeper.ui.screen.init.InitArgs
import com.tonapps.tonkeeper.ui.screen.init.InitScreen
import com.tonapps.tonkeeper.ui.screen.ledger.sign.LedgerSignScreen
import com.tonapps.tonkeeper.ui.screen.main.MainScreen
import com.tonapps.tonkeeper.ui.screen.send.main.SendScreen
import com.tonapps.tonkeeper.ui.screen.send.transaction.SendTransactionScreen
import com.tonapps.tonkeeper.ui.screen.start.StartScreen
import com.tonapps.tonkeeper.ui.screen.tonconnect.TonConnectScreen
import com.tonapps.tonkeeper.usecase.emulation.EmulationUseCase
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.backgroundPageColor
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.Theme
import com.tonapps.wallet.data.core.entity.RawMessageEntity
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import com.tonapps.wallet.data.passcode.LockScreen
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.passcode.ui.PasscodeView
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.cell.Cell
import uikit.base.BaseFragment
import uikit.dialog.alert.AlertDialog
import uikit.extensions.collectFlow
import uikit.extensions.findFragment
import uikit.extensions.runAnimation
import uikit.extensions.withAlpha

class RootActivity : BaseWalletActivity() {

    private var cachedRootViewModel: RootViewModel? = null

    override val viewModel: RootViewModel
        get() = createOrGetViewModel()

    private val legacyRN: RNLegacy by inject()
    private val settingsRepository by inject<SettingsRepository>()
    private val tokenRepository by inject<TokenRepository>()
    private val accountRepository by inject<AccountRepository>()
    private val emulationUseCase by inject<EmulationUseCase>()
    private val passcodeManager by inject<PasscodeManager>()

    private lateinit var uiHandler: Handler

    private lateinit var lockView: View
    private lateinit var lockPasscodeView: PasscodeView
    private lateinit var lockSignOut: View
    private lateinit var migrationLoaderContainer: View
    private lateinit var migrationLoaderIcon: View

    override fun onCreate(savedInstanceState: Bundle?) {
        val theme = settingsRepository.theme
        setTheme(theme)
        supportFragmentManager.fragmentFactory = WalletFragmentFactory()
        super.onCreate(savedInstanceState)
        if (theme.isSystem) {
            setAppearanceLight(!isDarkMode)
        } else {
            setAppearanceLight(theme.light)
        }
        legacyRN.setActivity(this)
        uiHandler = Handler(mainLooper)

        handleIntent(intent)

        lockView = findViewById(R.id.lock)
        lockPasscodeView = findViewById(R.id.lock_passcode)
        lockPasscodeView.doOnCheck = {
            passcodeManager.lockscreenCheck(this, it)
        }

        lockSignOut = findViewById(R.id.lock_sign_out)
        lockSignOut.setOnClickListener { signOutAll() }

        migrationLoaderContainer = findViewById(R.id.migration_loader_container)
        migrationLoaderContainer.setOnClickListener { }
        migrationLoaderIcon = findViewById(R.id.migration_loader_icon)

        ViewCompat.setOnApplyWindowInsetsListener(lockView) { _, insets ->
            val statusInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            lockView.updatePadding(top = statusInsets.top, bottom = navInsets.bottom)
            insets
        }

        collectFlow(viewModel.hasWalletFlow) { init(it) }
        collectFlow(viewModel.eventFlow) { event(it) }
        collectFlow(viewModel.lockscreenFlow, ::pinState)

        App.applyConfiguration(resources.configuration)
        remoteConfig?.fetchAndActivate()
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
        super.onResume()
        viewModel.connectTonConnectBridge()
    }

    override fun onPause() {
        super.onPause()
        viewModel.disconnectTonConnectBridge()
    }

    private suspend fun pinState(state: LockScreen.State) {
        if (state == LockScreen.State.None) {
            lockView.visibility = View.GONE
            lockPasscodeView.setSuccess()
        } else if (state == LockScreen.State.Error) {
            lockPasscodeView.setError()
        } else {
            lockView.visibility = View.VISIBLE
            if (passcodeManager.isBiometricRequest(this)) {
                if (passcodeManager.confirmationByBiometric(
                        this,
                        getString(Localization.app_name)
                    )
                ) {
                    passcodeManager.lockscreenBiometric()
                } else {
                    toast(Localization.authorization_required)
                }
            }
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
        cachedRootViewModel = null
        viewModelStore.clear()
        super.onDestroy()
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

    private fun setAppearanceLight(light: Boolean) {
        with(windowInsetsController) {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(R.layout.activity_root)
    }

    fun event(event: RootEvent) {
        when (event) {
            is RootEvent.Singer -> add(
                InitScreen.newInstance(
                    if (event.qr) InitArgs.Type.SignerQR else InitArgs.Type.Signer,
                    event.publicKey,
                    event.name
                )
            )

            is RootEvent.Ledger -> add(
                InitScreen.newInstance(
                    type = InitArgs.Type.Ledger,
                    ledgerConnectData = event.connectData,
                    accounts = event.accounts
                )
            )

            is RootEvent.Transfer -> {
                lifecycleScope.launch {
                    openSend(
                        targetAddress = event.address,
                        tokenAddress = event.jettonAddress,
                        amountNano = event.amount,
                        text = event.text,
                        wallet = event.wallet,
                        bin = event.bin,
                        initStateBase64 = event.initStateBase64
                    )
                }
            }

            is RootEvent.CloseCurrentTonConnect -> closeCurrentTonConnect {}
            is RootEvent.OpenDAppByShortcut -> openDAppByShortcut(event.wallet, event.url)
            else -> {}
        }
    }

    private fun openDAppByShortcut(wallet: WalletEntity, uri: Uri) {
        removeByClass({
            add(
                DAppScreen.newInstance(
                    wallet = wallet,
                    title = uri.host ?: "unknown",
                    url = uri,
                    source = "shortcut",
                )
            )
        }, DAppScreen::class.java)
    }

    private fun closeCurrentTonConnect(runnable: Runnable) {
        removeByClass(runnable, SendTransactionScreen::class.java, TonConnectScreen::class.java)
    }

    private suspend fun getJettonForwardAmount(
        wallet: WalletEntity,
        message: RawMessageEntity
    ): com.tonapps.icu.Coins {
        try {
            val transfer = message.getDefaultWalletTransfer()

            val emulated = emulationUseCase(
                message = accountRepository.messageBody(wallet, currentTimeSeconds() + 10 * 60, listOf(transfer)),
                params = true
            )

            return if (emulated.extra.isRefund) {
                TransferEntity.BASE_FORWARD_AMOUNT
            } else {
                emulated.extra.value + TransferEntity.BASE_FORWARD_AMOUNT
            }
        } catch (_: Throwable) {
            return TransferEntity.POINT_ONE_TON
        }
    }

    private suspend fun openSign(
        wallet: WalletEntity,
        targetAddress: String,
        tokenAddress: String?,
        amountNano: Long,
        bin: Cell?,
        initStateBase64: String?,
        comment: String? = null
    ) {
        val message = if (tokenAddress != null) {
            val tokens =
                tokenRepository.get(settingsRepository.currency, wallet.accountId, wallet.testnet)
                    ?: emptyList()
            val token = tokens.find { it.address == AddrStd(tokenAddress).toAccountId() }
                ?: throw IllegalStateException("Token not found")
            val message = RawMessageEntity(
                addressValue = token.balance.walletAddress,
                amount = TransferEntity.BASE_FORWARD_AMOUNT.toLong(),
                stateInitValue = initStateBase64,
                payloadValue = TonTransferHelper.jetton(
                    coins = Coins.ofNano(amountNano),
                    toAddress = AddrStd(targetAddress),
                    responseAddress = wallet.contract.address,
                    queryId = TransferEntity.newWalletQueryId(),
                    forwardPayload = bin ?: comment?.let {
                        TransferEntity.comment(it)
                    },
                ).base64()
            )

            message.copy(amount = getJettonForwardAmount(wallet, message).toLong())
        } else {
            RawMessageEntity(
                addressValue = targetAddress,
                amount = amountNano,
                stateInitValue = initStateBase64,
                payloadValue = bin?.base64() ?: comment?.let {
                    TransferEntity.comment(it)
                }?.base64()
            )
        }

        val request = SignRequestEntity.Builder()
            .setFrom(wallet.contract.address)
            .setValidUntil(currentTimeSeconds() + 10 * 60)
            .addMessage(message)
            .setTestnet(wallet.testnet)
            .build(Uri.parse("tonkeeper://signRaw/"))

        val screen = SendTransactionScreen.newInstance(wallet, request)
        add(screen)
    }

    private fun openDirectSend(builder: SendScreen.Companion.Builder) {
        removeByClass({
            add(builder.build())
        }, SendScreen::class.java)
    }

    private suspend fun openSend(
        wallet: WalletEntity,
        targetAddress: String? = null,
        tokenAddress: String?,
        amountNano: Long?,
        text: String? = null,
        nftAddress: String? = null,
        bin: Cell? = null,
        initStateBase64: String? = null
    ) {
        if ((bin != null || initStateBase64 != null) && !amountNano.isPositive()) {
            toast(Localization.invalid_link)
            return
        }

        val fragment = supportFragmentManager.findFragment<SendScreen>()

        if (targetAddress != null && amountNano.isPositive() && nftAddress.isNullOrBlank()) {
            if (bin != null || initStateBase64 != null) {
                openSign(
                    wallet = wallet,
                    targetAddress = targetAddress,
                    tokenAddress = tokenAddress,
                    amountNano = amountNano!!,
                    bin = bin,
                    initStateBase64 = initStateBase64,
                    comment = text,
                )
            } else {
                openDirectSend(
                    SendScreen.Companion.Builder(wallet)
                        .setTargetAddress(targetAddress)
                        .setTokenAddress(tokenAddress)
                        .setAmountNano(amountNano)
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
                    amountNano = amountNano,
                    text = text,
                    nftAddress = nftAddress,
                    bin = bin,
                    type = SendScreen.Companion.Type.Default
                )
            )
        } else {
            runOnUiThread {
                fragment.initializeArgs(
                    targetAddress = targetAddress,
                    tokenAddress = tokenAddress,
                    amountNano = amountNano,
                    text = text,
                    bin = bin,
                    type = SendScreen.Companion.Type.Default
                )
            }
        }
    }

    private fun signOutAll() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(Localization.sign_out_all_title)
        builder.setMessage(Localization.sign_out_all_description)
        builder.setNegativeButton(Localization.sign_out) {
            passcodeManager.deleteAll()
            viewModel.signOut()
            setIntroFragment()
        }
        builder.setPositiveButton(Localization.cancel)
        builder.show()
    }

    fun init(hasWallet: Boolean) {
        if (hasWallet) {
            setMainFragment()
        } else {
            setIntroFragment()
        }
    }

    private fun setIntroFragment() {
        setPrimaryFragment(StartScreen.newInstance(), runnable = {
            lockView.visibility = View.GONE
        })
    }

    private fun setMainFragment() {
        setPrimaryFragment(MainScreen.newInstance())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val uri = intent.data ?: intent.getStringExtra("link")?.toUriOrNull()
        if (0 >= DevSettings.firstLaunchDate) {
            DevSettings.firstLaunchDeeplink = uri?.toString() ?: ""
        } else if (uri?.hasRefer() == true || uri?.hasUtmSource() == true) {
            AnalyticsHelper.openRefDeeplink(settingsRepository.installId, uri.toString())
        }
        val extras = intent.extras
        val dappDeepLink = extras?.getStringValue("dapp_deeplink")?.toUriOrNull()
        if (dappDeepLink != null) {
            viewModel.openDApp(dappDeepLink)
            return
        } else if (extras != null && !extras.isEmpty && viewModel.processIntentExtras(extras)) {
            return
        } else if (uri != null) {
            processDeepLink(
                DeepLink.fixBadUri(uri),
                false,
                intent.getStringExtra(Browser.EXTRA_APPLICATION_ID)
            )
        }
    }

    override fun add(fragment: BaseFragment) {
        if (fragment is SendTransactionScreen || fragment is TonConnectScreen) {
            closeCurrentTonConnect {
                super.add(fragment)
            }
        } else {
            super.add(fragment)
        }
    }

    override fun openURL(url: String) {
        if (url.isBlank()) {
            return
        }
        val uri = url.toUriOrNull() ?: return
        if (uri.scheme == "tonkeeper" || uri.scheme == "ton" || uri.scheme == "tc" || uri.host == "app.tonkeeper.com") {
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
            toast(Localization.unknown_error)
            return false
        }
    }

    fun processDeepLink(uri: Uri, internal: Boolean, fromPackageName: String?) {
        viewModel.processDeepLink(uri, false, getReferrer(), internal, fromPackageName)
    }
}