package com.tonapps.wallet.data.multichain.legacy

import android.net.Uri
import com.tonapps.blockchain.model.legacy.BalanceEntity
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.Wallet
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.icu.Coins
import com.tonapps.wallet.SigningWallet
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.data.token.entities.TokenRateEntity
import com.tonapps.wallet.requireHd
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.math.BigInteger

/**
 * The legacy side of the GRAM ↔ USDT TRC20 swap: which wallets it signs for, the pair it opens on,
 * the accounts it builds from legacy balances, and the TRON key bytes it signs with.
 */
class LegacySwapAccountsTest {

    // Eligibility

    @Test
    fun regularMainnetWalletOnV5R1WithTronIsEligible() {
        val swap = LegacySwapWallet.of(wallet(), TRON_ADDRESS, tronDisabled = false)

        assertEquals(Address.Type.TonV5R1, swap?.tonAddressType)
        assertEquals(TRON_ADDRESS, swap?.tronAddress)
        assertEquals(WALLET_ID, swap?.id)
        assertTrue(swap?.tonPublicKey.orEmpty().startsWith("0x"))
    }

    @Test
    fun v4R2WalletSignsAsV4R2() {
        val swap = LegacySwapWallet.of(wallet(version = WalletVersion.V4R2), TRON_ADDRESS, tronDisabled = false)

        assertEquals(Address.Type.TonV4R2, swap?.tonAddressType)
    }

    @Test
    fun walletsTheSwapCannotSignForAreNotEligible() {
        assertNull(LegacySwapWallet.of(wallet(type = WalletType.Watch), TRON_ADDRESS, tronDisabled = false))
        assertNull(LegacySwapWallet.of(wallet(type = WalletType.Ledger), TRON_ADDRESS, tronDisabled = false))
        assertNull(LegacySwapWallet.of(wallet(type = WalletType.Multichain), TRON_ADDRESS, tronDisabled = false))
        assertNull(LegacySwapWallet.of(wallet(type = WalletType.Testnet), TRON_ADDRESS, tronDisabled = false))
        assertNull(LegacySwapWallet.of(wallet(version = WalletVersion.V3R2), TRON_ADDRESS, tronDisabled = false))
        assertNull(LegacySwapWallet.of(wallet(version = WalletVersion.V5BETA), TRON_ADDRESS, tronDisabled = false))
    }

    @Test
    fun walletWithoutTronIsNotEligible() {
        assertNull(LegacySwapWallet.of(wallet(), tronAddress = null, tronDisabled = false))
        assertNull(LegacySwapWallet.of(wallet(), tronAddress = " ", tronDisabled = false))
        assertNull(LegacySwapWallet.of(wallet(), TRON_ADDRESS, tronDisabled = true))
    }

    // Pair

    @Test
    fun pairKeepsTheSideTheCallerOpened() {
        val pair = accounts().pair(sellAssetId = LegacySwapAssets.USDT_TRC20_ASSET_ID, buyAssetId = null)

        assertEquals(LegacySwapAssets.USDT_TRC20_ASSET_ID, pair.sell.id)
        assertEquals(LegacySwapAssets.TON_ASSET_ID, pair.buy.id)
    }

    @Test
    fun pairSellsTheOtherAssetWhenOnlyTheBuySideIsAsked() {
        val pair = accounts().pair(sellAssetId = null, buyAssetId = LegacySwapAssets.USDT_TRC20_ASSET_ID)

        assertEquals(LegacySwapAssets.TON_ASSET_ID, pair.sell.id)
        assertEquals(LegacySwapAssets.USDT_TRC20_ASSET_ID, pair.buy.id)
    }

    @Test
    fun pairSellsGramWhenNothingOrSomethingElseIsAsked() {
        val nothing = accounts().pair(sellAssetId = null, buyAssetId = null)
        val other = accounts().pair(sellAssetId = "eth/mainnet/coin", buyAssetId = null)

        assertEquals(LegacySwapAssets.TON_ASSET_ID, nothing.sell.id)
        assertEquals(LegacySwapAssets.USDT_TRC20_ASSET_ID, nothing.buy.id)
        assertEquals(LegacySwapAssets.TON_ASSET_ID, other.sell.id)
    }

    // Accounts

    @Test
    fun usdtAccountIsTheTronAddressWithTheBalanceInBaseUnits() {
        val usdt = position(TokenEntity.TRON_USDT, amount = "2", rate = "0.999")

        val account = eligible().account(LegacySwapAssets.USDT_TRC20_ASSET_ID, listOf(usdt), USD)

        assertEquals("2000000", account?.balance?.available)
        assertEquals(TRON_ADDRESS, account?.data?.displayAddress)
        assertEquals(Chain.Tron.Mainnet, account?.data?.chain)
        assertEquals("0.999", account?.rate?.price)
    }

    @Test
    fun gramAccountIsTheTonAddressOnItsContractInNano() {
        val gram = position(TokenEntity.TON, amount = "8.24", rate = "1.34")

        val account = eligible().account(LegacySwapAssets.TON_ASSET_ID, listOf(gram), USD)

        assertEquals("8240000000", account?.balance?.available)
        assertEquals(Chain.Ton.Mainnet, account?.data?.chain)
        assertEquals(Address.Type.TonV5R1, account?.data?.addressType)
        assertEquals(eligible().tonPublicKey, account?.data?.publicKey)
    }

    /** A refresh that fails keeps the cached balance instead of showing an empty wallet. */
    @Test
    fun failedRefreshFallsBackToTheCachedBalances() = runTest {
        val usdt = position(TokenEntity.TRON_USDT, amount = "2", rate = "0.999")
        val tokenRepository = mockk<TokenRepository> {
            coEvery { get(any(), any(), TonNetwork.MAINNET, refresh = true, tronAddress = TRON_ADDRESS) } returns null
            coEvery { getLocal(any(), any(), TonNetwork.MAINNET) } returns listOf(usdt)
        }
        val settingsRepository = mockk<SettingsRepository> {
            every { currency } returns USD
        }
        val accounts = LegacySwapAccountsImpl(
            api = mockk(),
            accountRepository = mockk(),
            tokenRepository = tokenRepository,
            settingsRepository = settingsRepository,
        )

        val account = accounts.findAccount(eligible(), LegacySwapAssets.USDT_TRC20_ASSET_ID, refresh = true)

        assertEquals("2000000", account?.balance?.available)
    }

    /** With neither a load nor a cache there is no balance to show, not a zero one. */
    @Test
    fun failedRefreshWithoutACacheHasNoAccount() = runTest {
        val tokenRepository = mockk<TokenRepository> {
            coEvery { get(any(), any(), TonNetwork.MAINNET, refresh = true, tronAddress = TRON_ADDRESS) } returns null
            coEvery { getLocal(any(), any(), TonNetwork.MAINNET) } returns emptyList()
        }
        val settingsRepository = mockk<SettingsRepository> {
            every { currency } returns USD
        }
        val accounts = LegacySwapAccountsImpl(
            api = mockk(),
            accountRepository = mockk(),
            tokenRepository = tokenRepository,
            settingsRepository = settingsRepository,
        )

        assertNull(accounts.findAccount(eligible(), LegacySwapAssets.USDT_TRC20_ASSET_ID, refresh = true))
    }

    @Test
    fun tokenMissingFromTheListHasNoBalanceAndNoRate() {
        val account = eligible().account(LegacySwapAssets.USDT_TRC20_ASSET_ID, emptyList(), USD)

        assertEquals("0", account?.balance?.available)
        assertNull(account?.rate)
    }

    @Test
    fun assetOutsideTheSwapHasNoAccount() {
        assertNull(eligible().account("eth/mainnet/coin", emptyList(), USD))
    }

    // Keys

    @Test
    fun keyOfThirtyTwoBytesIsTakenAsIs() {
        val bytes = ByteArray(32) { (it + 1).toByte() }

        assertArrayEquals(bytes, BigInteger(1, bytes).toKeyBytes())
    }

    @Test
    fun keyWithTheTopBitSetLosesTheSignByte() {
        val bytes = ByteArray(32) { 0xF0.toByte() }
        val key = BigInteger(1, bytes)

        assertEquals(33, key.toByteArray().size)
        assertArrayEquals(bytes, key.toKeyBytes())
    }

    @Test
    fun keyWithALeadingZeroByteIsLeftPadded() {
        val bytes = ByteArray(32) { 0x11 }.also { it[0] = 0 }
        val key = BigInteger(1, bytes)

        assertEquals(31, key.toByteArray().size)
        assertArrayEquals(bytes, key.toKeyBytes())
    }

    @Test
    fun rawKeysSignOnlyForTheirChainsAndAreNoHdWallet() {
        val keys = SigningWallet.Keys(mapOf(Chain.Ton.Mainnet to ByteArray(32) { 1 }))

        assertThrows(IllegalStateException::class.java) { keys.getPrivateKey(Chain.Tron.Mainnet) }
        assertThrows(IllegalStateException::class.java) { keys.requireHd() }
    }

    private fun wallet(
        type: WalletType = WalletType.Default,
        version: WalletVersion = WalletVersion.V5R1,
    ) = WalletEntity(
        id = WALLET_ID,
        publicKey = EmptyPrivateKeyEd25519.publicKey(),
        type = type,
        version = version,
        label = Wallet.Label(accountName = "legacy_wallet", emoji = "", color = 0),
        initialized = true,
    )

    private fun eligible() = requireNotNull(LegacySwapWallet.of(wallet(), TRON_ADDRESS, tronDisabled = false))

    private fun accounts() = LegacySwapAccountsImpl(
        api = mockk(),
        accountRepository = mockk(),
        tokenRepository = mockk(),
        settingsRepository = mockk(),
    )

    private fun position(token: TokenEntity, amount: String, rate: String): AccountTokenEntity {
        val value = Coins.of(java.math.BigDecimal(amount), token.decimals)
        return AccountTokenEntity(
            balance = BalanceEntity(token = token, value = value, walletAddress = ""),
            fiatRate = TokenRateEntity(
                currency = USD,
                fiat = value,
                rate = Coins.of(java.math.BigDecimal(rate), token.decimals),
                rateDiff24h = "",
            ),
        )
    }

    companion object {
        private const val WALLET_ID = "legacy"
        private const val TRON_ADDRESS = "TPu5KzZ1dyKx22gyUiwVNAXUk2r6vRbRRP"
        private val USD by lazy { WalletCurrency.USD }

        // TokenEntity builds its icon URIs on class load, and android.net.Uri is a stub on the JVM.
        @BeforeClass
        @JvmStatic
        fun stubUri() {
            mockkConstructor(Uri.Builder::class)
            every { anyConstructed<Uri.Builder>().scheme(any()) } answers { self as Uri.Builder }
            every { anyConstructed<Uri.Builder>().path(any()) } answers { self as Uri.Builder }
            every { anyConstructed<Uri.Builder>().build() } returns mockk(relaxed = true)
        }
    }
}