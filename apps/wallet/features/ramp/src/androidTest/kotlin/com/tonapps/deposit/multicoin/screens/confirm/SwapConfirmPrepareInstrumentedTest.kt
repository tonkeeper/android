package com.tonapps.deposit.multicoin.screens.confirm

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmAction
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.CryptoKitClient
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.chainkit.core.chain.model.account.PrivateKey
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.core.net.module.NetConfig
import com.tonapps.chainkit.core.net.module.NetModule
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.data.dapps.wc.WcRepository
import com.tonapps.wallet.data.multichain.account.AccountBalanceEntity
import com.tonapps.wallet.data.multichain.account.AccountEntity
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.data.multichain.exchange.SwapPayloadInfo
import com.tonapps.wallet.data.multichain.exchange.SwapQuote
import com.tonapps.wallet.data.multichain.exchange.SwapRepository
import com.tonapps.wallet.data.multichain.exchange.SwapRoute
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.multichain.wallet.McWalletType
import com.tonapps.wallet.data.passcode.PasscodeManager
import io.exchangeapi.models.CrossSwapCalldataPayloadType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Instrumented (on-device) test for [SwapConfirmFeature].
 *
 * It drives the real [SwapConfirmFeature.prepareSwap] pipeline for the seven cross-chain swap
 * pairs captured in `swap_prepare_payloads.txt`, then builds the **unsigned** blockchain
 * transaction through the real BlockchainKit ([ChainKitProvider]'s [CryptoKitClient] mediator).
 *
 * What is mocked vs. real:
 *  - MOCKED: the swap quote/route responses ([SwapRepository.fetchQuote] / [SwapRepository.prepareRoute]),
 *    seeded from the captured payloads. Routes expire ~1 minute after capture, so a live re-fetch is
 *    non-deterministic — hence the mock. Also mocked: [McAccountRepository] (accounts derived from a
 *    fixed test mnemonic), and the unused-here [PasscodeManager] / [WcRepository].
 *  - REAL: [SwapConfirmFeature.prepareSwap] (produces a real [PendingTransaction]); the real
 *    [ChainKitProvider] for fee/nonce/balance (live RPC); and the real mediator build path
 *    `sign.transaction.buildSigningInputAndEncode(tx, fee, nonce, wallet=null-key)` for the encode step.
 *
 * Requirements / caveats (this is an integration test):
 *  - Must run on a device/emulator (TrustWalletCore native lib) with network access (live RPC for
 *    fee/nonce and for the TON/TRON/BTC unsigned-build step; EVM builds are offline).
 *  - The BTC unsigned build needs UTXOs for the test wallet's BTC address, so the `btc_to_eth` case
 *    only fully encodes if that address is funded on mainnet; otherwise its encode step fails with an
 *    insufficient-inputs error while the structural (PendingTransaction) assertions still hold.
 *
 * Run: `./gradlew :apps:wallet:features:ramp:connectedDebugAndroidTest`
 */
@RunWith(Parameterized::class)
class SwapConfirmPrepareInstrumentedTest(
    private val case: SwapCase,
) {

    // ---- prepareSwap: mock quote/route + accounts, run the real feature ----------------------------

    @Test
    fun prepareAndEncodeUnsignedTransaction() {
        val walletId = "test-wallet"

        val accountRepo = mockk<McAccountRepository>()
        val swapRepo = mockk<SwapRepository>()
        val passcodeManager = mockk<PasscodeManager>(relaxed = true)
        val wcRepository = mockk<WcRepository>(relaxed = true)

        // Accounts, keyed by asset id, derived from the fixed test wallet.
        val accounts = HashMap<String, AccountWithDetails>()
        val source = buildAccount(walletId, case.sourceAssetId, case.sourceDecimals, case.sourceSymbol, available = case.sourceAmount)
        val destination = buildAccount(walletId, case.destAssetId, case.destDecimals, case.destSymbol)
        accounts[case.sourceAssetId] = source
        accounts[case.destAssetId] = destination
        // A token source pays gas in its chain's native coin — prepareSwap looks that account up too.
        val energyCoinId = source.asset.value.chain.coinAssetId
        accounts.getOrPut(energyCoinId) { buildAccount(walletId, energyCoinId, 0, "") }

        val wallet = McWalletEntity(
            credentialId = "test-credential",
            name = "Test",
            type = McWalletType.Multicoin,
        )

        coEvery { accountRepo.getWallet(walletId) } returns wallet
        coEvery { accountRepo.findAccount(walletId, any(), any(), any()) } answers { accounts[secondArg<String>()] }

        val quote = SwapQuote(
            routeId = case.routeId,
            sourceBaseAmount = BigInteger.parseString(case.sourceAmount),
            buyBaseAmount = BigInteger.parseString(case.buyAmount),
            minimumBuyBaseAmount = BigInteger.parseString(case.minBuyAmount),
            slippageBps = SLIPPAGE_BPS,
            priceImpactBps = case.priceImpactBps,
            payloads = null,
            provider = SwapQuote.Provider.SwapXyz,
        )
        // Mirrors SwapRepository.prepareRoute for a swapsxyz `isFlexible = true` main payload:
        // `data`/`mode` are dropped, leaving just the deposit target and amount.
        val route = SwapRoute(
            main = SwapPayloadInfo.Data(
                to = case.payloadTo,
                amount = BigInteger.parseString(case.payloadAmount),
                data = null,
                calldataType = CrossSwapCalldataPayloadType.flex,
                mode = null,
                fee = null,
            ),
            approval = null,
        )

        coEvery { swapRepo.getSlippage(any(), any()) } returns null
        coEvery { swapRepo.fetchQuote(any(), any(), any(), any()) } returns quote
        coEvery { swapRepo.prepareRoute(any(), any(), any()) } returns route
        every { swapRepo.formatRateLabel(any(), any(), any()) } returns "1 ${case.sourceSymbol}"

        val request = ConfirmRequest(
            data = CommonTransactionData(assetId = case.sourceAssetId, walletId = walletId),
            type = ConfirmType.Swap(
                sourceAmount = BigInteger.parseString(case.sourceAmount),
                destinationAssetId = case.destAssetId,
                isMax = false,
                slippageBps = SLIPPAGE_BPS,
                quote = null, // force the fetchQuote path instead of reusing a snapshot
            ),
            action = ConfirmAction.SignAndSend,
        )

        val feature = SwapConfirmFeature(
            request = request,
            provider = provider,
            accountRepo = accountRepo,
            postTransactionRefreshSchedule = mockk(relaxed = true),
            swapRepo = swapRepo,
            passcodeManager = passcodeManager,
            wcRepository = wcRepository,
            // Swaps have no fee choice, so prepareSwap never touches either of these.
            feeBuilder = mockk(relaxed = true),
            gaslessSender = mockk(relaxed = true),
            settingsRepository = mockk(relaxed = true),
        )

        val pending = runBlocking {
            withTimeout(PREPARE_TIMEOUT_MS) { feature.pendingTx.first() }
        }

        assertNotNull(
            "prepareSwap returned null for ${case.name}; confirmationError=${feature.confirmationError.value}",
            pending,
        )
        pending!!

        // ---- assert the real PendingTransaction --------------------------------------------------
        assertEquals(case.sourceAssetId, pending.account.asset.id)
        assertEquals(case.destAssetId, pending.destination?.asset?.id)
        assertEquals(case.routeId, pending.quote?.routeId)
        assertEquals(BigInteger.parseString(case.buyAmount), pending.quote?.buyBaseAmount)

        val signing = pending.signing
        assertTrue("expected Signing.Tx for ${case.name}", signing is Signing.Tx)
        val tx = (signing as Signing.Tx).value
        assertTrue("expected Transaction.Swap for ${case.name}", tx is Transaction.Swap)
        tx as Transaction.Swap

        assertEquals("swap `to` for ${case.name}", case.payloadTo, tx.to.display)
        assertEquals("swap amount for ${case.name}", BigInteger.parseString(case.payloadAmount), tx.amount)
        assertEquals(case.destAssetId, tx.destination.asset.id)
        assertEquals("energy asset for ${case.name}", energyCoinId, tx.energy.id)

        // ---- build the UNSIGNED transaction with BlockchainKit ------------------------------------
        val network = tx.account.chain.network.type
        val mediator = client.blockchain.getMediator(network)
        val fee = pending.fee.estimated ?: Fee.None
        val nonce = pending.nonce ?: BigInteger.ZERO

        // EVM/TON/TRON implement the PrivateKey? overload; Bitcoin (UTXO) implements the CryptoWallet?
        // overload (it needs public keys + UTXOs). Pick the one the chain provides. Either way no
        // signing happens — buildSigningInput leaves the key empty, producing an UNSIGNED SigningInput.
        val built = runBlocking {
            withTimeout(BUILD_TIMEOUT_MS) {
                val delegate = mediator.sign.transaction
                if (tx.account.chain is Chain.Bitcoin) {
                    delegate.buildSigningInput(tx, fee, nonce, testWallet)
                } else {
                    delegate.buildSigningInput(tx, fee, nonce, null as PrivateKey?)
                }
            }
        }

        assertNull("unsigned build failed for ${case.name}: ${built.error}", built.error)
        val unsignedTx = built.unwrap().firstOutput()
        assertTrue("empty unsigned tx for ${case.name}", unsignedTx.encode().isNotEmpty())
    }

    // ---- helpers -----------------------------------------------------------------------------------

    private fun buildAccount(
        walletId: String,
        assetId: String,
        decimals: Int,
        symbol: String,
        available: String = "0",
    ): AccountWithDetails {
        val assetEntity = AssetEntity(
            id = assetId,
            name = symbol,
            symbol = symbol,
            decimals = decimals,
            imageUrl = "",
        )
        val chain = assetEntity.value.chain
        val addressType = when (chain) {
            is Chain.Ton -> Address.Type.TonV5R1
            is Chain.Bitcoin -> Address.Type.BtcSegwit
            else -> Address.Type.Default
        }
        val address = testWallet.getAddress(chain, addressType)
        val pubKey = testWallet.getPublicKey(chain)

        return AccountWithDetails(
            data = AccountEntity(
                walletId = walletId,
                network = chain.network.type.id,
                mode = chain.network.mode.id,
                displayAddress = address.display,
                publicKey = pubKey.defaultHex,
                segwitPublicKey = pubKey.segWit,
                addressType = addressType,
            ),
            balance = AccountBalanceEntity(available = available),
            asset = assetEntity,
            rate = null,
            isHidden = false,
        )
    }

    data class SwapCase(
        val name: String,
        val sourceAssetId: String,
        val sourceDecimals: Int,
        val sourceSymbol: String,
        val destAssetId: String,
        val destDecimals: Int,
        val destSymbol: String,
        val sourceAmount: String,
        val routeId: String,
        val buyAmount: String,
        val minBuyAmount: String,
        val priceImpactBps: Int?,
        val payloadTo: String,
        val payloadAmount: String,
    ) {
        override fun toString(): String = name
    }

    companion object {

        private const val SLIPPAGE_BPS = 300
        private const val PREPARE_TIMEOUT_MS = 120_000L
        private const val BUILD_TIMEOUT_MS = 60_000L

        // Public BIP39 test vector — derives valid addresses/keys for every supported chain.
        private const val MNEMONIC =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

        private lateinit var client: CryptoKitClient
        private lateinit var provider: ChainKitProvider
        private lateinit var testWallet: CryptoWallet

        @JvmStatic
        @BeforeClass
        fun setUpClass() {
            ChainKitProvider.init() // loads TrustWalletCore native lib
            client = CryptoKitClient(
                netModule = NetModule(
                    NetConfig(
                        isLogging = false,
                        userAgent = "SwapConfirmPrepareTest",
                        rateLimitTime = 1000,
                        rateLimitCount = 5,
                    ),
                ),
            )
            provider = ChainKitProvider(client)
            testWallet = CryptoWallet.fromMnemonic(MNEMONIC)
        }

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = listOf(
            // #1 BTC -> ETH (source coin, UTXO)
            SwapCase(
                name = "btc_to_eth",
                sourceAssetId = "btc/mainnet/coin",
                sourceDecimals = 8,
                sourceSymbol = "BTC",
                destAssetId = "eth/mainnet/coin",
                destDecimals = 18,
                destSymbol = "ETH",
                sourceAmount = "40000",
                routeId = "cffa0b39-4556-4068-ba9d-67da15144775",
                buyAmount = "13384840697163948",
                minBuyAmount = "12983295476249029",
                priceImpactBps = -76,
                payloadTo = "bc1pkttp384rgrpwudqgjh95c5zt27afj0ru6hyrzzwmrsr924xwpkvsw540zy",
                payloadAmount = "40000",
            ),
            // #2 ETH -> USDT (arb, source coin, EVM)
            SwapCase(
                name = "eth_to_usdt_arb",
                sourceAssetId = "eth/mainnet/coin",
                sourceDecimals = 18,
                sourceSymbol = "ETH",
                destAssetId = "arb/mainnet/erc20/0xfd086bc7cd5c481dcc9c85ebe478a1c0b69fcbb9",
                destDecimals = 6,
                destSymbol = "USDT",
                sourceAmount = "8000000000000000",
                routeId = "4f377a3c-1408-4d4c-be1d-f1e87fb05ca7",
                buyAmount = "15304585",
                minBuyAmount = "14845447",
                priceImpactBps = -50,
                payloadTo = "0x263B0fc37f349a40E8B14b29D173Ca1381A75F50",
                payloadAmount = "8000000000000000",
            ),
            // #3 USDT (erc20) -> BTC (source token, EVM)
            SwapCase(
                name = "usdt_erc20_to_btc",
                sourceAssetId = "eth/mainnet/erc20/0xdAC17F958D2ee523a2206206994597C13D831ec7",
                sourceDecimals = 6,
                sourceSymbol = "USDT",
                destAssetId = "btc/mainnet/coin",
                destDecimals = 8,
                destSymbol = "BTC",
                sourceAmount = "40000000",
                routeId = "42e97a65-08a7-45fb-92b3-0c70440ef488",
                buyAmount = "61488",
                minBuyAmount = "59643",
                priceImpactBps = 0,
                payloadTo = "0x1db41b617432ddc24bc689989c3b8b6e247482f7",
                payloadAmount = "40000000",
            ),
            // #4 TON -> ETH (source coin, TVM)
            SwapCase(
                name = "ton_to_eth",
                sourceAssetId = "ton/mainnet/coin",
                sourceDecimals = 9,
                sourceSymbol = "TON",
                destAssetId = "eth/mainnet/coin",
                destDecimals = 18,
                destSymbol = "ETH",
                sourceAmount = "8000000000",
                routeId = "6ca2f5eb-0023-4926-a3b3-84a1a7c9754c",
                buyAmount = "6492300000000000",
                minBuyAmount = "6492300000000000",
                priceImpactBps = -194,
                payloadTo = "UQCIHpB5tKQWspHWnvTUZb686JfvrMNmNT5u69pvv8uOR1gj",
                payloadAmount = "8000000000",
            ),
            // #5 USDT (jetton) -> ETH (source token, TVM)
            SwapCase(
                name = "usdt_jetton_to_eth",
                sourceAssetId = "ton/mainnet/jetton/0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe",
                sourceDecimals = 6,
                sourceSymbol = "USDT",
                destAssetId = "eth/mainnet/coin",
                destDecimals = 18,
                destSymbol = "ETH",
                sourceAmount = "20000000",
                routeId = "5b6eaa27-3d5b-44f6-bba0-3eccbd6a30e8",
                buyAmount = "10231100000000000",
                minBuyAmount = "10231100000000000",
                priceImpactBps = -166,
                payloadTo = "EQBlL2rVVK1xgd42ecyMx4cgbmG98X36ijtkVonjOn7zZ6K-",
                payloadAmount = "100000000",
            ),
            // #6 TRON -> ETH (source coin, TRON)
            SwapCase(
                name = "tron_to_eth",
                sourceAssetId = "tron/mainnet/coin",
                sourceDecimals = 6,
                sourceSymbol = "TRX",
                destAssetId = "eth/mainnet/coin",
                destDecimals = 18,
                destSymbol = "ETH",
                sourceAmount = "100000000",
                routeId = "409ac2d0-7fdd-47ab-93be-463cd9c31df5",
                buyAmount = "16698600000000000",
                minBuyAmount = "16698600000000000",
                priceImpactBps = -110,
                payloadTo = "TH4fqR5E3tzrAihoG9Do2Kt7qdFvi5rRmQ",
                payloadAmount = "100000000",
            ),
            // #7 USDT (trc20) -> ETH (source token, TRON)
            SwapCase(
                name = "usdt_trc20_to_eth",
                sourceAssetId = "tron/mainnet/trc20/TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t",
                sourceDecimals = 6,
                sourceSymbol = "USDT",
                destAssetId = "eth/mainnet/coin",
                destDecimals = 18,
                destSymbol = "ETH",
                sourceAmount = "20000000",
                routeId = "5960d7bf-198f-4522-b2c2-ccd51fdb57e2",
                buyAmount = "9740024378856589",
                minBuyAmount = "9447823647490891",
                priceImpactBps = -638,
                payloadTo = "TMkxBMf4sCsPYNXSAJTs7KkbvckY45R8hA",
                payloadAmount = "20000000",
            ),
        ).map { arrayOf(it) }
    }
}
