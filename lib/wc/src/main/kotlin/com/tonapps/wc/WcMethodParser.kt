package com.tonapps.wc

import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.DappConnectRequest
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.common.containsHexPrefix
import com.tonapps.chainkit.core.common.hexToBigInteger
import com.tonapps.log.L
import com.tonapps.wc.chains.WcRequestData
import com.tonapps.wc.chains.ethereum.WcEthTransaction
import com.tonapps.wc.chains.ethereum.WcEthSignMessage
import com.tonapps.wc.chains.solana.WcSolanaSignAllTransactions
import com.tonapps.wc.chains.solana.WcSolanaSignMessage
import com.tonapps.wc.chains.solana.WcSolanaSignTransaction
import com.tonapps.wc.chains.tron.WcTronSignMessage
import com.tonapps.wc.chains.tron.WcTronSignTransaction
import com.tonapps.wc.exceptions.InvalidJsonRpcParamsException
import com.tonapps.wc.models.WcConnection
import com.tonapps.wc.models.WcDappConnection
import com.tonapps.wc.models.WcMethod
import com.tonapps.wc.models.WcSessionProposal
import com.tonapps.wc.models.WcValidation
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer
import org.json.JSONObject
import kotlin.text.isNullOrEmpty

@Suppress("UNUSED_EXPRESSION")
class WcMethodParser(
    private val json: Json,
    private val adapter: WcRouteAdapter,
) {

    sealed interface Result {
        data class Request(val data: ConfirmRequest) : Result

        data class Error(val code: Int, val throwable: Throwable? = null) : Result {
            companion object {
                const val ERROR_PARSE = 4
            }
        }
    }

    fun handleMultiSession(
        requestId: String,
        multiSession: WcSessionProposal,
        wcConnection: WcConnection,
        validation: WcValidation
    ): DappConnectRequest {
        return adapter.handleMultiSession(requestId, multiSession, wcConnection, validation)
    }

    fun handleRequest(
        requestId: String,
        walletId: String,
        chain: Chain,
        params: String,
        method: WcMethod,
        meta: WcDappConnection,
    ): Result? {
        return try {
            innerHandleRequest(requestId, walletId, chain, params, method, meta)
        } catch (e: Throwable) {
            L.e(e)
            Result.Error(Result.Error.ERROR_PARSE, e)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun innerHandleRequest(
        requestId: String,
        walletId: String,
        chain: Chain,
        params: String,
        method: WcMethod,
        meta: WcDappConnection,
    ): Result {
        return when (method) {
            WcMethod.EthSignTypedDataV4 -> {
                val result = parseListString(requestId, params)
                val message = WcEthSignMessage(result, WcEthSignMessage.WcSignType.TypedMessage)
                val chainId = runCatching {
                    val chainId = JSONObject(message.data)
                        .getJSONObject("domain")
                        .getString("chainId")

                    when (chainId.containsHexPrefix()) {
                        true -> chainId.hexToBigInteger().toString()
                        false -> chainId
                    }
                }.getOrNull()

                if (!chainId.isNullOrEmpty() && chainId != chain.id) {
                    Result.Error(Result.Error.ERROR_PARSE)
                } else {
                    val url = forceConvertRequest(requestId, walletId, chain, meta, message)
                    Result.Request(url)
                }
            }
            WcMethod.EthPersonalSign -> {
                val result = parseListString(requestId, params)
                val message = WcEthSignMessage(result, WcEthSignMessage.WcSignType.PersonalMessage)
                val url = forceConvertRequest(requestId, walletId, chain, meta, message)
                Result.Request(url)
            }
            WcMethod.EthSignTransaction -> {
                val result = parseDataList<WcEthTransaction>(requestId, params)
                val url = forceConvertRequest(requestId, walletId, chain, meta, result)
                Result.Request(url)
            }
            WcMethod.EthSendTransaction ->{
                val result = parseDataList<WcEthTransaction>(requestId, params)
                    .copy(isSend = true)

                val url = forceConvertRequest(requestId, walletId, chain, meta, result)
                Result.Request(url)
            }
            WcMethod.WalletGetCapabilities -> Result.Error(Result.Error.ERROR_PARSE)
            WcMethod.WalletWatchAsset -> Result.Error(Result.Error.ERROR_PARSE)

            // TRON
            WcMethod.TronSignMessage -> {
                val result = json.decodeFromString<WcTronSignMessage>(params)
                val url = forceConvertRequest(requestId, walletId, chain, meta, result)
                Result.Request(url)
            }
            WcMethod.TronSignTransaction -> {
                val result = json.decodeFromString<WcTronSignTransaction>(params)
                val url = forceConvertRequest(requestId, walletId, chain, meta, result)
                Result.Request(url)
            }

            // SOLANA
            WcMethod.SolanaGetAccounts -> Result.Error(Result.Error.ERROR_PARSE) // Should be already handled
            WcMethod.SolanaRequestAccounts -> Result.Error(Result.Error.ERROR_PARSE) // Should be already handled
            WcMethod.SolanaSignMessage -> {
                val result = json.decodeFromString<WcSolanaSignMessage>(params)
                val url = forceConvertRequest(requestId, walletId, chain, meta, result)
                Result.Request(url)
            }
            WcMethod.SolanaSignTransaction -> {
                val result = json.decodeFromString<WcSolanaSignTransaction>(params)
                val url = forceConvertRequest(requestId, walletId, chain, meta, result)
                Result.Request(url)
            }
            WcMethod.SolanaSignAndSendTransaction -> {
                val result = json.decodeFromString<WcSolanaSignTransaction>(params)
                    .copy(isSend = true)

                val url = forceConvertRequest(requestId, walletId, chain, meta, result)
                Result.Request(url)
            }
            WcMethod.SolanaSignAllTransactions -> {
                val result = json.decodeFromString<WcSolanaSignAllTransactions>(params)
                val url = forceConvertRequest(requestId, walletId, chain, meta, result)
                Result.Request(url)
            }
        }
    }

    private fun parseListString(id: String, params: String, asLeast: Int = 2): List<String> {
        val result = json.decodeFromString<List<JsonElement>>(params)
            .map { json ->
                if (json is JsonPrimitive) {
                    json.content
                } else {
                    json.toString()
                }
            }

        if (result.size < asLeast) {
            throw InvalidJsonRpcParamsException(id)
        }

        return result
    }

    private inline fun <reified T : Any> parseDataList(id: String, params: String): T {
        val result = json.decodeFromString(ListSerializer(serializer<T>()), params)
            .firstOrNull()
        return result ?: throw InvalidJsonRpcParamsException(id)
    }

    private fun <T : WcRequestData> forceConvertRequest(
        requestId: String,
        walletId: String,
        chain: Chain,
        meta: WcDappConnection,
        rawRequest: T,
    ): ConfirmRequest {
        return adapter.convertRequest(requestId, walletId, chain, meta, rawRequest)
            ?: throw InvalidJsonRpcParamsException(requestId)
    }
}
