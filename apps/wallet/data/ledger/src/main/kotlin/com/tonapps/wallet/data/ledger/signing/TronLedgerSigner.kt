package com.tonapps.wallet.data.ledger.signing

import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerAppSetting
import com.tonapps.ledgerkit.LedgerConfirmDetail
import com.tonapps.ledgerkit.LedgerConfirmKind
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.apdu.Bip32Path
import com.tonapps.ledgerkit.app.tron.TronApp
import com.tonapps.ledgerkit.app.tron.TronMessages
import com.tonapps.ledgerkit.controller.LedgerScope
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.core
import com.trustwallet.core.tron.SigningOutput
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest

internal class TronLedgerSigner {

    suspend fun signTransaction(scope: LedgerScope, path: Bip32Path, input: ByteArray, step: Int, totalSteps: Int): ByteArray {
        val rawData = WalletCoreCompiler.preImage(Chain.Tron.Mainnet.coin.core, input)
        val signature = signRawData(scope, path, rawData, step, totalSteps)
        val compiled = WalletCoreCompiler.compile(Chain.Tron.Mainnet.coin.core, input, signature)
        val output = SigningOutput.ADAPTER.decode(compiled)
        WalletCoreCompiler.requireSigned(output.error, output.error_message)
        return compiled
    }

    suspend fun signRawData(scope: LedgerScope, path: Bip32Path, rawData: ByteArray, step: Int = 1, totalSteps: Int = 1): ByteArray {
        scope.ensureApp(LedgerAppId.Tron)
        scope.report(LedgerProgress.Confirm(LedgerAppId.Tron, LedgerConfirmKind.Transaction, step, totalSteps))
        val app = TronApp(scope.session)
        return try {
            app.signTransaction(path, rawData)
        } catch (e: LedgerFailureException) {
            val requiresHashSigning = e.failure == LedgerFailure.SettingRequired(LedgerAppId.Tron, LedgerAppSetting.SignByHash)
            if (!requiresHashSigning) {
                throw e
            }
            val hash = sha256(rawData)
            scope.report(LedgerProgress.Confirm(LedgerAppId.Tron, LedgerConfirmKind.Transaction, step, totalSteps, LedgerConfirmDetail.Hash(hash.toHexString())))
            app.signTransactionHash(path, hash)
        }
    }

    suspend fun signJsonTransaction(scope: LedgerScope, path: Bip32Path, json: String): String {
        val signed = signTransactionJson(scope, path, json, step = 1, totalSteps = 1)
        return JsonObject(mapOf("result" to signed)).toString()
    }

    suspend fun signProviderTransaction(scope: LedgerScope, path: Bip32Path, json: String, step: Int, totalSteps: Int): ByteArray {
        val signed = signTransactionJson(scope, path, json, step, totalSteps)
        return SigningOutput(json = signed.toString()).encode()
    }

    private suspend fun signTransactionJson(scope: LedgerScope, path: Bip32Path, json: String, step: Int, totalSteps: Int): JsonObject {
        val transaction = Json.parseToJsonElement(json).jsonObject
        val rawDataHex = transaction["raw_data_hex"]?.jsonPrimitive?.content
            ?: throw LedgerFailureException(LedgerFailure.Unsupported("The Tron transaction has no raw_data_hex"))
        val rawData = rawDataHex.hexToBytes()
        val declaredId = transaction["txID"]?.jsonPrimitive?.content
        if (declaredId != null && !declaredId.equals(sha256(rawData).toHexString(), ignoreCase = true)) {
            throw LedgerFailureException(LedgerFailure.InvalidPayload(0, "The Tron transaction id does not match its data"))
        }
        val signature = signRawData(scope, path, rawData, step, totalSteps)
        return JsonObject(transaction + ("signature" to JsonArray(listOf(JsonPrimitive(signature.toHexString())))))
    }

    suspend fun signPersonalMessage(scope: LedgerScope, path: Bip32Path, message: String): String {
        scope.ensureApp(LedgerAppId.Tron)
        val bytes = message.toByteArray(Charsets.UTF_8)
        val hash = TronMessages.personalMessageHash(bytes).toHexString()
        scope.report(LedgerProgress.Confirm(LedgerAppId.Tron, LedgerConfirmKind.Message, detail = LedgerConfirmDetail.Hash(hash)))
        val signature = TronApp(scope.session).signPersonalMessage(path, bytes)
        val normalized = signature.copyOf().also { bytes ->
            val recovery = bytes[RECOVERY_INDEX].toInt() and 0xff
            if (recovery < RECOVERY_OFFSET) {
                bytes[RECOVERY_INDEX] = (recovery + RECOVERY_OFFSET).toByte()
            }
        }
        return JsonObject(mapOf("signature" to JsonPrimitive("0x" + normalized.toHexString()))).toString()
    }

    suspend fun readAddress(scope: LedgerScope, path: Bip32Path, display: Boolean, expectedAddress: String?): String {
        scope.ensureApp(LedgerAppId.Tron)
        if (display && expectedAddress != null) {
            scope.report(LedgerProgress.VerifyAddress(LedgerAppId.Tron, expectedAddress))
        }
        return TronApp(scope.session).getAddress(path, display).address
    }

    private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    private companion object {
        const val RECOVERY_INDEX = 64
        const val RECOVERY_OFFSET = 27
    }
}
