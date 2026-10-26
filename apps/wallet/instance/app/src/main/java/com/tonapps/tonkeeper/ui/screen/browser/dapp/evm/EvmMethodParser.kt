package com.tonapps.tonkeeper.ui.screen.browser.dapp.evm

import com.tonapps.blockchain.model.ConfirmContext
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.log.L
import com.tonapps.wallet.data.dapps.wc.WcRequestParser
import com.tonapps.wc.WcRouteAdapter
import com.tonapps.wc.chains.WcRequestData
import com.tonapps.wc.chains.ethereum.WcEthSignMessage
import com.tonapps.wc.chains.ethereum.WcEthTransaction
import com.tonapps.wc.models.WcAppData
import com.tonapps.wc.models.WcDappConnection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.util.UUID

internal class EvmMethodParser(
    private val dappName: String,
    private val adapter: WcRouteAdapter = WcRequestParser(),
    // A confirmation is named by an id of our own: the page's JSON-RPC id is a timestamp plus a
    // random number, unique only by luck and meaningless outside the bridge.
    private val newRequestId: () -> String = { UUID.randomUUID().toString() },
) {

    sealed interface Result {

        data class Confirm(val request: ConfirmRequest, val requestId: String) : Result

        data class Error(val code: Int, val message: String) : Result
    }

    fun transaction(
        request: EvmProviderProtocol.Message.Request,
        walletId: String,
        chain: Chain.Evm,
        address: String,
    ): Result {
        val params = request.params.firstOrNull() as? Map<*, *>
            ?: return Result.Error(ERROR_INVALID_PARAMS, "Missing transaction")
        params.string("from")?.let { from ->
            if (!from.equals(address, ignoreCase = true)) {
                return Result.Error(ERROR_UNAUTHORIZED, "Unknown sender: $from")
            }
        }
        params.string("chainId")?.let { declared ->
            if (!matchesChain(declared, chain)) {
                return Result.Error(ERROR_UNRECOGNIZED_CHAIN, "Transaction is addressed to another chain")
            }
        }
        val transaction = WcEthTransaction(
            from = params.string("from") ?: address,
            data = params.string("data") ?: EMPTY_CALL_DATA,
            to = params.string("to"),
            nonce = params.string("nonce"),
            value = params.string("value"),
            // EIP-1193 calls it `gas`; WalletConnect payloads use `gasLimit` for the same field.
            gasLimit = params.string("gas") ?: params.string("gasLimit"),
            gasPrice = params.string("gasPrice"),
            isSend = true,
        )
        return confirm(request, walletId, chain, transaction)
    }

    fun personalSign(
        request: EvmProviderProtocol.Message.Request,
        walletId: String,
        chain: Chain.Evm,
        address: String,
    ): Result {
        val data = request.params.firstOrNull() as? String
            ?: return Result.Error(ERROR_INVALID_PARAMS, "Missing message")
        (request.params.getOrNull(1) as? String)?.let { signer ->
            if (!signer.equals(address, ignoreCase = true)) {
                return Result.Error(ERROR_UNAUTHORIZED, "Unknown signer: $signer")
            }
        }
        val message = WcEthSignMessage(
            raw = listOf(data, address),
            type = WcEthSignMessage.WcSignType.PersonalMessage,
        )
        return confirm(request, walletId, chain, message)
    }

    fun typedData(
        request: EvmProviderProtocol.Message.Request,
        walletId: String,
        chain: Chain.Evm,
        address: String,
    ): Result {
        val payload = request.params.firstNotNullOfOrNull(::typedDataOrNull)
            ?: return Result.Error(ERROR_INVALID_PARAMS, "Missing typed data")
        request.params.filterIsInstance<String>()
            .firstOrNull { typedDataOrNull(it) == null }
            ?.let { signer ->
                if (!signer.equals(address, ignoreCase = true)) {
                    return Result.Error(ERROR_UNAUTHORIZED, "Unknown signer: $signer")
                }
            }
        if (!matchesChain(payload.chainId, chain)) {
            return Result.Error(ERROR_UNRECOGNIZED_CHAIN, "Typed data is addressed to another chain")
        }
        // The raw JSON travels on: chainkit hashes typed data itself.
        val message = WcEthSignMessage(
            raw = listOf(address, payload.raw),
            type = WcEthSignMessage.WcSignType.TypedMessage,
        )
        return confirm(request, walletId, chain, message)
    }

    private fun <T : WcRequestData> confirm(
        request: EvmProviderProtocol.Message.Request,
        walletId: String,
        chain: Chain.Evm,
        data: T,
    ): Result {
        val parsed = runCatching {
            adapter.convertRequest(newRequestId(), walletId, chain, dappMeta(request.host), data)
        }.onFailure { L.e(it) }.getOrNull()
            ?: return Result.Error(ERROR_INVALID_PARAMS, "Request cannot be confirmed")
        val dapp = parsed.context as? ConfirmContext.Dapp
            ?: return Result.Error(ERROR_INVALID_PARAMS, "Request has no dapp context")
        return Result.Confirm(
            request = ConfirmRequest(
                data = parsed.data,
                type = parsed.type,
                action = parsed.action,
                context = dapp.copy(source = ConfirmContext.Dapp.Source.Injected),
                initiator = parsed.initiator,
            ),
            requestId = dapp.requestId,
        )
    }

    private fun typedDataOrNull(value: Any?): TypedData? {
        val json = when (value) {
            is String -> runCatching { Json.parseToJsonElement(value).jsonObject }.getOrNull()
            is Map<*, *> -> toJson(value) as? JsonObject
            else -> null
        } ?: return null
        if (!json.containsKey("domain")) {
            return null
        }
        val chainId = (json["domain"]?.jsonObject?.get("chainId") as? JsonPrimitive)?.content
        // A payload that arrived as a string travels on untouched; only an object needs encoding.
        return TypedData(raw = value as? String ?: json.toString(), chainId = chainId)
    }

    private fun toJson(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> JsonObject(value.entries.associate { it.key.toString() to toJson(it.value) })
            is List<*> -> JsonArray(value.map(::toJson))
            else -> JsonPrimitive(value.toString())
        }
    }

    private fun matchesChain(declared: String?, chain: Chain.Evm): Boolean {
        val value = declared ?: return true
        val decimal = when {
            value.startsWith("0x", ignoreCase = true) -> value.removePrefix("0x").toLongOrNull(16)?.toString()
            else -> value
        } ?: return true
        return decimal == chain.id
    }

    private fun dappMeta(host: String): WcDappConnection {
        return WcDappConnection(
            topic = host,
            app = WcAppData(
                name = dappName,
                url = "https://$host",
            ),
        )
    }

    private fun Map<*, *>.string(key: String): String? {
        return (this[key] as? String)?.takeIf { it.isNotBlank() }
    }

    private class TypedData(val raw: String, val chainId: String?)

    private companion object {

        private const val ERROR_UNAUTHORIZED = 4100
        private const val ERROR_UNRECOGNIZED_CHAIN = 4902
        private const val ERROR_INVALID_PARAMS = -32602

        private const val EMPTY_CALL_DATA = "0x"
    }
}
