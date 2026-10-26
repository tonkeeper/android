package com.tonapps.deposit.multicoin.screens.confirm.fee

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.legacy.BalanceEntity
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.contract.WalletFeature
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.Authorization
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.api.entity.EmulateWithBatteryResult
import com.tonapps.wallet.api.tron.TronApi
import com.tonapps.wallet.api.tron.entity.TronEstimationEntity
import com.tonapps.wallet.api.tron.entity.TronResourcesEntity
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.battery.entity.BatteryConfigEntity
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.entities.PreferredFeeMethod
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.tonapi.models.MessageConsequences
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The battery a TON or TRON transfer and swap is offered: priced whatever the charges, so a battery
 * that ran out still offers its recharge, and listed unpriced in a swap whose estimate came back
 * without a price.
 */
class TxFeeBuilderTonTest : BaseUnitTest() {

    private val api = mockk<API>()
    private val tronApi = mockk<TronApi>()
    private val batteryRepository = mockk<BatteryRepository>()
    private val unifiedAccountRepository = mockk<UnifiedAccountRepository>()
    private val accountRepo = mockk<McAccountRepository>()
    private val settings = mockk<SettingsRepository>()
    private val tokenRepository = mockk<TokenRepository>()
    private val wallet = mockk<WalletEntity>()

    private val builder = TxFeeBuilder(
        api = api,
        batteryRepository = batteryRepository,
        unifiedAccountRepository = unifiedAccountRepository,
        accountRepo = accountRepo,
        settingsRepository = settings,
        tokenRepository = tokenRepository,
        sender = mockk(relaxed = true),
        evmGasless = mockk(),
    )

    private val ton by lazy { testAccount(TestAssets.ton, balance = "1000") }
    private val usdt by lazy { testAccount(TestAssets.usdtTon, balance = "5000000") }
    private val trx by lazy { testAccount(TestAssets.trx, balance = "1000") }
    private val usdtTron by lazy { testAccount(TestAssets.usdtTron, balance = "5000000") }

    private val tonFee by lazy { Fee.Value(ton.asset.value, BigInteger.parseString(TON_FEE)) }
    private val trxFee by lazy { Fee.Value(trx.asset.value, BigInteger.parseString("1100000")) }

    private var charges = 0
    private var supported = true
    private var allowed = false

    // The TON the battery is charged for the message, as the emulation reports it.
    private var extra = -TON_FEE.toLong()

    @Before
    fun setUp() {
        every { wallet.id } returns TestWallet.ID
        every { wallet.network } returns TonNetwork.MAINNET
        every { wallet.accountId } returns TestWallet.TON_ADDRESS
        every { wallet.isSupportedFeature(WalletFeature.GASLESS) } returns false
        every { api.getConfig(any()) } returns mockk<ConfigEntity>(relaxed = true)
        every { api.tron } returns tronApi
        coEvery { unifiedAccountRepository.getTonWalletById(TestWallet.ID) } returns wallet
        coEvery { unifiedAccountRepository.getAuthBy(TestWallet.ID) } returns Authorization(
            deviceToken = "device",
            walletId = TestWallet.ID,
        )
        coEvery { batteryRepository.getCharges(any(), any()) } answers { charges }
        coEvery { batteryRepository.getConfig(any(), any()) } returns mockk<BatteryConfigEntity>(relaxed = true) {
            every { chargeCost } returns CHARGE_COST
            every { excessesAddress } returns mockk(relaxed = true)
            every { rechargeMethods } returns emptyList()
        }
        coEvery { batteryRepository.emulate(any(), any(), any(), any()) } answers {
            EmulateWithBatteryResult(
                consequences = mockk<MessageConsequences> { every { event.extra } returns extra },
                withBattery = supported && allowed,
                excess = null,
                supportedByBattery = supported,
            )
        }
        coEvery { accountRepo.findAccount(any(), any(), any(), any()) } returns null
        coEvery { tokenRepository.getJettonBalance(any(), any(), any()) } returns mockk<BalanceEntity> {
            every { isRequestMinting } returns false
            every { customPayloadApiUri } returns null
        }
        every { settings.getPreferredFeeMethod(any()) } returns PreferredFeeMethod.UNSPECIFIED
        every { settings.batteryIsEnabledTx(any(), any()) } returns true
        every { settings.isSafeModeEnabled(any(), any()) } returns false
    }

    // region TON jetton transfer

    /** The battery is priced with no charges at all, so the shortage dialog offers to recharge it. */
    @Test
    fun aJettonTransferWithNoChargesKeepsTheBatteryForARecharge() = runTest {
        val state = buildJetton()

        assertTrue("an unpayable battery is not listed on a transfer", state.options.isEmpty())
        assertEquals(Asset.Battery.ID, state.shortBattery?.id)
        assertTrue(state.batteryRechargeable)
        coVerify { batteryRepository.emulate(wallet, any(), forceRelayer = true, safeModeEnabled = false) }
    }

    @Test
    fun aChargedJettonTransferIsPaidWithTheBattery() = runTest {
        charges = 1000
        allowed = true

        val state = buildJetton()

        assertEquals(listOf(Asset.Battery.ID), state.options.map { it.id })
        assertTrue(TxFeeLogic.isSufficient(state.options.single()))
        assertNull(state.shortBattery)
        assertFalse(state.batteryRechargeable)
    }

    /** A message the battery never relays is not worth recharging for. */
    @Test
    fun aJettonTransferTheBatteryDoesNotSupportHasNoBattery() = runTest {
        supported = false

        val state = buildJetton()

        assertTrue(state.options.isEmpty())
        assertNull(state.shortBattery)
        assertFalse(state.batteryRechargeable)
    }

    /** With the charges in hand, a refusal is the battery's own reason: a recharge changes nothing. */
    @Test
    fun aJettonTransferTheBatteryRefusesDespiteTheChargesHasNoBattery() = runTest {
        charges = 1000

        val state = buildJetton()

        assertTrue(state.options.isEmpty())
        assertNull(state.shortBattery)
        assertFalse(state.batteryRechargeable)
    }

    /** Only a swap lists an unpriced battery. */
    @Test
    fun anUnpricedJettonTransferHasNoBattery() = runTest {
        extra = 0

        val state = buildJetton()

        assertNull(state.shortBattery)
        assertFalse(state.batteryRechargeable)
    }

    // endregion

    // region TON swap

    @Test
    fun aTonSwapWithNoChargesListsTheBatteryToRecharge() = runTest {
        val state = buildTonSwap()

        val battery = state.options.first()
        assertTrue(battery.isBattery)
        assertFalse(battery.isUnpriced)
        assertFalse(TxFeeLogic.isSufficient(battery))
        assertTrue(state.batteryRechargeable)
    }

    /** An estimate without a price still lists the battery, so a wallet without charges can recharge. */
    @Test
    fun aTonSwapPricedAtNothingListsAnUnpricedBattery() = runTest {
        extra = 0

        val state = buildTonSwap()

        val battery = state.options.first()
        assertTrue(battery.isUnpriced)
        assertFalse(TxFeeLogic.isSufficient(battery))
        assertTrue(state.batteryRechargeable)
    }

    /** With charges in hand a recharge is not what is missing: no "Recharge" for 2705 charges. */
    @Test
    fun aTonSwapPricedAtNothingWithChargesHasNoBattery() = runTest {
        charges = 2705
        extra = 0

        val state = buildTonSwap()

        assertTrue(state.options.isEmpty())
        assertFalse(state.batteryRechargeable)
    }

    @Test
    fun aTronSwapPricedAtNothingWithChargesHasNoBattery() = runTest {
        charges = 2705
        stubTronEstimate(charges = 0)

        val state = buildTronSwap()

        assertTrue(state.options.none { it.isBattery })
        assertFalse(state.batteryRechargeable)
    }

    @Test
    fun aTonSwapTheBatteryDoesNotSupportKeepsTheChainFee() = runTest {
        supported = false

        val state = buildTonSwap()

        assertTrue(state.options.isEmpty())
        assertFalse(state.batteryRechargeable)
    }

    @Test
    fun aTonSwapTheBatteryRefusesDespiteTheChargesKeepsTheChainFee() = runTest {
        charges = 1000

        val state = buildTonSwap()

        assertTrue(state.options.isEmpty())
        assertFalse(state.batteryRechargeable)
    }

    // endregion

    // region TRON swap

    @Test
    fun aTronSwapPricedAtNoChargesListsAnUnpricedBattery() = runTest {
        stubTronEstimate(charges = 0)

        val state = buildTronSwap()

        val battery = state.options.first()
        assertTrue(battery.isUnpriced)
        assertTrue(state.batteryRechargeable)
    }

    @Test
    fun aPricedTronSwapListsTheBatteryAtItsCharges() = runTest {
        stubTronEstimate(charges = 7)

        val state = buildTronSwap()

        val battery = state.options.first()
        assertTrue(battery.isBattery)
        assertEquals(BigInteger.fromInt(7), battery.fee.amount)
        assertTrue("no charges to pay the 7 with", state.batteryRechargeable)
    }

    // endregion

    private fun stubTronEstimate(charges: Int) {
        every { tronApi.estimateTransferResources(any()) } returns TronResourcesEntity(
            energy = 64_285,
            bandwidth = 345,
        )
        every { tronApi.estimateBatteryCharges(any(), any(), any()) } returns TronEstimationEntity.Charges(
            charges = charges,
            estimated = mockk(relaxed = true),
        )
    }

    private suspend fun buildJetton(): TxFeeState = builder.build(
        account = usdt,
        energy = ton,
        walletId = TestWallet.ID,
        amount = BigInteger.fromInt(1_000_000),
        to = TestWallet.TON_ADDRESS,
        comment = null,
        estimated = tonFee,
        shrinkToFit = false,
    )

    private suspend fun buildTonSwap(): TxFeeState = builder.buildSwap(
        account = ton,
        energy = ton,
        walletId = TestWallet.ID,
        transactions = listOf(swap(ton, Chain.Ton.Mainnet, TestWallet.TON_ADDRESS, data = SWAP_BODY)),
        approvals = emptyList(),
        estimated = tonFee,
        attachedEnergy = BigInteger.ZERO,
        shrinkToFit = false,
    )

    // A USD₮ TRC20 swap: the TRX one reads TokenEntity, which a JVM test cannot initialise.
    private suspend fun buildTronSwap(): TxFeeState = builder.buildSwap(
        account = usdtTron,
        energy = trx,
        walletId = TestWallet.ID,
        transactions = listOf(swap(usdtTron, Chain.Tron.Mainnet, TestWallet.TRON_ADDRESS, data = null)),
        approvals = emptyList(),
        estimated = trxFee,
        attachedEnergy = BigInteger.ZERO,
        shrinkToFit = false,
    )

    private fun swap(account: AccountWithDetails, chain: Chain, to: String, data: String?) = Transaction.Swap(
        account = account.value,
        amount = BigInteger.fromInt(100),
        destination = account.value,
        to = Address.force(to, chain),
        isMax = false,
        data = data,
    )

    private companion object {
        const val CHARGE_COST = "0.0026"
        const val TON_FEE = "5000000"
        const val SWAP_BODY = "te6cckEBAQEAAgAAAEysuc0="
    }
}
