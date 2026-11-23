package com.tonapps.deposit.multicoin.domain

import com.tonapps.deposit.multicoin.data.RampAssetDetail
import io.exchangeapi.models.ExchangeMerchantInfo
import io.exchangeapi.models.ExchangeMerchantSlug

data class RampProviderOption(
    val slug: ExchangeMerchantSlug,
    val title: String,
    val imageUrl: String?,
    val methods: List<String>,
)

// Where MoonPay itself is offered, its row stands for its widget too, as on iOS.
fun RampAssetDetail.merchantOptions(fiat: String, merchants: List<ExchangeMerchantInfo>, withMoonPayWidget: Boolean): List<RampProviderOption> {
    val sold = methods.filter { !it.isP2P }
    return merchants.mapNotNull { info ->
        val slug = merchantSlug(info.id)
            ?.takeIf { withMoonPayWidget || it != ExchangeMerchantSlug.moonpay }
            ?: return@mapNotNull null
        val names = sold.filter { method -> method.providers.any { it.merchant == slug && it.fiat == fiat } }.map { it.name }
        names.takeIf { it.isNotEmpty() }?.let { RampProviderOption(slug, info.title, info.image.takeIf(String::isNotBlank), it) }
    }
}

fun merchantSlug(value: String?): ExchangeMerchantSlug? {
    return value?.let { ExchangeMerchantSlug.entries.firstOrNull { slug -> slug.value == it } }
}

fun RampAssetDetail.moonPayMethodNames(fiat: String): List<String> {
    return methods.filter { method -> !method.isP2P && method.providers.any { it.merchant == ExchangeMerchantSlug.moonpay && it.fiat == fiat } }
        .map { it.name }
}

fun List<RampPaymentMethod>.soldBy(provider: ExchangeMerchantSlug, fiat: String): List<RampPaymentMethod> {
    return mapNotNull { method ->
        val merchantMethod = (method.details as? RampPaymentMethod.Details.Merchant)?.method
        if (merchantMethod == null || merchantMethod.isP2P) {
            return@mapNotNull method
        }
        val providers = merchantMethod.providers.filter { it.merchant == provider && it.fiat == fiat }
        providers.takeIf { it.isNotEmpty() }?.let {
            method.copy(details = RampPaymentMethod.Details.Merchant(merchantMethod.copy(providers = it)))
        }
    }
}