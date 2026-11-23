package com.tonapps.wallet.data.ledger.signing

import com.tonapps.ledgerkit.apdu.Apdu
import com.tonapps.wallet.data.ledger.analytics.LedgerIssueSite
import com.tonapps.wallet.data.ledger.analytics.LedgerNonFatals
import com.trustwallet.core.ethereum.SigningInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.ByteString.Companion.decodeHex
import java.io.IOException
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal class EvmTokenDescriptors(httpClient: OkHttpClient) {

    private val httpClient = httpClient.newBuilder()
        .apply {
            interceptors().clear()
            networkInterceptors().clear()
        }
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    private val cache = ConcurrentHashMap<String, CachedDescriptor>()

    suspend fun forTransaction(input: ByteArray): ByteArray? = try {
        EvmTokenCall.from(input)?.let { call -> find(call) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LedgerNonFatals.report(e, LedgerIssueSite.ClearSigning)
        null
    }

    suspend fun forContract(chainId: Long, contract: String): ByteArray? = try {
        find(EvmTokenCall(chainId, contract.lowercase()))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LedgerNonFatals.report(e, LedgerIssueSite.ClearSigning)
        null
    }

    private suspend fun find(call: EvmTokenCall): ByteArray? {
        val key = "${call.chainId}:${call.contract}"
        cache[key]?.let { return it.payload?.copyOf() }
        val payload = withContext(Dispatchers.IO) { fetch(call) }
        cache[key] = CachedDescriptor(payload)
        return payload?.copyOf()
    }

    private fun fetch(call: EvmTokenCall): ByteArray? {
        val url = CAL_TOKENS_URL.toHttpUrl().newBuilder()
            .addQueryParameter("contract_address", call.contract)
            .addQueryParameter("chain_id", call.chainId.toString())
            .addQueryParameter("output", "descriptor")
            .addQueryParameter("ref", "branch:main")
            .build()
        val request = Request.Builder().url(url).header(CLIENT_VERSION_HEADER, CLIENT_VERSION).build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Ledger CAL responded with ${response.code}")
            }
            EvmTokenDescriptor.payload(response.body.string(), call)
        }
    }

    private class CachedDescriptor(val payload: ByteArray?)

    private companion object {
        const val CAL_TOKENS_URL = "https://global.api.prd.ledger.com/cal/v1/tokens"
        const val CLIENT_VERSION_HEADER = "X-Ledger-Client-Version"
        const val CLIENT_VERSION = "keeper-android"
        const val CALL_TIMEOUT_SECONDS = 4L
    }
}

internal class EvmTokenCall(val chainId: Long, val contract: String) {

    companion object {

        private val ADDRESS = Regex("^0x[0-9a-f]{40}$")
        private val TRANSFER_SELECTOR = "a9059cbb".decodeHex()
        private val APPROVE_SELECTOR = "095ea7b3".decodeHex()
        private const val MAX_CHAIN_ID_BITS = 32

        fun from(input: ByteArray): EvmTokenCall? {
            val signingInput = SigningInput.ADAPTER.decode(input)
            val transaction = signingInput.transaction ?: return null
            val genericData = transaction.contract_generic?.data_
            val isTokenCall = transaction.erc20_transfer != null ||
                transaction.erc20_approve != null ||
                genericData?.startsWith(TRANSFER_SELECTOR) == true ||
                genericData?.startsWith(APPROVE_SELECTOR) == true
            if (!isTokenCall) {
                return null
            }
            val contract = signingInput.to_address.lowercase()
            val chainId = BigInteger(1, signingInput.chain_id.toByteArray())
            if (!ADDRESS.matches(contract) || chainId.signum() <= 0 || chainId.bitLength() > MAX_CHAIN_ID_BITS) {
                return null
            }
            return EvmTokenCall(chainId.toLong(), contract)
        }
    }
}

internal object EvmTokenDescriptor {

    private const val ADDRESS_LENGTH = 20
    private const val DECIMALS_LENGTH = 4
    private const val CHAIN_ID_LENGTH = 4
    private const val SUFFIX_LENGTH = ADDRESS_LENGTH + DECIMALS_LENGTH + CHAIN_ID_LENGTH
    private const val MAX_TICKER_LENGTH = 32
    private const val MIN_PRINTABLE = 0x20
    private const val MAX_PRINTABLE = 0x7e

    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(TokenDto.serializer())

    fun payload(body: String, call: EvmTokenCall): ByteArray? {
        val descriptor = json.decodeFromString(listSerializer, body).firstOrNull()?.descriptor ?: return null
        val signatureHex = descriptor.signatures?.prod ?: return null
        if (!descriptor.data.isHexEncoded() || !signatureHex.isHexEncoded()) {
            return null
        }
        val data = descriptor.data.hexToBytes()
        val tickerLength = data.size - SUFFIX_LENGTH
        if (tickerLength !in 1..MAX_TICKER_LENGTH) {
            return null
        }
        val ticker = data.copyOfRange(0, tickerLength)
        val address = data.copyOfRange(tickerLength, tickerLength + ADDRESS_LENGTH)
        val chainId = BigInteger(1, data.copyOfRange(data.size - CHAIN_ID_LENGTH, data.size)).toLong()
        val isValid = ticker.all { (it.toInt() and 0xff) in MIN_PRINTABLE..MAX_PRINTABLE } &&
            address.contentEquals(call.contract.hexToBytes()) &&
            chainId == call.chainId
        if (!isValid) {
            return null
        }
        val payload = byteArrayOf(tickerLength.toByte()) + data + signatureHex.hexToBytes()
        return payload.takeIf { it.size <= Apdu.MAX_DATA_LENGTH }
    }

    @Serializable
    private class TokenDto(val descriptor: DescriptorDto? = null)

    @Serializable
    private class DescriptorDto(val data: String, val signatures: SignaturesDto? = null)

    @Serializable
    private class SignaturesDto(val prod: String? = null)
}
