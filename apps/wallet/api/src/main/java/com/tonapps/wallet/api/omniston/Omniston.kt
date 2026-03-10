package com.tonapps.wallet.api.omniston

import kotlinx.serialization.Serializable

object Omniston {

    fun fixAddress(address: String): String {
        if (address.equals("ton", true)) {
            return "EQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAM9c"
        }
        return address
    }

    @Serializable
    data class AssetAddress(
        val blockchain: Int = 607,
        val address: String
    )

    @Serializable
    data class Amount(
        val offer_units: String? = null,
        val ask_units: String? = null
    )

    @Serializable
    data class SettlementParams(
        val max_price_slippage_bps: Int = 500,
        val max_outgoing_messages: Int = 4
    )

    @Serializable
    data class QuoteParams(
        val offer_asset_address: AssetAddress,
        val ask_asset_address: AssetAddress,
        val amount: Amount,
        val referrer_fee_bps: Int = 0,
        val settlement_methods: List<Int> = listOf(0),
        val settlement_params: SettlementParams
    )

    @Serializable
    data class QuoteResult(
        val quote_id: String,
        val offer_asset_address: AssetAddress,
        val ask_asset_address: AssetAddress,
        val offer_amount: Amount,
        val ask_amount: Amount,
        val rate: String,
        val expires_at: Long,
        val settlement_methods: List<Int>
    )

    @Serializable
    data class EventWrapper(
        val jsonrpc: String,
        val method: String,
        val params: EventParams
    )

    @Serializable
    data class EventParams(
        val subscription: Long,
        val result: EventResult
    )

    @Serializable
    data class EventResult(
        val event: Event
    )

    @Serializable
    data class Event(
        val quote_updated: QuoteUpdated? = null,
    )

    @Serializable
    data class QuoteUpdated(
        val quote_id: String,
        val resolver_id: String,
        val resolver_name: String,
        val offer_asset_address: AssetAddress,
        val ask_asset_address: AssetAddress,
        val offer_units: String,
        val ask_units: String,
        val referrer_address: String?,
        val referrer_fee_units: String,
        val protocol_fee_units: String,
        val quote_timestamp: Long,
        val trade_start_deadline: Long,
        val gas_budget: String,
        val estimated_gas_consumption: String,
        val referrer_fee_asset: AssetAddress,
        val protocol_fee_asset: AssetAddress,
        val params: SwapParams
    )

    @Serializable
    data class SwapParams(
        val swap: SwapDetails
    )

    @Serializable
    data class SwapDetails(
        val routes: List<Route>
    )

    @Serializable
    data class Route(
        val steps: List<Step>,
        val gas_budget: String
    )

    @Serializable
    data class Step(
        val offer_asset_address: AssetAddress,
        val ask_asset_address: AssetAddress,
        val chunks: List<Chunk>
    )

    @Serializable
    data class Chunk(
        val protocol: String,
        val offer_amount: String,
        val ask_amount: String,
        val extra_version: Int,
        val extra: List<Int>
    )

    @Serializable
    data class TonEventResult(
        val ton: TonPayload
    )

    @Serializable
    data class TonPayload(
        val messages: List<TonMessage>
    )

    @Serializable
    data class TonMessage(
        val target_address: String,
        val send_amount: String,
        val payload: String
    )

    @Serializable
    data class TransactionBuildTransfer(
        val destination_address: AssetAddress,
        val gas_excess_address: AssetAddress,
        val source_address: AssetAddress,
        val quote: QuoteUpdated,
    )

}