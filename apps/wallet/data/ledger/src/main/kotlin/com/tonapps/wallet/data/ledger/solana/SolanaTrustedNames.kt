package com.tonapps.wallet.data.ledger.solana

import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.transport.LedgerModel
import com.tonapps.wallet.data.ledger.analytics.LedgerIssueSite
import com.tonapps.wallet.data.ledger.analytics.LedgerNonFatals
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.ByteString.Companion.decodeHex
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal class SolanaTrustedNames(
    httpClient: OkHttpClient,
    private val signatureKind: SignatureKind = SignatureKind.Prod,
) {

    private val httpClient = httpClient.newBuilder()
        .apply {
            interceptors().clear()
            networkInterceptors().clear()
        }
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    private val certificates = ConcurrentHashMap<LedgerModel, Certificate>()

    suspend fun certificate(model: LedgerModel): Certificate? = guarded {
        certificates[model] ?: withContext(Dispatchers.IO) { fetchCertificate(model) }?.also { certificates[model] = it }
    }

    suspend fun descriptor(recipient: SolanaTokenRecipient, challenge: ByteArray): ByteArray? = guarded {
        withContext(Dispatchers.IO) { fetchDescriptor(recipient, challenge) }
    }

    private fun fetchCertificate(model: LedgerModel): Certificate? {
        val device = model.calName ?: return null
        val url = "$CAL_URL/v1/certificates".toHttpUrl().newBuilder()
            .addQueryParameter("output", "id,descriptor")
            .addQueryParameter("target_device", device)
            .addQueryParameter("public_key_usage", "trusted_name")
            .addQueryParameter("public_key_id", "domain_metadata_key")
            .addQueryParameter("latest", "true")
            .build()
        val body = get(url.toString())
        val certificate = json.decodeFromString(ListSerializer(CertificateResponse.serializer()), body).firstOrNull() ?: return null
        val signature = when (signatureKind) {
            SignatureKind.Prod -> certificate.descriptor.signatures.prod
            SignatureKind.Test -> certificate.descriptor.signatures.test
        }
        return Certificate(certificate.descriptor.data.decodeHex().toByteArray(), signature.decodeHex().toByteArray())
    }

    private fun fetchDescriptor(recipient: SolanaTokenRecipient, challenge: ByteArray): ByteArray? {
        val challengeHex = "0x" + challenge.joinToString("") { "%02x".format(it) }
        val path = when (recipient) {
            is SolanaTokenRecipient.Existing -> "owner/${recipient.tokenAccount}"
            is SolanaTokenRecipient.Created -> "computed-token-account/${recipient.owner}/${recipient.mint}"
        }
        val url = "$TRUST_URL/v2/solana/$path".toHttpUrl().newBuilder()
            .addQueryParameter("challenge", challengeHex)
            .build()
        val response = json.decodeFromString(OwnerResponse.serializer(), get(url.toString()))
        if (!recipient.matches(response.tokenAccount, response.owner, response.contract)) {
            LedgerNonFatals.report(IllegalStateException("The trusted name does not match the transaction recipient"), LedgerIssueSite.ClearSigning)
            return null
        }
        return response.signedDescriptor.decodeHex().toByteArray()
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header(CLIENT_VERSION_HEADER, CLIENT_VERSION).build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Ledger service responded with ${response.code}")
            }
            response.body.string()
        }
    }

    private suspend fun <T> guarded(block: suspend () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LedgerNonFatals.report(e, LedgerIssueSite.ClearSigning)
        null
    }

    class Certificate(val descriptor: ByteArray, val signature: ByteArray)

    enum class SignatureKind {
        Prod,
        Test,
    }

    @Serializable
    private class CertificateResponse(val descriptor: CertificateDescriptor)

    @Serializable
    private class CertificateDescriptor(val data: String, val signatures: CertificateSignatures)

    @Serializable
    private class CertificateSignatures(val prod: String, val test: String)

    @Serializable
    private class OwnerResponse(
        @SerialName("tokenAccount") val tokenAccount: String,
        @SerialName("owner") val owner: String,
        @SerialName("contract") val contract: String,
        @SerialName("signedDescriptor") val signedDescriptor: String,
    )

    private companion object {
        const val CAL_URL = "https://global.api.prd.ledger.com/cal"
        const val TRUST_URL = "https://nft.api.live.ledger.com"
        const val CLIENT_VERSION_HEADER = "X-Ledger-Client-Version"
        const val CLIENT_VERSION = "keeper-android"
        const val CALL_TIMEOUT_SECONDS = 4L
        val json = Json { ignoreUnknownKeys = true }

        val LedgerModel.calName: String?
            get() = when (this) {
                LedgerModel.Stax -> "stax"
                LedgerModel.Flex -> "flex"
                LedgerModel.NanoX -> "nanox"
                LedgerModel.NanoGen5 -> "apexp"
                LedgerModel.NanoSPlus -> "nanosp"
                LedgerModel.NanoS -> null
            }
    }
}

internal sealed interface SolanaTokenRecipient {

    fun matches(tokenAccount: String, owner: String, mint: String): Boolean

    data class Existing(val tokenAccount: String, val mint: String) : SolanaTokenRecipient {
        override fun matches(tokenAccount: String, owner: String, mint: String): Boolean =
            tokenAccount == this.tokenAccount && mint == this.mint
    }

    data class Created(val owner: String, val mint: String, val tokenAccount: String) : SolanaTokenRecipient {
        override fun matches(tokenAccount: String, owner: String, mint: String): Boolean =
            tokenAccount == this.tokenAccount && owner == this.owner && mint == this.mint
    }

    companion object {

        private const val TOKEN_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
        private const val TOKEN_2022_PROGRAM = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"
        private const val ASSOCIATED_TOKEN_PROGRAM = "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL"
        private const val TRANSFER_CHECKED: Byte = 12
        private const val CREATE: Byte = 0
        private const val CREATE_IDEMPOTENT: Byte = 1
        private val tokenPrograms = setOf(TOKEN_PROGRAM, TOKEN_2022_PROGRAM)

        fun from(message: ByteArray): SolanaTokenRecipient? {
            val parsed = try {
                SolanaMessage.parse(message)
            } catch (e: LedgerFailureException) {
                return null
            }
            val transfer = parsed.instructions.singleOrNull { parsed.isTransferChecked(it) } ?: return null
            val mint = parsed.address(transfer.accounts[1]) ?: return null
            val tokenAccount = parsed.address(transfer.accounts[2]) ?: return null
            val creation = parsed.instructions.firstOrNull { parsed.createsAccount(it, tokenAccount) }
                ?: return Existing(tokenAccount = tokenAccount, mint = mint)
            if (parsed.address(creation.accounts[3]) != mint) {
                return null
            }
            val owner = parsed.address(creation.accounts[2]) ?: return null
            return Created(owner = owner, mint = mint, tokenAccount = tokenAccount)
        }

        private fun SolanaMessage.isTransferChecked(instruction: SolanaMessage.Instruction): Boolean =
            program(instruction) in tokenPrograms &&
                instruction.data.firstOrNull() == TRANSFER_CHECKED &&
                instruction.accounts.size >= 4

        private fun SolanaMessage.createsAccount(instruction: SolanaMessage.Instruction, tokenAccount: String): Boolean =
            program(instruction) == ASSOCIATED_TOKEN_PROGRAM &&
                (instruction.data.isEmpty() || instruction.data[0] == CREATE || instruction.data[0] == CREATE_IDEMPOTENT) &&
                instruction.accounts.size >= 4 &&
                address(instruction.accounts[1]) == tokenAccount
    }
}
