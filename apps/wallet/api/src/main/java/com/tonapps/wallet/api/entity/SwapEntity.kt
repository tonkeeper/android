package com.tonapps.wallet.api.entity

import io.Serializer
import kotlinx.serialization.Serializable

object SwapEntity {

    @Serializable
    data class Message(
        val targetAddress: String,
        val sendAmount: String,
        val payload: String?,
    )

    @Serializable
    data class Messages(
        val messages: List<Message>,
        val quoteId: String,
        val resolverName: String,
        val askUnits: String,
        val bidUnits: String,
        val protocolFeeUnits: String,
        val tradeStartDeadline: String,
        val gasBudget: String,
        val estimatedGasConsumption: String,
        val slippage: Int
    ) {

        val isEmpty: Boolean
            get() = messages.isEmpty()
    }

    val empty = Messages(
        messages = emptyList(),
        quoteId = "",
        resolverName = "",
        askUnits = "",
        bidUnits = "",
        protocolFeeUnits = "",
        tradeStartDeadline = "",
        gasBudget = "",
        estimatedGasConsumption = "",
        slippage = 100
    )

    fun parse(data: String) = try {
        Serializer.fromJSON<Messages>(data)
    } catch (ignored: Throwable) {
        null
    }
}