package com.tonapps.deposit.multicoin.domain

import com.tonapps.deposit.multicoin.data.RampAssetDetail
import com.tonapps.deposit.multicoin.data.RampMethod
import com.tonapps.deposit.multicoin.data.RampProvider
import io.exchangeapi.models.ExchangeLimits
import io.exchangeapi.models.ExchangeMerchantInfo
import io.exchangeapi.models.ExchangeMerchantSlug
import io.exchangeapi.models.ExchangePaymentMethodType
import org.junit.Assert.assertEquals
import org.junit.Test

class RampProvidersTest {

    private val card = method(
        ExchangePaymentMethodType.card,
        provider(ExchangeMerchantSlug.moonpay),
        provider(ExchangeMerchantSlug.mercuryo),
        provider(ExchangeMerchantSlug.transak, fiat = "EUR"),
    )
    private val googlePay = method(ExchangePaymentMethodType.google_pay, provider(ExchangeMerchantSlug.mercuryo))
    private val p2p = method(ExchangePaymentMethodType.p2p, provider(ExchangeMerchantSlug.wallet))
    private val asset = detail(card, googlePay, p2p)

    @Test
    fun `the merchants that sell in the currency come in the order of the merchants list, without P2P, and MoonPay's widget only where MoonPay is not offered`() {
        val merchants = listOf(info(ExchangeMerchantSlug.transak), info(ExchangeMerchantSlug.moonpay), info(ExchangeMerchantSlug.mercuryo), info(ExchangeMerchantSlug.wallet))

        val usd = asset.merchantOptions("USD", merchants, withMoonPayWidget = false)
        val eur = asset.merchantOptions("EUR", merchants, withMoonPayWidget = false)
        val withWidget = asset.merchantOptions("USD", merchants, withMoonPayWidget = true)

        assertEquals(listOf(ExchangeMerchantSlug.mercuryo), usd.map { it.slug })
        assertEquals(listOf("card", "google_pay"), usd.single().methods)
        assertEquals("Mercuryo", usd.single().title)
        assertEquals(listOf(ExchangeMerchantSlug.transak), eur.map { it.slug })
        assertEquals(listOf(ExchangeMerchantSlug.moonpay, ExchangeMerchantSlug.mercuryo), withWidget.map { it.slug })
    }

    @Test
    fun `MoonPay's methods are the ones our backend lists it for in the currency`() {
        assertEquals(listOf("card"), asset.moonPayMethodNames("USD"))
        assertEquals(emptyList<String>(), asset.moonPayMethodNames("EUR"))
    }

    @Test
    fun `a provider keeps only its own methods and quotes alone, and P2P stays`() {
        val methods = listOf(card, googlePay, p2p).map { it.toPaymentMethod() }

        val moonPay = methods.soldBy(ExchangeMerchantSlug.moonpay, "USD")
        val mercuryo = methods.soldBy(ExchangeMerchantSlug.mercuryo, "USD")

        assertEquals(listOf("card", "p2p"), moonPay.map { it.id })
        assertEquals(listOf(ExchangeMerchantSlug.moonpay), merchants(moonPay.first()))
        assertEquals(listOf("card", "google_pay", "p2p"), mercuryo.map { it.id })
        assertEquals(listOf(ExchangeMerchantSlug.mercuryo), merchants(mercuryo.first()))
    }

    private fun merchants(method: RampPaymentMethod): List<ExchangeMerchantSlug> {
        return (method.details as RampPaymentMethod.Details.Merchant).method.providers.map { it.merchant }
    }

    private fun method(type: ExchangePaymentMethodType, vararg providers: RampProvider): RampMethod {
        return RampMethod(type = type, name = type.value, image = "", providers = providers.toList())
    }

    private fun provider(merchant: ExchangeMerchantSlug, fiat: String = "USD"): RampProvider {
        return RampProvider(merchant = merchant, fiat = fiat, limits = ExchangeLimits())
    }

    private fun info(slug: ExchangeMerchantSlug): ExchangeMerchantInfo {
        return ExchangeMerchantInfo(
            id = slug.value,
            title = slug.value.replaceFirstChar { it.uppercase() },
            description = "",
            image = "https://img/${slug.value}",
            fee = "",
            buttons = emptyList(),
        )
    }

    private fun detail(vararg methods: RampMethod): RampAssetDetail {
        return RampAssetDetail(
            assetId = "ton",
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
}