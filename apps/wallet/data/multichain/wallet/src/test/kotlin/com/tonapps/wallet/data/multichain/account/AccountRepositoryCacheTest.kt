package com.tonapps.wallet.data.multichain.account

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.cache.JsonCacheRepository
import com.tonapps.wallet.data.cache.JsonResponseCache
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.multichain.wallet.McWalletType
import com.tonapps.wallet.data.settings.SettingsRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.walletapi.models.Asset
import io.walletapi.models.AssetInfo
import io.walletapi.models.AssetPrice
import io.walletapi.models.GetWalletAssets200Response
import io.walletapi.models.WalletPerps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.CountDownLatch

class AccountRepositoryCacheTest {

    private val tonAccount = AccountEntity(
        walletId = WALLET_ID,
        network = Chain.Ton.Mainnet.network.type.id,
        mode = Chain.Ton.Mainnet.network.mode.id,
        displayAddress = "0:27bafbc6d4a559505c397864e9e2a68c69ffbea37c61f34b54d114231d3906f3",
        publicKey = "",
        segwitPublicKey = "",
        addressType = Address.Type.TonV5R1,
        id = "ton-v5r1",
    )

    private val api = mockk<API>()
    private val jsonCacheRepository = mockk<JsonCacheRepository>()
    private val settings = mockk<SettingsRepository>()

    private val repository = AccountRepositoryImpl(
        accountDao = mockk { coEvery { getAccountsByWalletId(WALLET_ID) } returns listOf(tonAccount) },
        walletDao = mockk {
            coEvery { getWallet(WALLET_ID) } returns McWalletEntity(
                credentialId = "credential",
                name = "Wallet",
                id = WALLET_ID,
                color = 0,
                type = McWalletType.Multicoin,
            )
        },
        credentialDao = mockk(),
        walletBundleDao = mockk(),
        jsonCacheRepository = jsonCacheRepository,
        appKeyRepository = mockk(),
        deviceRepository = mockk(),
        provider = mockk(),
        settings = settings,
        api = api,
        perpetualRepository = mockk(),
    )

    init {
        every { settings.currency.code } returns CURRENCY
        every { settings.isSafeModeEnabled(any(), any()) } returns false
        every { settings.hideDustAssets } returns false
        coEvery { jsonCacheRepository.save(any(), any(), any(), any()) } just Runs
    }

    @Test
    fun `disk snapshot read after a network fetch keeps the network balance`() = runTest {
        givenNetworkBalance(FRESH_BALANCE)
        givenDiskBalance(STALE_BALANCE)

        repository.fetchAccounts(WALLET_ID)
        repository.getCachedAccounts(WALLET_ID, capabilities = listOf(AssetCapability.Swap))

        assertEquals(FRESH_BALANCE, repository.findAccount(WALLET_ID, TON_ASSET_ID)?.balance?.available)
    }

    @Test
    fun `disk snapshot fills an account not fetched from network`() = runTest {
        givenDiskBalance(STALE_BALANCE)

        repository.getCachedAccounts(WALLET_ID)

        assertEquals(STALE_BALANCE, repository.findAccount(WALLET_ID, TON_ASSET_ID)?.balance?.available)
    }

    @Test
    fun `network fetch replaces an account filled from disk`() = runTest {
        givenDiskBalance(STALE_BALANCE)
        givenNetworkBalance(FRESH_BALANCE)

        repository.getCachedAccounts(WALLET_ID)
        repository.fetchAccounts(WALLET_ID)

        assertEquals(FRESH_BALANCE, repository.findAccount(WALLET_ID, TON_ASSET_ID)?.balance?.available)
    }

    @Test
    fun `cached page shows accounts fetched this session instead of the snapshot rows`() = runTest {
        givenNetworkBalance(FRESH_BALANCE)
        givenDiskBalance(STALE_BALANCE)

        repository.fetchAccounts(WALLET_ID)
        val page = repository.getCachedAccounts(
            WALLET_ID,
            capabilities = listOf(AssetCapability.Swap),
            preferFetchedAccounts = true,
        )
        val snapshot = repository.getCachedAccounts(WALLET_ID, capabilities = listOf(AssetCapability.Swap))

        assertEquals(FRESH_BALANCE, page?.accounts?.single()?.balance?.available)
        assertEquals(STALE_BALANCE, snapshot?.accounts?.single()?.balance?.available)
    }

    @Test
    fun `list response that started before a single-asset read keeps and shows the newer balance`() = runBlocking {
        val listStarted = CountDownLatch(1)
        val releaseList = CountDownLatch(1)
        givenNetworkAnswer {
            listStarted.countDown()
            releaseList.await()
            assetsResponse(STALE_BALANCE)
        }
        every {
            api.multichain.wallets.getWalletAsset(
                walletId = WALLET_ID,
                assetId = TON_ASSET_ID,
                currencies = any(),
                network = any(),
                xWalletId = any(),
            )
        } returns tonAsset(FRESH_BALANCE)

        val list = async(Dispatchers.IO) { repository.fetchAccounts(WALLET_ID) }
        listStarted.await()
        repository.findAccount(WALLET_ID, TON_ASSET_ID, forceRefresh = true)
        releaseList.countDown()
        val page = list.await()

        assertEquals(FRESH_BALANCE, page.accounts.single().balance.available)
        assertEquals(FRESH_BALANCE, repository.findAccount(WALLET_ID, TON_ASSET_ID)?.balance?.available)
    }

    @Test
    fun `forced single-asset read falls back to the cached account when the network fails`() = runTest {
        givenDiskBalance(STALE_BALANCE)
        givenSingleAssetFailure()

        repository.getCachedAccounts(WALLET_ID)

        assertEquals(
            STALE_BALANCE,
            repository.findAccount(WALLET_ID, TON_ASSET_ID, forceRefresh = true)?.balance?.available,
        )
    }

    @Test
    fun `single-asset fetch reports the network failure instead of the cached account`() {
        givenDiskBalance(STALE_BALANCE)
        givenSingleAssetFailure()

        runBlocking { repository.getCachedAccounts(WALLET_ID) }

        assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.fetchAccount(WALLET_ID, TON_ASSET_ID) }
        }
    }

    @Test
    fun `single-asset read answered for the other TON contract takes the primary contract's balance`() = runTest {
        every {
            api.multichain.wallets.getWalletAsset(
                walletId = WALLET_ID,
                assetId = TON_ASSET_ID,
                currencies = any(),
                network = any(),
                xWalletId = any(),
            )
        } returns tonAsset(STALE_BALANCE, Address.Type.TonV4R2)
        givenNetworkAnswer {
            GetWalletAssets200Response(
                assets = listOf(tonAsset(STALE_BALANCE, Address.Type.TonV4R2), tonAsset(FRESH_BALANCE)),
                fiatPrice = mapOf(CURRENCY to "0"),
                nextCursor = "",
            )
        }

        val account = repository.fetchAccount(WALLET_ID, TON_ASSET_ID)

        assertEquals(tonAccount.id, account?.data?.id)
        assertEquals(FRESH_BALANCE, account?.balance?.available)
    }

    @Test
    fun `primary TON contract missing from the list holds none of the asset`() = runTest {
        every {
            api.multichain.wallets.getWalletAsset(
                walletId = WALLET_ID,
                assetId = TON_ASSET_ID,
                currencies = any(),
                network = any(),
                xWalletId = any(),
            )
        } returns tonAsset(STALE_BALANCE, Address.Type.TonV4R2)
        givenNetworkAnswer {
            GetWalletAssets200Response(
                assets = listOf(tonAsset(STALE_BALANCE, Address.Type.TonV4R2)),
                fiatPrice = mapOf(CURRENCY to "0"),
                nextCursor = "",
            )
        }

        assertEquals("0", repository.fetchAccount(WALLET_ID, TON_ASSET_ID)?.balance?.available)
    }

    @Test
    fun `perps block with an amount in the asked currency is the wallet perps total`() = runTest {
        val perps = WalletPerps(WalletPerps.Status.ok, equity = "300.12", fiatPrice = mapOf(CURRENCY to "300.12"))
        givenNetworkAnswer { assetsResponse(FRESH_BALANCE, perps) }

        val page = repository.fetchAccounts(WALLET_ID)

        assertEquals(WalletPerpsTotal.Amount(BigDecimal.parseString("300.12")), page.perpsTotal)
    }

    @Test
    fun `wallet without a perps account has an empty perps total`() = runTest {
        givenNetworkAnswer { assetsResponse(FRESH_BALANCE, WalletPerps(WalletPerps.Status.none)) }

        assertEquals(WalletPerpsTotal.Empty, repository.fetchAccounts(WALLET_ID).perpsTotal)
    }

    @Test
    fun `unreadable or missing perps block leaves the perps total unavailable`() = runTest {
        givenNetworkAnswer { assetsResponse(FRESH_BALANCE, WalletPerps(WalletPerps.Status.unavailable)) }
        assertEquals(WalletPerpsTotal.Unavailable, repository.fetchAccounts(WALLET_ID).perpsTotal)

        givenNetworkAnswer { assetsResponse(FRESH_BALANCE) }
        assertEquals(WalletPerpsTotal.Unavailable, repository.fetchAccounts(WALLET_ID).perpsTotal)
    }

    @Test
    fun `wallet totals read the total and the perps without saving the assets cache`() = runTest {
        val perps = WalletPerps(WalletPerps.Status.ok, equity = "5.11", fiatPrice = mapOf(CURRENCY to "5.11"))
        givenNetworkAnswer { assetsResponse(FRESH_BALANCE, perps).copy(fiatPrice = mapOf(CURRENCY to "81.6")) }

        val totals = repository.fetchWalletTotals(WALLET_ID, CURRENCY)

        assertEquals(BigDecimal.parseString("81.6"), totals.total)
        assertEquals(WalletPerpsTotal.Amount(BigDecimal.parseString("5.11")), totals.perpsTotal)
        coVerify(exactly = 0) { jsonCacheRepository.save(any(), any(), any(), any()) }
    }

    private fun givenSingleAssetFailure() {
        every {
            api.multichain.wallets.getWalletAsset(
                walletId = WALLET_ID,
                assetId = TON_ASSET_ID,
                currencies = any(),
                network = any(),
                xWalletId = any(),
            )
        } throws IllegalStateException("offline")
    }

    private fun givenNetworkBalance(balance: String) {
        givenNetworkAnswer { assetsResponse(balance) }
    }

    private fun givenNetworkAnswer(answer: () -> GetWalletAssets200Response) {
        every {
            api.multichain.wallets.getWalletAssets(
                walletId = WALLET_ID,
                currencies = any(),
                assetIds = any(),
                capabilities = any(),
                chain = any(),
                network = any(),
                search = any(),
                availableOnly = any(),
                showHidden = any(),
                showAll = any(),
                verifiedOnly = any(),
                hideDust = any(),
                limit = any(),
                cursor = any(),
                xWalletId = any(),
            )
        } answers { answer() }
    }

    private fun givenDiskBalance(balance: String) {
        coEvery { jsonCacheRepository.getJson(any(), any(), WALLET_ID) } returns JsonResponseCache.encode(assetsResponse(balance))
    }

    private fun assetsResponse(balance: String, perps: WalletPerps? = null) = GetWalletAssets200Response(
        assets = listOf(tonAsset(balance)),
        fiatPrice = mapOf(CURRENCY to "0"),
        nextCursor = "",
        perps = perps,
    )

    private fun tonAsset(balance: String, type: Address.Type = Address.Type.TonV5R1) = Asset(
        asset = AssetInfo(
            assetId = TON_ASSET_ID,
            name = "Gram",
            symbol = "GRAM",
            decimals = 9,
            image = "",
            verification = AssetInfo.Verification.whitelist,
        ),
        address = tonAccount.displayAddress,
        price = AssetPrice(),
        isHidden = false,
        balance = balance,
        type = type.id,
    )

    private companion object {
        const val WALLET_ID = "wallet"
        const val CURRENCY = "USD"
        const val TON_ASSET_ID = "ton/mainnet/coin"
        const val FRESH_BALANCE = "9646914362"
        const val STALE_BALANCE = "2255441000"
    }
}
