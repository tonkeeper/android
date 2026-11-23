package com.tonapps.wallet.data.ledger.signing

import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.core
import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerConfirmDetail
import com.tonapps.ledgerkit.LedgerConfirmKind
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.apdu.Bip32Path
import com.tonapps.ledgerkit.app.btc.BtcApp
import com.tonapps.ledgerkit.app.btc.BtcExtendedPublicKey
import com.tonapps.ledgerkit.app.btc.BtcPsbtV2
import com.tonapps.ledgerkit.app.btc.BtcRawTransaction
import com.tonapps.ledgerkit.app.btc.BtcScripts
import com.tonapps.ledgerkit.app.btc.BtcSpendInput
import com.tonapps.ledgerkit.app.btc.BtcSpendOutput
import com.tonapps.ledgerkit.app.btc.BtcUnsignedTransaction
import com.tonapps.ledgerkit.app.btc.BtcWalletPolicy
import com.tonapps.ledgerkit.controller.LedgerScope
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.data.ledger.LedgerDerivation
import com.trustwallet.core.BitcoinScript
import com.trustwallet.core.HDWallet
import com.trustwallet.core.bitcoin.OutPoint
import com.trustwallet.core.bitcoin.SigningInput
import com.trustwallet.core.bitcoin.SigningOutput
import com.trustwallet.core.bitcoin.Transaction
import com.trustwallet.core.bitcoin.TransactionInput
import com.trustwallet.core.bitcoin.TransactionOutput
import kotlinx.coroutines.CancellationException
import okio.ByteString
import okio.ByteString.Companion.toByteString

internal class BitcoinLedgerSigner(private val chainKitProvider: ChainKitProvider) {

    class PlannedSpend internal constructor(
        val inputs: List<PlannedInput>,
        val outputs: List<PlannedOutput>,
    )

    class PlannedInput internal constructor(
        val previousTxidInternal: ByteArray,
        val previousIndex: Long,
        val amount: Long,
        val scriptPubKey: ByteArray,
        val path: Bip32Path,
        val previousTransaction: ByteArray?,
    )

    class PlannedOutput internal constructor(
        val amount: Long,
        val script: ByteArray,
        val isOwnChange: Boolean,
    )

    suspend fun plan(account: Account, accountIndex: Int, input: ByteArray): PlannedSpend {
        val signingInput = SigningInput.ADAPTER.decode(input)
        val plan = signingInput.plan ?: throw LedgerFailureException(LedgerFailure.Unsupported("Bitcoin transaction has no plan"))
        if (plan.utxos.isEmpty()) {
            throw LedgerFailureException(LedgerFailure.InvalidPayload(0, "Bitcoin transaction has no inputs"))
        }
        val unsupportedShape = signingInput.output_op_return.size > 0 ||
            signingInput.extra_outputs.isNotEmpty() ||
            signingInput.lock_time != 0 ||
            signingInput.hash_type !in SUPPORTED_HASH_TYPES
        if (unsupportedShape) {
            throw LedgerFailureException(LedgerFailure.Unsupported("Bitcoin transaction with extra outputs, lock time or custom sighash"))
        }
        val unlocked = chainKitProvider.bitcoinUnlockedUtxos(account)
        val accountPath = LedgerDerivation.bitcoinAccount(accountIndex)
        val inputs = plan.utxos.map { utxo ->
            val outPoint = utxo.out_point ?: throw LedgerFailureException(LedgerFailure.InvalidPayload(0, "UTXO without outpoint"))
            val txidInternal = outPoint.hash.toByteArray()
            val txidDisplay = txidInternal.reversedArray().toHexString()
            val source = unlocked.firstOrNull { it.txid == txidDisplay && it.vout == outPoint.index }
                ?: throw LedgerFailureException(LedgerFailure.InvalidPayload(0, "Bitcoin balance changed, try again"))
            val path = source.path.takeIf { it.isNotBlank() }?.let(Bip32Path::parse)
                ?: LedgerDerivation.bitcoinAddress(accountIndex, 0, 0)
            if (path.depth != accountPath.depth + 2 || path.indices.take(accountPath.depth) != accountPath.indices) {
                throw LedgerFailureException(LedgerFailure.Unsupported("UTXO path $path is outside the Ledger account"))
            }
            val previous = previousTransaction(account, txidDisplay, txidInternal)
            val previousOutput = previous?.let { BtcRawTransaction.parse(it).outputs.getOrNull(outPoint.index) }
            if (previousOutput != null && previousOutput.amount != utxo.amount) {
                throw LedgerFailureException(LedgerFailure.InvalidPayload(0, "UTXO amount does not match its transaction"))
            }
            PlannedInput(
                previousTxidInternal = txidInternal,
                previousIndex = outPoint.index.toLong(),
                amount = utxo.amount,
                scriptPubKey = previousOutput?.script ?: utxo.script.toByteArray(),
                path = path,
                previousTransaction = previous?.takeIf { it.size <= MAX_NON_WITNESS_UTXO_BYTES },
            )
        }
        val outputs = buildList {
            add(PlannedOutput(plan.amount, lockScript(signingInput.to_address), isOwnChange = false))
            if (plan.change > 0) {
                add(PlannedOutput(plan.change, lockScript(signingInput.change_address), isOwnChange = true))
            }
        }
        val fee = inputs.sumOf { it.amount } - outputs.sumOf { it.amount }
        if (fee != plan.fee) {
            throw LedgerFailureException(LedgerFailure.InvalidPayload(0, "Bitcoin fee does not match the plan"))
        }
        return PlannedSpend(inputs, outputs)
    }

    suspend fun sign(
        scope: LedgerScope,
        spend: PlannedSpend,
        accountIndex: Int,
        expectedFingerprint: String?,
        expectedZpub: String,
        step: Int,
        totalSteps: Int,
    ): ByteArray {
        val info = scope.ensureApp(LedgerAppId.Bitcoin)
        if (!info.isAtLeast(BtcApp.MIN_APP_VERSION)) {
            throw LedgerFailureException(LedgerFailure.AppUpdateRequired(LedgerAppId.Bitcoin, info.version, BtcApp.MIN_APP_VERSION))
        }
        val app = BtcApp(scope.session)
        val identity = identity(app, accountIndex, expectedFingerprint, expectedZpub)
        val zpub = identity.accountXpub.toZpub()
        val publicKeys = spend.inputs.map { it.path }.distinct()
            .associateWith { path -> childKey(zpub, path) ?: app.getExtendedPublicKey(path, display = false).publicKey }
        val receivePath = LedgerDerivation.bitcoinAddress(accountIndex, 0, 0)
        val receiveKey = publicKeys[receivePath]
            ?: childKey(zpub, receivePath)
            ?: app.getExtendedPublicKey(receivePath, display = false).publicKey
        val unsigned = BtcUnsignedTransaction(
            inputs = spend.inputs.map { input ->
                val publicKey = publicKeys.getValue(input.path)
                if (!BtcScripts.p2wpkhScript(publicKey).contentEquals(input.scriptPubKey)) {
                    throw LedgerFailureException(LedgerFailure.AddressMismatch(LedgerAppId.Bitcoin))
                }
                BtcSpendInput(
                    previousTxidInternal = input.previousTxidInternal,
                    previousIndex = input.previousIndex,
                    amount = input.amount,
                    scriptPubKey = input.scriptPubKey,
                    publicKey = publicKey,
                    path = input.path,
                    previousTransaction = input.previousTransaction,
                )
            },
            outputs = spend.outputs.map { output ->
                val ownKey = receiveKey.takeIf { output.isOwnChange && BtcScripts.p2wpkhScript(it).contentEquals(output.script) }
                BtcSpendOutput(
                    amount = output.amount,
                    script = output.script,
                    ownPublicKey = ownKey,
                    ownPath = ownKey?.let { receivePath },
                )
            },
        )
        val policy = BtcWalletPolicy.nativeSegwit(identity.fingerprint, accountIndex, identity.accountXpub.encode())
        val change = unsigned.outputs.firstOrNull { it.ownPublicKey != null }
        val detail = change?.ownPublicKey?.let { LedgerConfirmDetail.BitcoinChange(change.amount, BtcScripts.p2wpkhAddress(it)) }
        scope.report(LedgerProgress.Confirm(LedgerAppId.Bitcoin, LedgerConfirmKind.Transaction, step, totalSteps, detail))
        val signatures = app.signPsbt(BtcPsbtV2.build(unsigned, identity.fingerprint), policy)
        val byInput = unsigned.inputs.indices.associateWith { index ->
            val signature = signatures.firstOrNull { it.inputIndex == index }
                ?: throw LedgerFailureException(LedgerFailure.Unexpected("The Bitcoin app did not sign input $index"))
            if (!signature.publicKey.contentEquals(unsigned.inputs[index].publicKey)) {
                throw LedgerFailureException(LedgerFailure.Unexpected("The Bitcoin app signed input $index with another key"))
            }
            signature.signature
        }
        val signed = unsigned.finalize(byInput)
        return SigningOutput(
            transaction = Transaction(
                version = unsigned.version.toInt(),
                lockTime = unsigned.lockTime.toInt(),
                inputs = unsigned.inputs.map { input ->
                    TransactionInput(
                        previousOutput = OutPoint(
                            hash = input.previousTxidInternal.toByteString(),
                            index = input.previousIndex.toInt(),
                            sequence = input.sequence.toInt(),
                        ),
                        sequence = input.sequence.toInt(),
                        script = ByteString.EMPTY,
                    )
                },
                outputs = unsigned.outputs.map { TransactionOutput(value_ = it.amount, script = it.script.toByteString()) },
            ),
            encoded = signed.serialize().toByteString(),
            transaction_id = signed.txidDisplay,
        ).encode()
    }

    suspend fun verifyReceiveAddress(
        scope: LedgerScope,
        accountIndex: Int,
        expectedFingerprint: String?,
        expectedZpub: String,
        expectedAddress: String,
    ) {
        val info = scope.ensureApp(LedgerAppId.Bitcoin)
        if (!info.isAtLeast(BtcApp.MIN_APP_VERSION)) {
            throw LedgerFailureException(LedgerFailure.AppUpdateRequired(LedgerAppId.Bitcoin, info.version, BtcApp.MIN_APP_VERSION))
        }
        val app = BtcApp(scope.session)
        val identity = identity(app, accountIndex, expectedFingerprint, expectedZpub)
        val policy = BtcWalletPolicy.nativeSegwit(identity.fingerprint, accountIndex, identity.accountXpub.encode())
        val receiveKey = app.getExtendedPublicKey(LedgerDerivation.bitcoinAddress(accountIndex, 0, 0), display = false).publicKey
        if (BtcScripts.p2wpkhAddress(receiveKey) != expectedAddress) {
            throw LedgerFailureException(LedgerFailure.AddressMismatch(LedgerAppId.Bitcoin))
        }
        scope.report(LedgerProgress.VerifyAddress(LedgerAppId.Bitcoin, expectedAddress))
        val shown = app.getWalletAddress(policy, change = false, addressIndex = 0, display = true)
        if (shown != expectedAddress) {
            throw LedgerFailureException(LedgerFailure.AddressMismatch(LedgerAppId.Bitcoin))
        }
    }

    private suspend fun identity(app: BtcApp, accountIndex: Int, expectedFingerprint: String?, expectedZpub: String): Identity {
        if (expectedFingerprint.isNullOrEmpty() || expectedZpub.isEmpty()) {
            throw LedgerFailureException(LedgerFailure.AddressMismatch(LedgerAppId.Bitcoin))
        }
        val fingerprint = app.getMasterFingerprint()
        if (!expectedFingerprint.equals(fingerprint.toHexString(), ignoreCase = true)) {
            throw LedgerFailureException(LedgerFailure.AddressMismatch(LedgerAppId.Bitcoin))
        }
        val accountXpub = app.getExtendedPublicKey(LedgerDerivation.bitcoinAccount(accountIndex), display = false)
        if (accountXpub.toZpub() != expectedZpub) {
            throw LedgerFailureException(LedgerFailure.AddressMismatch(LedgerAppId.Bitcoin))
        }
        return Identity(fingerprint, accountXpub)
    }

    private fun childKey(zpub: String, path: Bip32Path): ByteArray? = try {
        HDWallet.getPublicKeyFromExtended(zpub, Chain.Bitcoin.Mainnet.coin.core, path.toString())?.data?.takeIf { it.size == COMPRESSED_KEY_LENGTH }
    } catch (_: RuntimeException) {
        null
    }

    private suspend fun previousTransaction(account: Account, txidDisplay: String, txidInternal: ByteArray): ByteArray? {
        repeat(PREVIOUS_TX_ATTEMPTS) {
            previousTransactionOnce(account, txidDisplay, txidInternal)?.let { return it }
        }
        return null
    }

    private suspend fun previousTransactionOnce(account: Account, txidDisplay: String, txidInternal: ByteArray): ByteArray? {
        val hex = try {
            chainKitProvider.findTransactionMeta(account, txidDisplay)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        } ?: return null
        val raw = hex.takeIf { it.isHexEncoded() }?.hexToBytes() ?: return null
        val parsed = try {
            BtcRawTransaction.parse(raw)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return raw.takeIf { parsed.txidInternal.contentEquals(txidInternal) }
    }

    private fun lockScript(address: String): ByteArray {
        val script = BitcoinScript.lockScriptForAddress(address, Chain.Bitcoin.Mainnet.coin.core).data
        if (script.isEmpty()) {
            throw LedgerFailureException(LedgerFailure.InvalidPayload(0, "Unsupported Bitcoin address"))
        }
        return script
    }

    private class Identity(val fingerprint: ByteArray, val accountXpub: BtcExtendedPublicKey)

    private companion object {
        val SUPPORTED_HASH_TYPES = setOf(0, 1)
        const val MAX_NON_WITNESS_UTXO_BYTES = 50_000
        const val PREVIOUS_TX_ATTEMPTS = 2
        const val COMPRESSED_KEY_LENGTH = 33
    }
}
