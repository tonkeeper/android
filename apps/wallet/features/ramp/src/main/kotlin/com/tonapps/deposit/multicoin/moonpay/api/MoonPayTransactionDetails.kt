package com.tonapps.deposit.multicoin.moonpay.api

import kotlinx.serialization.Serializable

internal const val MOONPAY_STATUS_COMPLETED = "completed"
internal const val MOONPAY_STATUS_FAILED = "failed"

// The transaction as MoonPay currently sees it.
@Serializable
data class MoonPayTransactionDetails(
    val id: String,
    val status: String = "",
    val transactionHash: String? = null,
    val customer: Customer? = null,
    val stages: List<Stage> = emptyList(),
    // Server time, ISO 8601.
    val createdAt: String? = null,
) {

    val createdAtMs: Long?
        get() = createdAt?.let(::parseMoonPayInstantMs)

    val isFailed: Boolean
        get() = status == MOONPAY_STATUS_FAILED

    val isCompleted: Boolean
        get() = status == MOONPAY_STATUS_COMPLETED

    val failedStage: Stage?
        get() = stages.firstOrNull { it.status == MOONPAY_STATUS_FAILED }

    @Serializable
    data class Customer(val id: String)

    @Serializable
    data class Stage(
        val kind: String? = null,
        val name: String? = null,
        val status: String? = null,
        val failureReason: String? = null,
    )
}

@Serializable
internal class MoonPayTransactionResponse(val data: MoonPayTransactionDetails)

@Serializable
internal class MoonPayTransactionsResponse(val data: List<MoonPayTransactionDetails>)
