package com.tonapps.deposit.multicoin.moonpay.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val MOONPAY_CARD_TYPE = "card"

@Serializable
data class MoonPayPaymentMethodAvailability(
    val active: Boolean = false,
    // `card_expired`, `card_declined`, `card_blocked`, …
    val reasons: List<String> = emptyList(),
)

// A payment method type the connected customer may use.
@Serializable
data class MoonPayPaymentMethodConfig(
    // `card`, `google_pay`, `sepa`, `paypal`, … kept raw so a type added later still renders.
    val type: String,
    val capabilities: Capabilities? = null,
    val availability: MoonPayPaymentMethodAvailability? = null,
) {

    val isActive: Boolean
        get() = availability?.active ?: false

    // Unknown capabilities are not treated as a restriction.
    val supportsBuy: Boolean
        get() = capabilities?.supportsBuy ?: true

    @Serializable
    data class Capabilities(
        val supportedCurrencies: List<String> = emptyList(),
        val supportedTransactionTypes: List<String> = emptyList(),
        val allowsDeletion: Boolean = false,
        val requiresWidget: Boolean = false,
    ) {

        val supportsBuy: Boolean
            get() = "buy" in supportedTransactionTypes
    }
}

// A stored payment method: an entry of `paymentMethods` and the `complete` payload of the `add-card` frame.
@Serializable
data class MoonPaySavedCard(
    val id: String,
    val type: String? = null,
    val cardType: String? = null,
    val brand: String? = null,
    @Serializable(with = MoonPayLenientString::class)
    val last4: String? = null,
    @Serializable(with = MoonPayLenientString::class)
    val expirationMonth: String? = null,
    @Serializable(with = MoonPayLenientString::class)
    val expirationYear: String? = null,
    val availability: MoonPayPaymentMethodAvailability? = null,
)

data class MoonPayPaymentMethods(
    val configs: List<MoonPayPaymentMethodConfig>,
    val stored: List<MoonPaySavedCard>,
) {

    val activeConfigs: List<MoonPayPaymentMethodConfig>
        get() = configs.filter { it.isActive }
}

@Serializable
internal class MoonPayPaymentMethodsResponse(val data: Data) {

    @Serializable
    class Data(
        @SerialName("paymentMethodConfigs")
        val configs: List<MoonPayPaymentMethodConfig> = emptyList(),
        @SerialName("paymentMethods")
        val stored: List<MoonPaySavedCard> = emptyList(),
    )

    // MoonPay lists wallets such as Google Pay here with placeholder ids; only cards carry
    // a payment method id that a quote and the buy frame accept.
    fun toPaymentMethods(): MoonPayPaymentMethods {
        return MoonPayPaymentMethods(
            configs = data.configs,
            stored = data.stored.filter { it.type == null || it.type == MOONPAY_CARD_TYPE },
        )
    }
}
