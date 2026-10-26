package com.tonapps.perps.funding

import io.Serializer
import io.perpsapi.models.QuotePayload
import io.perpsapi.models.WithdrawQuote
import io.perpsapi.models.WithdrawQuoteRequest
import java.math.BigInteger
import kotlinx.serialization.builtins.ListSerializer

data class PerpsWithdrawTransfer(
    val toAccountIndex: Long,
    val amountUnits: BigInteger,
    val usdcFeeUnits: BigInteger,
    val memoHex: String,
)

data class PerpsWithdrawQuote(
    val quoteId: String,
    val expiresAtMs: Long,
    val executionId: String,
    val outputUnits: BigInteger,
    val transfer: PerpsWithdrawTransfer,
    val estimatedDurationSeconds: Long? = null,
) {
    fun isAlive(nowMs: Long): Boolean = expiresAtMs > nowMs
}

sealed class PerpsWithdrawQuoteException(message: String) : IllegalStateException(message) {
    class Expired : PerpsWithdrawQuoteException("withdraw quote expired")
    class EchoMismatch(field: String) : PerpsWithdrawQuoteException("withdraw quote echoes another $field")
    class MalformedAmount(field: String) : PerpsWithdrawQuoteException("withdraw quote $field is malformed")
    class MissingTracking : PerpsWithdrawQuoteException("withdraw quote without tracking")
    class UnexpectedPayloads : PerpsWithdrawQuoteException("withdraw quote is not a single Lighter transfer")
    class UnsafeTransfer(payloadId: String) : PerpsWithdrawQuoteException("payload $payloadId is not the transfer quoted")
    class Overspend : PerpsWithdrawQuoteException("withdraw transfer spends more than the budget")
}

object PerpsWithdrawQuoteReader {

    private const val LIGHTER_CHAIN_ID = "lighter/mainnet"
    private const val PAYLOAD_TYPE_MAIN = "main"
    private const val SUPPORTED_PAYLOAD_VERSION = 1
    private const val USDC_ASSET_INDEX = 3
    private const val PERPS_ROUTE = 0
    private const val MEMO_HEX_LENGTH = 64

    fun read(quote: WithdrawQuote, request: WithdrawQuoteRequest, accountIndex: Long, nowMs: Long): PerpsWithdrawQuote {
        val expiresAtMs = quote.expiresAt.toInstant().toEpochMilli()
        if (expiresAtMs <= nowMs) {
            throw PerpsWithdrawQuoteException.Expired()
        }
        if (!quote.input.assetId.equals(PerpsFundingAssetId.USDC_ETHEREUM, ignoreCase = true)) {
            throw PerpsWithdrawQuoteException.EchoMismatch("input asset")
        }
        if (quote.input.amount != request.amount) {
            throw PerpsWithdrawQuoteException.EchoMismatch("amount")
        }
        if (quote.input.accountIndex != accountIndex) {
            throw PerpsWithdrawQuoteException.EchoMismatch("account")
        }
        if (!quote.output.assetId.equals(request.destinationAsset, ignoreCase = true)) {
            throw PerpsWithdrawQuoteException.EchoMismatch("destination asset")
        }
        val evmRecipient = PerpsFundingAssetId.parseDestination(request.destinationAsset)?.isEvm == true
        if (!quote.output.address.equals(request.recipientAddress, ignoreCase = evmRecipient)) {
            throw PerpsWithdrawQuoteException.EchoMismatch("recipient")
        }
        val budget = units(request.amount, "amount")
        val output = units(quote.output.amount, "output amount")
        val minimum = units(quote.output.minAmount, "min amount")
        if (minimum.signum() <= 0 || minimum > output) {
            throw PerpsWithdrawQuoteException.MalformedAmount("min amount")
        }
        val executionId = quote.tracking?.executionId?.takeIf { it.isNotEmpty() }
            ?: throw PerpsWithdrawQuoteException.MissingTracking()
        val transfer = transfer(quote, accountIndex)
        if (transfer.amountUnits + transfer.usdcFeeUnits > budget) {
            throw PerpsWithdrawQuoteException.Overspend()
        }
        return PerpsWithdrawQuote(
            quoteId = quote.quoteId,
            expiresAtMs = expiresAtMs,
            executionId = executionId,
            outputUnits = output,
            transfer = transfer,
            estimatedDurationSeconds = quote.estimatedDurationSeconds?.takeIf { it >= 0 }?.toLong(),
        )
    }

    private fun transfer(quote: WithdrawQuote, accountIndex: Long): PerpsWithdrawTransfer {
        val payload = payloads(quote).singleOrNull()
            ?.takeIf { it.type == PAYLOAD_TYPE_MAIN && it.version == SUPPORTED_PAYLOAD_VERSION }
            ?.takeIf { it.chainId.equals(LIGHTER_CHAIN_ID, ignoreCase = true) }
            ?: throw PerpsWithdrawQuoteException.UnexpectedPayloads()
        val meta = payload.meta
        if (meta.assetIndex != USDC_ASSET_INDEX || meta.fromRouteType != PERPS_ROUTE || meta.toRouteType != PERPS_ROUTE) {
            throw PerpsWithdrawQuoteException.UnsafeTransfer(payload.id)
        }
        val toAccountIndex = meta.toAccountIndex?.takeIf { it > 0 && it != accountIndex }
            ?: throw PerpsWithdrawQuoteException.UnsafeTransfer(payload.id)
        val amount = meta.amount?.let { units(it, "transfer amount") }?.takeIf { it.signum() > 0 }
            ?: throw PerpsWithdrawQuoteException.UnsafeTransfer(payload.id)
        val fee = meta.usdcFee?.let { units(it, "transfer fee") }
            ?: throw PerpsWithdrawQuoteException.UnsafeTransfer(payload.id)
        val memo = meta.memo?.lowercase()?.removePrefix("0x")
            ?.takeIf { it.length == MEMO_HEX_LENGTH && it.all { char -> char in '0'..'9' || char in 'a'..'f' } }
            ?: throw PerpsWithdrawQuoteException.UnsafeTransfer(payload.id)
        return PerpsWithdrawTransfer(
            toAccountIndex = toAccountIndex,
            amountUnits = amount,
            usdcFeeUnits = fee,
            memoHex = memo,
        )
    }

    // The operation comes as raw JSON; its single payload is checked here field by field.
    private fun payloads(quote: WithdrawQuote): List<QuotePayload> {
        val payloads = quote.operation["payloads"] ?: throw PerpsWithdrawQuoteException.UnexpectedPayloads()
        return try {
            // SerializationException is an IllegalArgumentException.
            Serializer.JSON.decodeFromJsonElement(ListSerializer(QuotePayload.serializer()), payloads)
        } catch (_: IllegalArgumentException) {
            throw PerpsWithdrawQuoteException.UnexpectedPayloads()
        }
    }

    private fun units(value: String, field: String): BigInteger {
        return try {
            PerpsFundingAmount.integerUnits(value)
        } catch (e: PerpsFundingAmountException) {
            throw PerpsWithdrawQuoteException.MalformedAmount(field)
        }
    }
}
