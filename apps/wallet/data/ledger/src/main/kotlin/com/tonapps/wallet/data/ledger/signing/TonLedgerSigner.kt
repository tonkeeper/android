package com.tonapps.wallet.data.ledger.signing

import com.tonapps.blockchain.ton.contract.WalletV4R2Contract
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.chainkit.core.chain.model.account.LedgerWallet
import com.tonapps.ledger.ton.AccountPath
import com.tonapps.ledger.ton.LedgerKnownJettons
import com.tonapps.ledger.ton.TonPayloadFormat
import com.tonapps.ledger.ton.TonTransport
import com.tonapps.ledger.ton.Transaction
import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerConfirmKind
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.controller.LedgerScope
import com.tonapps.ledgerkit.session.LedgerAppInfo
import com.tonapps.wallet.data.ledger.ton.LinkTonTransport
import com.tonapps.wallet.data.ledger.ton.TON_PARSE_FAILED
import com.tonapps.wallet.data.ledger.ton.TonProofHash
import com.tonapps.wallet.data.ledger.ton.TonTransactionMapper
import com.tonapps.wallet.data.ledger.ton.mapTonTransportErrors
import com.trustwallet.core.theopennetwork.SigningInput
import com.trustwallet.core.theopennetwork.SigningOutput
import okio.ByteString.Companion.toByteString
import org.ton.cell.Cell
import kotlinx.io.bytestring.ByteString as IoByteString
import org.ton.kotlin.crypto.PublicKeyEd25519
import java.math.BigInteger

internal class TonLedgerSigner(private val clock: () -> Long = System::currentTimeMillis) {

    suspend fun signChainkitInput(
        scope: LedgerScope,
        accountIndex: Int,
        publicKey: ByteArray,
        input: ByteArray,
        waitedSeconds: Long,
        step: Int,
        totalSteps: Int,
        jettonMaster: String? = null,
    ): ByteArray {
        val signingInput = SigningInput.ADAPTER.decode(input)
        val mapped = TonTransactionMapper.map(signingInput, jettonMaster)
        val transaction = mapped.copy(timeout = mapped.timeout + waitedSeconds.toInt() + HARDWARE_EXPIRY_EXTENSION_SECONDS)
        val signedBody = signTransaction(scope, accountIndex, transaction, step, totalSteps)
        val contract = WalletV4R2Contract(publicKey = PublicKeyEd25519(IoByteString(publicKey)))
        val message = contract.createTransferMessageCell(contract.address, transaction.seqno, signedBody)
        return SigningOutput(
            encoded = message.base64(),
            hash = message.hash().toByteArray().toByteString(),
        ).encode()
    }

    suspend fun signTransaction(
        scope: LedgerScope,
        accountIndex: Int,
        transaction: Transaction,
        step: Int,
        totalSteps: Int,
    ): Cell {
        requireNotExpired(transaction)
        val info = scope.ensureApp(LedgerAppId.Ton)
        val supported = transaction.withoutUnsupportedHints(info)
        requireVersion(info, requiredVersion(supported, info.version))
        scope.report(LedgerProgress.Confirm(LedgerAppId.Ton, LedgerConfirmKind.Transaction, step, totalSteps))
        val withoutHint = supported.withoutKnownJetton()
        val signed = try {
            signOnDevice(scope, accountIndex, supported)
        } catch (e: LedgerFailureException) {
            val failure = e.failure
            if (withoutHint === supported || failure !is LedgerFailure.InvalidPayload || failure.statusWord != TON_PARSE_FAILED) {
                throw e
            }
            signOnDevice(scope, accountIndex, withoutHint)
        }
        requireNotExpired(transaction)
        return signed
    }

    private fun requireNotExpired(transaction: Transaction) {
        if (transaction.timeout <= clock() / MILLIS_PER_SECOND) {
            throw LedgerFailureException(LedgerFailure.RequestExpired)
        }
    }

    private suspend fun signOnDevice(scope: LedgerScope, accountIndex: Int, transaction: Transaction): Cell =
        mapTonTransportErrors {
            TonTransport(LinkTonTransport(scope.session)).signTransaction(AccountPath(accountIndex), transaction)
        }

    private fun Transaction.withoutKnownJetton(): Transaction {
        val jetton = payload as? TonPayloadFormat.JettonTransfer ?: return this
        return if (jetton.knownJetton == null) { this } else { copy(payload = jetton.copy(knownJetton = null)) }
    }

    suspend fun signProof(
        scope: LedgerScope,
        accountIndex: Int,
        publicKey: ByteArray,
        domain: String,
        timestamp: Long,
        payload: String,
    ): ByteArray = checkedDappProof(domain, timestamp, payload).let {
        signDappProof(scope, accountIndex, publicKey, domain, timestamp, payload)
    }

    private fun checkedDappProof(domain: String, timestamp: Long, payload: String) {
        val reserved = domain == LedgerWallet.PROOF_DOMAIN || payload == LedgerWallet.PROOF_PAYLOAD || timestamp <= 0
        if (reserved) {
            throw LedgerFailureException(LedgerFailure.InvalidPayload(0, "The proof request uses reserved parameters"))
        }
        val domainBytes = domain.toByteArray(Charsets.UTF_8).size
        val payloadBytes = payload.toByteArray(Charsets.UTF_8).size
        val tooLong = domainBytes > MAX_PROOF_DOMAIN_BYTES ||
            payloadBytes > MAX_PROOF_PAYLOAD_BYTES ||
            PROOF_FIXED_BYTES + domainBytes + payloadBytes > MAX_APDU_DATA_BYTES
        if (tooLong) {
            throw LedgerFailureException(LedgerFailure.InvalidPayload(0, "The proof request is too long for Ledger"))
        }
    }

    private suspend fun signDappProof(
        scope: LedgerScope,
        accountIndex: Int,
        publicKey: ByteArray,
        domain: String,
        timestamp: Long,
        payload: String,
    ): ByteArray = signAddressProof(
        scope = scope,
        accountIndex = accountIndex,
        publicKey = publicKey,
        domain = domain,
        timestamp = timestamp,
        payload = payload,
        progress = LedgerProgress.Confirm(LedgerAppId.Ton, LedgerConfirmKind.Proof),
    )

    suspend fun signWalletKeyProof(scope: LedgerScope, accountIndex: Int, publicKey: ByteArray, step: Int, totalSteps: Int): ByteArray =
        signAddressProof(
            scope = scope,
            accountIndex = accountIndex,
            publicKey = publicKey,
            domain = LedgerWallet.PROOF_DOMAIN,
            timestamp = LedgerWallet.PROOF_TIMESTAMP,
            payload = LedgerWallet.PROOF_PAYLOAD,
            progress = LedgerProgress.Confirm(LedgerAppId.Ton, LedgerConfirmKind.Wallet, step, totalSteps),
        )

    private suspend fun signAddressProof(
        scope: LedgerScope,
        accountIndex: Int,
        publicKey: ByteArray,
        domain: String,
        timestamp: Long,
        payload: String,
        progress: LedgerProgress,
    ): ByteArray {
        val info = scope.ensureApp(LedgerAppId.Ton)
        requireVersion(info, PROOF_MIN_VERSION)
        scope.report(progress)
        val signature = mapTonTransportErrors {
            TonTransport(LinkTonTransport(scope.session))
                .signAddressProof(AccountPath(accountIndex), domain, BigInteger.valueOf(timestamp), payload)
        }
        val expectedHash = TonProofHash.compute(publicKey, domain, timestamp, payload)
        if (!TonProofHash.verify(publicKey, expectedHash, signature)) {
            throw LedgerFailureException(LedgerFailure.Unexpected("The Ledger signed a different TON proof"))
        }
        return signature
    }

    private fun Transaction.withoutUnsupportedHints(info: LedgerAppInfo): Transaction = when (val hint = payload) {
        is TonPayloadFormat.JettonTransfer -> {
            val known = hint.knownJetton
            if (known == null || info.isAtLeast(LedgerKnownJettons.minAppVersion(known.jettonId))) {
                this
            } else {
                copy(payload = hint.copy(knownJetton = null))
            }
        }
        else -> withWhalesHintSupportedBy(info::isAtLeast)
    }

    private fun requireVersion(info: LedgerAppInfo, requiredVersion: String) {
        if (!info.isAtLeast(requiredVersion)) {
            throw LedgerFailureException(LedgerFailure.AppUpdateRequired(LedgerAppId.Ton, info.version, requiredVersion))
        }
    }

    private fun requiredVersion(transaction: Transaction, installedVersion: String): String {
        val payload = transaction.payload ?: return BASE_MIN_VERSION
        if (installedVersion == BROKEN_PAYLOAD_VERSION) {
            return BROKEN_PAYLOAD_FIX_VERSION
        }
        return when (payload) {
            is TonPayloadFormat.JettonTransfer, is TonPayloadFormat.Comment -> BASE_MIN_VERSION
            is TonPayloadFormat.WhalesDeposit, is TonPayloadFormat.WhalesWithdraw -> TonPayloadFormat.WHALES_MIN_APP_VERSION
            else -> EXTENDED_PAYLOAD_MIN_VERSION
        }
    }

    private companion object {
        const val BASE_MIN_VERSION = "2.0.0"
        const val EXTENDED_PAYLOAD_MIN_VERSION = "2.1.0"
        const val PROOF_MIN_VERSION = "2.1.0"
        const val BROKEN_PAYLOAD_VERSION = "2.7.0"
        const val BROKEN_PAYLOAD_FIX_VERSION = "2.8.1"
        const val HARDWARE_EXPIRY_EXTENSION_SECONDS = 150
        const val MILLIS_PER_SECOND = 1000L
        const val MAX_PROOF_DOMAIN_BYTES = 128
        const val MAX_PROOF_PAYLOAD_BYTES = 128
        const val PROOF_FIXED_BYTES = 34
        const val MAX_APDU_DATA_BYTES = 255
    }
}
