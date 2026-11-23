package com.tonapps.deposit.multicoin.domain

import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.deposit.data.ExchangeRepository
import com.tonapps.deposit.multicoin.data.RampAsset
import com.tonapps.deposit.multicoin.data.RampAssetDetail
import com.tonapps.deposit.multicoin.data.RampConfiguration
import com.tonapps.deposit.multicoin.data.RampMethod
import com.tonapps.deposit.multicoin.data.RampProvider
import com.tonapps.deposit.multicoin.data.RampRepository
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import io.exchangeapi.models.ExchangeLimits
import io.exchangeapi.models.ExchangeMerchantSlug
import io.exchangeapi.models.ExchangeMerchantSlug.mercuryo
import io.exchangeapi.models.ExchangeMerchantSlug.transak
import io.exchangeapi.models.ExchangePaymentMethodType
import io.infrastructure.ClientException
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantRampSourceTest {

    private val rampRepository = mockk<RampRepository>()
    private val exchangeRepository = mockk<ExchangeRepository>()
    private val accountRepository = mockk<UnifiedAccountRepository>()
    private val mcAccountRepository = mockk<McAccountRepository> {
        coEvery { findAccount(TestWallet.ID, TestAssets.ton.id, any(), any()) } returns testAccount(TestAssets.ton)
    }
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val source = MerchantRampSource(
        TestWallet.ID,
        rampRepository,
        exchangeRepository,
        accountRepository,
        mcAccountRepository,
        settingsRepository,
        RampWalletAddress(mcAccountRepository),
    )

    @Test
    fun `the default asset is USDT on TON, even when another network's comes first`() = runTest {
        configuration(asset(TestAssets.usdtTron), asset(TestAssets.usdtTon), asset(TestAssets.ton))

        assertEquals(TestAssets.usdtTon.id, source.findDefaultAsset("USD").getOrThrow()?.asset?.id)
    }

    @Test
    fun `without USDT on TON the first asset is the default`() = runTest {
        configuration(asset(TestAssets.usdtTron), asset(TestAssets.ton))

        assertEquals(TestAssets.usdtTron.id, source.findDefaultAsset("USD").getOrThrow()?.asset?.id)
    }

    @Test
    fun `an asset the app cannot read is never the default`() = runTest {
        configuration(asset(AssetEntity(id = "sol/usdt", name = "USDT", symbol = "USDT", decimals = 6, imageUrl = "")), asset(TestAssets.usdtEth))

        assertEquals(TestAssets.usdtEth.id, source.findDefaultAsset("USD").getOrThrow()?.asset?.id)
    }

    @Test
    fun `no asset for the fiat is no default`() = runTest {
        configuration()

        assertNull(source.findDefaultAsset("USD").getOrThrow())
    }

    @Test
    fun `an asset no method lists stays in the preferred fiat the ramp lists, else USD`() = runTest {
        coEvery { accountRepository.getTonWalletById(TestWallet.ID) } returns mockk(relaxed = true)
        coEvery { exchangeRepository.getCurrencies(any(), any(), any()) } returns listOf(WalletCurrency.fiat("EUR"), WalletCurrency.USD)
        val asset = detail()

        assertEquals("EUR", source.resolveFiat(asset, preferred = "EUR"))
        assertEquals("USD", source.resolveFiat(asset, preferred = "CLP"))
        assertEquals("USD", source.resolveFiat(asset, preferred = "TON"))
    }

    @Test
    fun `the app describes a token the onramp does not know, without methods`() = runTest {
        coEvery { mcAccountRepository.findAccount(TestWallet.ID, TestAssets.usdtTron.id, any(), any()) } returns null

        val known = source.appAssetDetail(TestAssets.ton.id).getOrThrow()

        assertEquals(TestAssets.ton.symbol, known?.symbol)
        assertTrue(known?.methods.orEmpty().isEmpty())
        assertNull(source.appAssetDetail(TestAssets.usdtTron.id).getOrThrow())
    }

    @Test
    fun `the fiat is picked from the asset's providers when the app's list fails to load`() = runTest {
        coEvery { accountRepository.getTonWalletById(TestWallet.ID) } throws IllegalStateException("No wallet")
        val asset = detail(
            method(ExchangePaymentMethodType.card, provider(mercuryo, fiat = "EUR")),
            method(ExchangePaymentMethodType.sepa, provider(transak, fiat = "GBP")),
        )

        assertEquals("GBP", source.resolveFiat(asset, preferred = "GBP"))
        assertEquals("EUR", source.resolveFiat(asset, preferred = "RUB"))
    }

    @Test
    fun `an asset the onramp doesn't sell is no asset, not a failure`() = runTest {
        coEvery { rampRepository.getAsset(TestWallet.ID, RampType.RampOn, TestAssets.ton.id) } throws ClientException("Not found", statusCode = 404)

        assertNull(source.loadAssetDetail(TestAssets.ton.id).getOrThrow())
    }

    @Test
    fun `any other failure to load an asset is a failure`() = runTest {
        coEvery { rampRepository.getAsset(TestWallet.ID, RampType.RampOn, TestAssets.ton.id) } throws ClientException("Bad request", statusCode = 400)

        assertTrue(source.loadAssetDetail(TestAssets.ton.id).isFailure)
    }

    private fun configuration(vararg assets: RampAsset) {
        coEvery { rampRepository.getConfiguration(TestWallet.ID, RampType.RampOn, null, "USD", null, "USDT", null, 30) } returns
            RampConfiguration(assets.toList(), nextCursor = null)
    }

    private fun detail(vararg methods: RampMethod): RampAssetDetail {
        return RampAssetDetail(
            assetId = TestAssets.ton.id,
            symbol = "TON",
            decimals = 9,
            image = null,
            networkName = null,
            networkImage = null,
            methods = methods.toList(),
            extraIdRequired = false,
            extraIdName = null,
        )
    }

    private fun asset(entity: AssetEntity): RampAsset {
        return RampAsset(entity, null, listOf("USD"))
    }

    private fun method(type: ExchangePaymentMethodType, vararg providers: RampProvider): RampMethod {
        return RampMethod(type = type, name = type.value, image = "", providers = providers.toList())
    }

    private fun provider(merchant: ExchangeMerchantSlug, min: String? = null, fiat: String = "USD"): RampProvider {
        return RampProvider(merchant = merchant, fiat = fiat, limits = ExchangeLimits(min = min))
    }

}
