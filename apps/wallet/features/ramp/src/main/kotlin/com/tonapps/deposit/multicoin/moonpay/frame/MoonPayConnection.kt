package com.tonapps.deposit.multicoin.moonpay.frame

import com.tonapps.deposit.multicoin.moonpay.api.MOONPAY_STATUS_COMPLETED
import com.tonapps.deposit.multicoin.moonpay.api.MOONPAY_STATUS_FAILED
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayLenientString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MoonPayConnectionStatus(val value: String) {
    @SerialName("active")
    Active("active"),

    @SerialName("connectionRequired")
    ConnectionRequired("connectionRequired"),

    @SerialName("termsAcceptanceRequired")
    TermsAcceptanceRequired("termsAcceptanceRequired"),

    @SerialName("pending")
    Pending("pending"),

    @SerialName("unavailable")
    Unavailable("unavailable"),

    @SerialName("failed")
    Failed("failed"),
    ;

    companion object {
        fun of(value: String): MoonPayConnectionStatus? = entries.firstOrNull { it.value == value }
    }
}

@Serializable
data class MoonPayCustomer(
    val id: String,
    val country: String? = null,
    val administrativeArea: String? = null,
)

@Serializable
data class MoonPayConnectionResult(
    val status: MoonPayConnectionStatus,
    @SerialName("credentials")
    val encryptedCredentials: String? = null,
    val customer: MoonPayCustomer? = null,
    val mismatch: Boolean? = null,
    val reason: String? = null,
)

@Serializable
data class MoonPayTransaction(
    @Serializable(with = MoonPayLenientString::class)
    val id: String? = null,
    val status: String = "",
    val failureCode: String? = null,
    val failureReason: String? = null,
) {

    val isFailed: Boolean
        get() = status == MOONPAY_STATUS_FAILED

    val isCompleted: Boolean
        get() = status == MOONPAY_STATUS_COMPLETED || status == WIDGET_STATUS_COMPLETE
}

// The widget reports a settled purchase as `complete`, the buy frame and the API as `completed`.
private const val WIDGET_STATUS_COMPLETE = "complete"
