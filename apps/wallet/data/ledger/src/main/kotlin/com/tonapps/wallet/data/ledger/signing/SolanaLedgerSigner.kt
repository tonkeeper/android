package com.tonapps.wallet.data.ledger.signing

import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerConfirmDetail
import com.tonapps.ledgerkit.LedgerConfirmKind
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.apdu.Bip32Path
import com.tonapps.ledgerkit.app.solana.SolanaApp
import com.tonapps.ledgerkit.controller.LedgerScope
import com.tonapps.ledgerkit.session.LedgerAppInfo
import com.tonapps.ledgerkit.session.LedgerPki
import com.tonapps.ledgerkit.util.Base58
import com.tonapps.wallet.data.ledger.analytics.LedgerNonFatals
import com.tonapps.wallet.data.ledger.solana.SolanaTokenRecipient
import com.tonapps.wallet.data.ledger.solana.SolanaTrustedNames
import com.tonapps.wallet.data.ledger.solana.SolanaWireTransaction
import com.trustwallet.core.CoinType
import com.trustwallet.core.DataVector
import com.trustwallet.core.PublicKey
import com.trustwallet.core.PublicKeyType
import com.trustwallet.core.TransactionCompiler
import com.trustwallet.core.solana.PubkeySignature
import com.trustwallet.core.solana.SigningOutput
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest
import java.util.Base64

internal class SolanaLedgerSigner(private val trustedNames: SolanaTrustedNames) {

    suspend fun signChainkitInput(
        scope: LedgerScope,
        path: Bip32Path,
        publicKey: ByteArray,
        input: ByteArray,
        step: Int,
        totalSteps: Int,
    ): ByteArray {
        val message = WalletCoreCompiler.preImage(CoinType.Solana, input)
        val signature = sign(scope, path, publicKey, message, step, totalSteps)
        val compiled = TransactionCompiler.compileWithSignatures(
            CoinType.Solana,
            input,
            DataVector().apply { add(signature) },
            DataVector().apply { add(publicKey) },
        )
        val output = SigningOutput.ADAPTER.decode(compiled)
        WalletCoreCompiler.requireSigned(output.error, output.error_message)
        return compiled
    }

    suspend fun signWireTransaction(
        scope: LedgerScope,
        path: Bip32Path,
        publicKey: ByteArray,
        base64Transaction: String,
        step: Int = 1,
        totalSteps: Int = 1,
    ): String {
        val signed = signWire(scope, path, publicKey, base64Transaction, step, totalSteps)
        return JsonObject(
            mapOf(
                SIGNATURE to JsonPrimitive(signed.signatureBase58),
                TRANSACTION to JsonPrimitive(signed.transactionBase64),
            ),
        ).toString()
    }

    suspend fun signPrebuilt(
        scope: LedgerScope,
        path: Bip32Path,
        publicKey: ByteArray,
        base64Transaction: String,
        step: Int,
        totalSteps: Int,
    ): ByteArray {
        val signed = signWire(scope, path, publicKey, base64Transaction, step, totalSteps)
        return SigningOutput(
            encoded = signed.transactionBase64,
            signatures = listOf(PubkeySignature(pubkey = Base58.encode(publicKey), signature = signed.signatureBase58)),
        ).encode()
    }

    private suspend fun signWire(
        scope: LedgerScope,
        path: Bip32Path,
        publicKey: ByteArray,
        base64Transaction: String,
        step: Int,
        totalSteps: Int,
    ): SignedWire {
        val transaction = SolanaWireTransaction.parse(decodeBase64(base64Transaction))
        val index = transaction.signerIndex(publicKey)
        val signature = sign(scope, path, publicKey, transaction.message, step, totalSteps)
        return SignedWire(
            signatureBase58 = Base58.encode(signature),
            transactionBase64 = Base64.getEncoder().encodeToString(transaction.withSignature(index, signature)),
        )
    }

    suspend fun readPublicKey(scope: LedgerScope, path: Bip32Path, display: Boolean, expectedAddress: String?): ByteArray {
        requireApp(scope)
        if (display && expectedAddress != null) {
            scope.report(LedgerProgress.VerifyAddress(LedgerAppId.Solana, expectedAddress))
        }
        return SolanaApp(scope.session).getPublicKey(path, display)
    }

    private suspend fun sign(
        scope: LedgerScope,
        path: Bip32Path,
        publicKey: ByteArray,
        message: ByteArray,
        step: Int,
        totalSteps: Int,
    ): ByteArray {
        val info = requireApp(scope)
        val app = SolanaApp(scope.session)
        SolanaTokenRecipient.from(message)?.let { recipient ->
            provideRecipient(scope, app, info, recipient)
        }
        val detail = if (isBlindSigningEnabled(app)) {
            LedgerConfirmDetail.MessageHash(Base58.encode(MessageDigest.getInstance("SHA-256").digest(message)))
        } else {
            null
        }
        scope.report(LedgerProgress.Confirm(LedgerAppId.Solana, LedgerConfirmKind.Transaction, step, totalSteps, detail))
        val signature = app.signMessage(path, message)
        if (!PublicKey(publicKey, PublicKeyType.ED25519).verify(signature, message)) {
            throw LedgerFailureException(LedgerFailure.Unexpected("The Ledger signed a different Solana transaction"))
        }
        return signature
    }

    private suspend fun isBlindSigningEnabled(app: SolanaApp): Boolean = try {
        app.getConfiguration().blindSigningEnabled
    } catch (e: LedgerFailureException) {
        val failure = e.failure
        if (failure !is LedgerFailure.Unexpected && failure !is LedgerFailure.DeviceError && failure !is LedgerFailure.InvalidPayload) {
            throw e
        }
        LedgerNonFatals.reportFallback("The Solana app configuration could not be read", e)
        false
    }

    private suspend fun provideRecipient(scope: LedgerScope, app: SolanaApp, info: LedgerAppInfo, recipient: SolanaTokenRecipient) {
        if (!info.isAtLeast(SolanaApp.TRUSTED_NAME_MIN_APP_VERSION)) {
            return
        }
        val model = scope.session.model ?: return
        val certificate = trustedNames.certificate(model) ?: return
        try {
            LedgerPki.loadCertificate(scope.session, LedgerPki.KEY_USAGE_TRUSTED_NAME, certificate.descriptor, certificate.signature)
            val descriptor = trustedNames.descriptor(recipient, app.getChallenge()) ?: return
            app.provideTrustedName(descriptor)
        } catch (e: LedgerFailureException) {
            val failure = e.failure
            if (failure !is LedgerFailure.DeviceError && failure !is LedgerFailure.InvalidPayload && failure !is LedgerFailure.Unexpected) {
                throw e
            }
            LedgerNonFatals.reportFallback("The Solana app rejected a trusted name", e)
        }
    }

    private suspend fun requireApp(scope: LedgerScope): LedgerAppInfo {
        val info = scope.ensureApp(LedgerAppId.Solana)
        if (!info.isAtLeast(SolanaApp.MIN_APP_VERSION)) {
            throw LedgerFailureException(LedgerFailure.AppUpdateRequired(LedgerAppId.Solana, info.version, SolanaApp.MIN_APP_VERSION))
        }
        return info
    }

    private fun decodeBase64(value: String): ByteArray = try {
        Base64.getDecoder().decode(value.trim())
    } catch (e: IllegalArgumentException) {
        throw LedgerFailureException(LedgerFailure.InvalidPayload(0, "The Solana transaction is not base64"))
    }

    private class SignedWire(val signatureBase58: String, val transactionBase64: String)

    private companion object {
        const val SIGNATURE = "signature"
        const val TRANSACTION = "transaction"
    }
}
