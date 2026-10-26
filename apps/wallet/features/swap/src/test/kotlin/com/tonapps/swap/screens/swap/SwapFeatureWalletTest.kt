package com.tonapps.swap.screens.swap

import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.swap.screens.swap.SwapFixtures.ETH_ASSET_ID
import com.tonapps.swap.screens.swap.SwapFixtures.TON_ASSET_ID
import com.tonapps.swap.screens.swap.SwapFixtures.USDT_TON_ASSET_ID
import com.tonapps.swap.screens.swap.SwapFixtures.USDT_TRON_ASSET_ID
import com.tonapps.swap.screens.swap.SwapFixtures.WALLET_ID
import com.tonapps.wallet.data.multichain.exchange.SwapConfig
import com.tonapps.wallet.data.multichain.exchange.SwapDefaultPair
import com.tonapps.wallet.data.multichain.exchange.SwapRepository
import com.tonapps.wallet.data.multichain.legacy.LegacySwapWallet
import com.tonapps.wallet.data.raffle.entities.RaffleEntity
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.screenTest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.OffsetDateTime

/**
 * The wallet node every loader hangs off: which wallet the screen swaps for, and whether it is a
 * multichain wallet or a legacy one that must never reach the multichain backend.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwapFeatureWalletTest : BaseUnitTest() {

    private lateinit var env: SwapTestEnv

    @Before
    fun setUp() {
        env = SwapTestEnv(context, timeSource, features)
    }

    /** An unknown wallet may be a legacy one, so the pair starts locked and opens once it is known. */
    @Test
    fun pairIsLockedUntilTheWalletIsKnown() {
        var lockedOnOpen: Boolean? = null

        screenTest(
            open = {
                val feature = env.feature()
                lockedOnOpen = feature.isPairLocked.value
                SwapScreen(this, env, feature)
            },
        ) {
            assertEquals(true, lockedOnOpen)
            assertFalse(feature.isPairLocked.value)
        }
    }

    /** A pick that lands before the lookup answered must not replace a legacy wallet's fixed pair. */
    @Test
    fun pickBeforeTheWalletIsKnownNeverReachesALegacyPair() {
        env.legacyWallet = legacyWallet

        screenTest(
            open = {
                val feature = env.feature()
                feature.sendAction(SwapAction.Asset.SelectBuy(SwapFixtures.ethAsset))
                SwapScreen(this, env, feature)
            },
        ) {
            val opened = now()

            assertEquals(SwapInitState.Ready, opened.initState)
            assertEquals(TON_ASSET_ID, opened.sellAsset?.id)
            assertEquals(USDT_TRON_ASSET_ID, opened.buyAsset?.id)
            assertTrue(env.accountReads.none { it == ETH_ASSET_ID })
        }
    }

    /** Config, accounts, quotes and the raffle: a legacy wallet asks the multichain backend for none. */
    @Test
    fun legacyWalletNeverReachesTheMultichainBackend() {
        env.legacyWallet = legacyWallet
        env.raffles = listOf(raffle(id = "offer", status = RaffleEntity.Status.NotJoined, zeroFeeEndsAt = tomorrow()))

        swapTest(env) {
            after { sendAction(SwapAction.Amount.SetSell("1")) }
            after { env.refreshTrigger.tryEmit(Unit) }

            assertTrue(env.configRequests.isEmpty())
            assertTrue(env.forcedAccountReads.isEmpty())
            assertEquals(env.accountReads.toList(), env.legacyAccountReads.toList())
            assertEquals(setOf(TON_ASSET_ID, USDT_TRON_ASSET_ID), env.legacyAccountReads.toSet())
            assertTrue(env.quoteWalletIds.all { it == null })
            assertTrue(env.raffleRequests.isEmpty())
            assertNull(feature.zeroFeeRaffleId.value)
        }
    }

    /**
     * A legacy wallet the swap cannot sign for (TRON switched off since the entry point checked) is no
     * multichain wallet: the screen fails and asks the multichain backend for nothing.
     */
    @Test
    fun legacyWalletTheSwapCannotSignForFailsTheScreen() {
        env.isMultichainWallet = false

        swapTest(env, awaitReady = false) {
            val opened = now()

            assertEquals(SwapInitState.Error, opened.initState)
            assertTrue(feature.isPairLocked.value)
            assertTrue(env.configRequests.isEmpty())
            assertTrue(env.accountReads.isEmpty())
            assertTrue(env.raffleRequests.isEmpty())
        }
    }

    /** The lookup runs once for the screen; nothing the user does sends it again. */
    @Test
    fun walletIsLookedUpOnce() = swapTest(env) {
        after { sendAction(SwapAction.Amount.SetSell("1")) }
        after { sendAction(SwapAction.Asset.Swap) }
        after { sendAction(SwapAction.Asset.SelectBuy(SwapFixtures.ethAsset)) }
        after { env.refreshTrigger.tryEmit(Unit) }

        assertEquals(listOf(WALLET_ID), env.legacyLookups.toList())
    }

    /** A lookup that failed cannot tell the two kinds apart, so the screen fails and Retry asks again. */
    @Test
    fun failedLookupFailsTheScreenAndRetryLooksAgain() {
        var fails = true
        env.legacyLookup = {
            if (fails) {
                throw IllegalStateException("wallet store is unavailable")
            }
            null
        }

        swapTest(env, awaitReady = false) {
            val broken = now()
            assertEquals(SwapInitState.Error, broken.initState)
            assertTrue(feature.isPairLocked.value)
            assertTrue(env.configRequests.isEmpty())
            assertTrue(env.accountReads.isEmpty())

            fails = false
            val recovered = after { sendAction(SwapAction.Retry) }

            assertEquals(SwapInitState.Ready, recovered.initState)
            assertEquals(2, env.legacyLookups.size)
            assertFalse(feature.isPairLocked.value)
            assertTrue(recovered.hasAccounts)
        }
    }

    /** A config that failed for a wallet that did not is read again on Retry, for that same wallet. */
    @Test
    fun configWithoutAPairFailsTheScreenAndRetryReadsItAgain() {
        var pair = SwapDefaultPair(source = null, destination = null)
        env.swapRepo = mockk<SwapRepository>(relaxed = true).also { repo ->
            every { repo.swapCompleted } returns MutableSharedFlow()
            coEvery { repo.getConfig(any(), any(), any()) } answers {
                SwapConfig(defaultPair = pair, slippagePerChain = emptyMap())
            }
        }

        swapTest(env, awaitReady = false) {
            assertEquals(SwapInitState.Error, now().initState)
            assertFalse(feature.isPairLocked.value)

            pair = SwapDefaultPair(source = SwapFixtures.tonAsset, destination = SwapFixtures.usdtTonAsset)
            val recovered = after { sendAction(SwapAction.Retry) }

            assertEquals(SwapInitState.Ready, recovered.initState)
            assertEquals(TON_ASSET_ID, recovered.sellAccount?.asset?.id)
            assertEquals(USDT_TON_ASSET_ID, recovered.buyAccount?.asset?.id)
        }
    }

    /** A Retry that finds the same wallet and config is no change: accounts and quote stay as they are. */
    @Test
    fun retryThatChangesNothingKeepsTheQuote() = swapTest(env) {
        val quoted = after { sendAction(SwapAction.Amount.SetSell("1")) }
        env.forcedAccountReads.clear()

        val retried = after { sendAction(SwapAction.Retry) }

        assertEquals(2, env.legacyLookups.size)
        assertTrue(env.forcedAccountReads.isEmpty())
        assertEquals(1, quoteRequests.size)
        assertEquals(quoted.quote, retried.quote)
        assertEquals(quoted.selectedSlippageBps, retried.selectedSlippageBps)
    }

    @Test
    fun noSelectedWalletIsNeverLookedUp() {
        env.walletIdAnswer = { null }

        swapTest(env, awaitReady = false) {
            assertEquals(SwapInitState.Error, now().initState)
            assertTrue(env.legacyLookups.isEmpty())
            assertTrue(env.accountReads.isEmpty())
        }
    }

    /**
     * Config only fills the sides that are empty: a reload that suggests another pair changes
     * neither the side the user picked nor the one the first config filled.
     */
    @Test
    fun reloadedConfigNeverReplacesThePair() {
        var configReads = 0
        env.swapRepo = mockk<SwapRepository>(relaxed = true).also { repo ->
            every { repo.swapCompleted } returns MutableSharedFlow()
            coEvery { repo.getConfig(any(), any(), any()) } answers {
                configReads++
                val pair = when (configReads) {
                    1 -> SwapDefaultPair(source = SwapFixtures.tonAsset, destination = SwapFixtures.usdtTonAsset)
                    else -> SwapDefaultPair(source = SwapFixtures.btcAsset, destination = SwapFixtures.trxAsset)
                }
                SwapConfig(defaultPair = pair, slippagePerChain = emptyMap())
            }
        }

        swapTest(env) {
            after { sendAction(SwapAction.Asset.SelectSell(SwapFixtures.ethAsset)) }

            val reloaded = after { sendAction(SwapAction.Retry) }

            assertEquals(2, configReads)
            assertEquals(ETH_ASSET_ID, reloaded.sellAsset?.id)
            assertEquals(USDT_TON_ASSET_ID, reloaded.buyAsset?.id)
            assertEquals(ETH_ASSET_ID, reloaded.sellAccount?.asset?.id)
        }
    }

    /** The perk line is the zero-fee offer the wallet has not joined yet, while its window is open. */
    @Test
    fun raffleIdIsTheOpenZeroFeeOffer() {
        env.raffles = listOf(
            raffle(id = "joined", status = RaffleEntity.Status.Joined, zeroFeeEndsAt = tomorrow()),
            raffle(id = "expired", status = RaffleEntity.Status.NotJoined, zeroFeeEndsAt = yesterday()),
            raffle(id = "no-window", status = RaffleEntity.Status.NotJoined, zeroFeeEndsAt = null),
            raffle(id = "offer", status = RaffleEntity.Status.NotJoined, zeroFeeEndsAt = tomorrow()),
        )

        swapTest(env) {
            assertEquals("offer", feature.zeroFeeRaffleId.value)
            assertEquals(listOf(WALLET_ID), env.raffleRequests.toList())
        }
    }

    @Test
    fun noOpenOfferMeansNoPerkLine() {
        env.raffles = listOf(
            raffle(id = "joined", status = RaffleEntity.Status.Joined, zeroFeeEndsAt = tomorrow()),
            raffle(id = "expired", status = RaffleEntity.Status.NotJoined, zeroFeeEndsAt = yesterday()),
        )

        swapTest(env) {
            assertNull(feature.zeroFeeRaffleId.value)
        }
    }

    private fun raffle(
        id: String,
        status: RaffleEntity.Status,
        zeroFeeEndsAt: OffsetDateTime?,
    ): RaffleEntity = mockk {
        every { this@mockk.id } returns id
        every { this@mockk.status } returns status
        every { progress } returns RaffleEntity.Progress(
            ticketsTotal = 0,
            history = emptyList(),
            zeroFeeEndsAt = zeroFeeEndsAt,
        )
    }

    // The raffle window is checked against the real clock, not the test's virtual one.
    private fun tomorrow(): OffsetDateTime = OffsetDateTime.now().plusDays(1)

    private fun yesterday(): OffsetDateTime = OffsetDateTime.now().minusDays(1)

    private companion object {
        val legacyWallet = LegacySwapWallet(
            id = WALLET_ID,
            accountId = "0:legacy",
            name = "legacy_wallet",
            emoji = "",
            tonAddress = SwapFixtures.TON_ADDRESS,
            tonPublicKey = "0x00",
            tonAddressType = Address.Type.TonV5R1,
            tronAddress = SwapFixtures.TRON_ADDRESS,
        )
    }
}