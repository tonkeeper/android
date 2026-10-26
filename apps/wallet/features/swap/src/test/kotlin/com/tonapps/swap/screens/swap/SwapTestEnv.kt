package com.tonapps.swap.screens.swap

import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.tonapps.core.flags.WalletFeatureKey
import com.tonapps.extensions.AppLifecycleProvider
import com.tonapps.network.NetworkMonitor
import com.tonapps.swap.SwapRoutes
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.core.ExchangeAPI
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestRemoteConfig
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import com.tonapps.wallet.testing.testWallet
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.exchange.SwapRepository
import com.tonapps.wallet.data.multichain.legacy.LegacySwapAccounts
import com.tonapps.wallet.data.multichain.legacy.LegacySwapPair
import com.tonapps.wallet.data.multichain.legacy.LegacySwapWallet
import com.tonapps.wallet.data.raffle.RaffleRepository
import com.tonapps.wallet.data.raffle.entities.RaffleEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import io.exchangeapi.apis.CrosschainApi
import io.exchangeapi.infrastructure.Serializer
import io.exchangeapi.models.CreateCrossSwapQuoteRequest
import io.exchangeapi.models.CrossSwapConfig
import io.exchangeapi.models.CrossSwapExactType
import io.exchangeapi.models.CrossSwapPrepare
import io.exchangeapi.models.CrossSwapQuote
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.TimeSource
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The world a [SwapFeature] runs in.
 *
 * Real: the whole graph and the real [SwapRepository], answering from responses captured verbatim
 * from `https://swap.tonkeeper.com` (`src/test/resources`), plus real chainkit address validation —
 * wallet-core's jvm artifact unpacks its own native lib, so accounts are built from addresses the
 * `chainkit` CLI derived for its stored test wallet.
 *
 * Faked: the HTTP layer, and the Android globals the feature reaches for on construction (the
 * exact-output flag through [FeatureManager], [AppContext] for the fallback pair).
 */
class SwapTestEnv(
    private val context: Context,
    private val timeSource: TimeSource,
    features: TestRemoteConfig,
    val timings: SwapTimings = SwapTimings(),
    exactOutputEnabled: Boolean = true,
    swapKitEnabled: Boolean = false,
    // The real trigger replays its last refresh to a new subscriber; one the app has never fired
    // has nothing to replay.
    refreshReplayed: Boolean = true,
) {

    /** Every quote request the feature sent, in order. */
    val quoteRequests = CopyOnWriteArrayList<CreateCrossSwapQuoteRequest>()

    /** The X-Wallet-ID each quote request went out with, in order; null for an unregistered wallet. */
    val quoteWalletIds = CopyOnWriteArrayList<String?>()

    /** Every config request the feature sent, as (walletId, sellAssetId, buyAssetId). */
    val configRequests = CopyOnWriteArrayList<Triple<String, String?, String?>>()

    /** Every route id the feature asked to prepare, in order. */
    val prepareRequests = CopyOnWriteArrayList<String>()

    /** Accounts the wallet owns, keyed by asset id; an id that is missing resolves to null. */
    val accounts = linkedMapOf(
        SwapFixtures.TON_ASSET_ID to SwapFixtures.tonAccount,
        SwapFixtures.USDT_TON_ASSET_ID to SwapFixtures.usdtTonAccount,
        SwapFixtures.ETH_ASSET_ID to SwapFixtures.ethAccount,
        SwapFixtures.USDT_ETH_ASSET_ID to SwapFixtures.usdtEthAccount,
        SwapFixtures.BTC_ASSET_ID to SwapFixtures.btcAccount,
        SwapFixtures.TRX_ASSET_ID to SwapFixtures.trxAccount,
        SwapFixtures.USDT_TRON_ASSET_ID to SwapFixtures.usdtTronAccount,
    )

    var configAnswer: () -> CrossSwapConfig = { SwapFixtures.config }

    var quoteAnswer: (CreateCrossSwapQuoteRequest) -> CrossSwapQuote = { request ->
        when (request.exactType) {
            CrossSwapExactType.exact_output -> SwapFixtures.exactOutputQuote
            else -> SwapFixtures.exactInputQuote
        }
    }

    var prepareAnswer: (String) -> CrossSwapPrepare = {
        throw IllegalStateException("no prepare response set up for this test")
    }

    var accountAnswer: suspend (String) -> AccountWithDetails? = { assetId -> accounts[assetId] }

    var forcedAccountAnswer: suspend (String) -> AccountWithDetails? = { assetId -> accountAnswer(assetId) }

    val accountReads = CopyOnWriteArrayList<String>()

    val forcedAccountReads = CopyOnWriteArrayList<String>()

    /** Every account read for a legacy wallet, by asset id; [accountReads] holds them too. */
    val legacyAccountReads = CopyOnWriteArrayList<String>()

    val refreshTrigger = MutableSharedFlow<Unit>(replay = 1).apply {
        if (refreshReplayed) {
            tryEmit(Unit)
        }
    }

    val appInForeground = MutableStateFlow(true)

    val isOnline = MutableStateFlow(true)

    var walletIdAnswer: () -> String? = { SwapFixtures.WALLET_ID }

    var hiddenBalances = false

    /** The raffles the wallet takes part in. */
    var raffles: List<RaffleEntity> = emptyList()

    /** Every wallet id the raffles were asked for, in order. */
    val raffleRequests = CopyOnWriteArrayList<String>()

    private val crosschain = mockk<CrosschainApi>()
    private val accountRepo = mockk<McAccountRepository>()
    private val oldAccount = mockk<AccountRepository>()
    private val raffleRepository = mockk<RaffleRepository>()

    /** The legacy swap wallet the selected wallet resolves to; null is a multichain wallet. */
    var legacyWallet: LegacySwapWallet? = null

    /** How the legacy lookup answers for a wallet id; a test may delay it or make it throw. */
    var legacyLookup: suspend (String) -> LegacySwapWallet? = { legacyWallet }

    /** Whether the multichain store has the selected wallet; false is a legacy wallet the swap cannot sign for. */
    var isMultichainWallet = true

    /** Every wallet id the legacy lookup was asked about, in order. */
    val legacyLookups = CopyOnWriteArrayList<String>()

    /** The fixed pair a legacy wallet opens on, as (sell, buy) asked for by the route. */
    var legacyPair: (String?, String?) -> LegacySwapPair = { _, _ ->
        LegacySwapPair(sell = SwapFixtures.tonAsset, buy = SwapFixtures.usdtTronAsset)
    }

    private val legacyAccounts = mockk<LegacySwapAccounts>()
    private val settings = mockk<SettingsRepository>()
    private val networkMonitor = mockk<NetworkMonitor> {
        every { isOnlineFlow } returns isOnline
    }
    private val appLifecycle = mockk<AppLifecycleProvider> {
        every { observeWithoutGrace } returns appInForeground
    }

    /** The real repository over recorded responses; a test may replace it to control timing. */
    var swapRepo: SwapRepository

    init {
        features
            .set(WalletFeatureKey.IS_SWAP_EXACT_OUTPUT_ENABLED, exactOutputEnabled)
            .set(WalletFeatureKey.IS_SWAPKIT_HARD_SWITCH_ENABLED, swapKitEnabled)

        val exchange = mockk<ExchangeAPI>()
        val api = mockk<API>()
        every { api.exchange } returns exchange
        every { exchange.swapV2 } returns crosschain
        every { crosschain.getCrossSwapConfig(any(), any(), any()) } answers {
            configRequests += Triple(firstArg(), secondArg(), thirdArg())
            configAnswer()
        }
        every { crosschain.createCrossSwapQuote(any(), any(), any()) } answers {
            val request = firstArg<CreateCrossSwapQuoteRequest>()
            quoteRequests += request
            quoteWalletIds += secondArg<String?>()
            quoteAnswer(request)
        }
        every { crosschain.prepareCrossSwapRoute(any(), any(), any(), any()) } answers {
            val routeId = firstArg<String>()
            prepareRequests += routeId
            prepareAnswer(routeId)
        }

        every { settings.installId } returns "test-install-id"
        every { settings.hiddenBalances } answers { hiddenBalances }

        swapRepo = SwapRepository(api, settings)

        coEvery { accountRepo.findAccount(any(), any(), any(), any()) } coAnswers {
            val assetId = secondArg<String>()
            accountReads += assetId
            if (arg<Boolean>(3)) {
                forcedAccountReads += assetId
                forcedAccountAnswer(assetId)
            } else {
                accountAnswer(assetId)
            }
        }
        every { accountRepo.refreshTrigger } returns refreshTrigger
        coEvery { accountRepo.getWallet(any()) } coAnswers { testWallet().takeIf { isMultichainWallet } }
        coEvery { legacyAccounts.find(any()) } coAnswers {
            val walletId = firstArg<String>()
            legacyLookups += walletId
            legacyLookup(walletId)
        }
        every { legacyAccounts.pair(any(), any()) } answers { legacyPair(firstArg(), secondArg()) }
        coEvery { legacyAccounts.findAccount(any(), any(), any()) } coAnswers {
            val assetId = secondArg<String>()
            accountReads += assetId
            legacyAccountReads += assetId
            accountAnswer(assetId)
        }
        every { oldAccount.getSelectedWalletId() } answers { walletIdAnswer() }
        every { oldAccount.selectedWalletFlow } returns emptyFlow()
        every { raffleRepository.getRafflesFlow(any(), any()) } answers {
            raffleRequests += firstArg<String>()
            flowOf(raffles)
        }
    }

    // ViewModelStore is the only public way to cancel a ViewModel: clear() itself is internal.
    private val store = ViewModelStore()
    private var featureCount = 0

    fun feature(initialRoute: SwapRoutes.Swap = SwapRoutes.Swap()): SwapFeature = SwapFeature(
        initialRoute = initialRoute,
        context = context,
        oldAccount = oldAccount,
        accountRepo = accountRepo,
        swapRepo = swapRepo,
        raffleRepository = raffleRepository,
        appLifecycle = appLifecycle,
        settingsRepository = settings,
        networkMonitor = networkMonitor,
        legacyAccounts = legacyAccounts,
        timings = timings,
        timeSource = timeSource,
    ).also { store.put("swap-${featureCount++}", it) }

    /**
     * Cancels the graph. Without it a live quote keeps its TTL cycling (requote -> quote -> TTL),
     * which in virtual time never idles and hangs the scheduler's drain at the end of a test.
     */
    fun clear() {
        store.clear()
    }
}

/** The recorded backend responses, and the accounts they were quoted for. */
object SwapFixtures {

    val TON_ASSET_ID = TestAssets.ton.id
    val USDT_TON_ASSET_ID = TestAssets.usdtTon.id
    val ETH_ASSET_ID = TestAssets.eth.id
    val USDT_ETH_ASSET_ID = TestAssets.usdtEth.id
    val BTC_ASSET_ID = TestAssets.btc.id
    val TRX_ASSET_ID = TestAssets.trx.id
    val USDT_TRON_ASSET_ID = TestAssets.usdtTron.id

    const val WALLET_ID = TestWallet.ID
    const val TON_ADDRESS = TestWallet.TON_ADDRESS
    const val ETH_ADDRESS = TestWallet.ETH_ADDRESS
    const val BTC_ADDRESS = TestWallet.BTC_ADDRESS
    const val TRON_ADDRESS = TestWallet.TRON_ADDRESS

    val tonAsset = TestAssets.ton
    val usdtTonAsset = TestAssets.usdtTon
    val ethAsset = TestAssets.eth
    val usdtEthAsset = TestAssets.usdtEth
    val btcAsset = TestAssets.btc
    val trxAsset = TestAssets.trx
    val usdtTronAsset = TestAssets.usdtTron

    // TON holds 5, the balance every "insufficient funds" case is measured against; the assets the
    // provider matrix quotes are funded well above what those cases sell.
    val tonAccount = testAccount(tonAsset, balance = "5000000000", price = "1.43")
    val usdtTonAccount = testAccount(usdtTonAsset, balance = "0")
    val ethAccount = testAccount(ethAsset, balance = "100000000000000000", price = "2486.41")
    val usdtEthAccount = testAccount(usdtEthAsset, balance = "50000000")
    val btcAccount = testAccount(btcAsset, balance = "10000000", price = "78900.0")
    val trxAccount = testAccount(trxAsset, balance = "10000000000", price = "0.32")
    val usdtTronAccount = testAccount(usdtTronAsset, balance = "100000000")

    val config: CrossSwapConfig by lazy { decodeResource("swap_config.json") }
    val exactInputQuote: CrossSwapQuote by lazy { decodeResource("swap_quote_exact_input.json") }
    val exactOutputQuote: CrossSwapQuote by lazy { decodeResource("swap_quote_exact_output.json") }
    val noRouteQuote: CrossSwapQuote by lazy { decodeResource("swap_quote_no_route.json") }

    fun quote(name: String): CrossSwapQuote = decodeResource("swap_quote_$name.json")

    fun prepare(name: String): CrossSwapPrepare = decodeResource("swap_prepare_$name.json")

    private inline fun <reified T> decodeResource(name: String): T {
        val stream = checkNotNull(SwapFixtures::class.java.classLoader?.getResourceAsStream(name)) {
            "missing test resource $name"
        }
        val json = stream.bufferedReader().use { it.readText() }

        return Serializer.kotlinxSerializationJson.decodeFromString(json)
    }
}