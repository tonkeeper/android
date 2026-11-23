package com.tonapps.deposit.usecase.sign

import android.content.Context
import com.tonapps.blockchain.model.legacy.errors.SignatureDisabledException
import com.tonapps.blockchain.ton.connect.TONProof
import com.tonapps.blockchain.tron.TronTransaction
import com.tonapps.ledger.ton.Transaction
import com.tonapps.time.TimeProvider
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.data.ledger.signing.LedgerSigningInteractor
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.passcode.PasscodeReason
import com.tonapps.wallet.data.passcode.WalletUnlocker
import com.tonapps.wallet.data.plugins.PluginsRepository
import com.tonapps.wallet.data.rn.RNLegacy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ton.bitstring.BitString
import org.ton.cell.Cell
import uikit.extensions.activity

class SignUseCase(
    private val accountRepository: AccountRepository,
    private val mcAccountRepository: McAccountRepository,
    private val chainKitProvider: ChainKitProvider,
    private val passcodeManager: PasscodeManager,
    private val walletUnlocker: WalletUnlocker,
    private val rnLegacy: RNLegacy,
    private val pluginsRepository: PluginsRepository,
    timeProvider: TimeProvider,
    ledgerSigner: LedgerSigningInteractor,
) {

    private val signTransaction = SignTransaction(accountRepository, mcAccountRepository, passcodeManager, walletUnlocker, rnLegacy, ledgerSigner)
    private val signProof = SignProof(accountRepository, mcAccountRepository, chainKitProvider, passcodeManager, walletUnlocker, rnLegacy, timeProvider, ledgerSigner)

    suspend operator fun invoke(
        context: Context,
        wallet: WalletEntity,
        bytes: ByteArray,
        reason: PasscodeReason,
    ): ByteArray {
        val activity = context.activity ?: throw IllegalArgumentException("Context must be an Activity")
        return if (wallet.type == WalletType.Multichain) {
            signTransaction.multichain(wallet, bytes, reason)
        } else {
            signTransaction.default(activity, wallet, bytes, reason)
        }
    }

    suspend operator fun invoke(
        context: Context,
        wallet: WalletEntity,
        domain: String,
        payload: String,
    ): TONProof.Result = withContext(Dispatchers.Main) {
        val activity = context.activity ?: throw IllegalArgumentException("Context must be an Activity")
        if (wallet.type == WalletType.Keystone) {
            signProof.keystone(activity, wallet, payload, domain)
        } else if (wallet.signsWithLedger) {
            signProof.ledger(activity, wallet, payload, domain)
        } else if (wallet.type == WalletType.Multichain) {
            signProof.multichain(wallet, payload, domain)
        } else if (wallet.hasPrivateKey) {
            signProof.default(activity, wallet, payload, domain)
        } else {
            throw SignException.UnsupportedWalletType(wallet.type)
        }
    }

    suspend operator fun invoke(
        context: Context,
        wallet: WalletEntity,
        seqNo: Int,
        ledgerTransaction: Transaction,
        transactionIndex: Int = 0,
        transactionCount: Int = 1,
    ): Cell = withContext(Dispatchers.Main) {
        requireSignatureEnabled(wallet)
        val contract = wallet.contract
        val activity = context.activity ?: throw IllegalArgumentException("Context must be an Activity")
        val signedBody = signTransaction.ledger(
            activity = activity,
            wallet = wallet,
            ledgerTransaction = ledgerTransaction,
            transactionIndex = transactionIndex,
            transactionCount = transactionCount
        )

        contract.createTransferMessageCell(
            address = contract.address,
            seqno = seqNo,
            transferBody = signedBody
        )
    }

    suspend operator fun invoke(
        context: Context,
        wallet: WalletEntity,
        unsignedBody: Cell,
        reason: PasscodeReason,
    ): BitString {
        requireSignatureEnabled(wallet)
        val activity = context.activity ?: throw IllegalArgumentException("Context must be an Activity")
        return signTransaction.requestSignature(activity, wallet, unsignedBody, reason)
    }

    suspend operator fun invoke(
        context: Context,
        wallet: WalletEntity,
        unsignedBody: Cell,
        seqNo: Int,
        reason: PasscodeReason,
        ledgerTransaction: Transaction? = null,
        transactionIndex: Int = 0,
        transactionCount: Int = 1,
    ): Cell = withContext(Dispatchers.Main) {
        requireSignatureEnabled(wallet)
        val activity = context.activity ?: throw IllegalArgumentException("Context must be an Activity")
        val contract = wallet.contract

        val signedBody = if (ledgerTransaction != null) {
            signTransaction.ledger(
                activity = activity,
                wallet = wallet,
                ledgerTransaction = ledgerTransaction,
                transactionIndex = transactionIndex,
                transactionCount = transactionCount,
            )
        } else {
            signTransaction.requestSignedMessage(activity, wallet, unsignedBody, reason)
        }

        contract.createTransferMessageCell(
            address = contract.address,
            seqno = seqNo,
            transferBody = signedBody
        )
    }

    suspend operator fun invoke(
        context: Context,
        wallet: WalletEntity,
        transaction: TronTransaction,
        reason: PasscodeReason,
    ): TronTransaction = withContext(Dispatchers.Main) {
        val activity =
            context.activity ?: throw IllegalArgumentException("Context must be an Activity")

        signTransaction.tron(activity, wallet, transaction, reason)
    }

    /**
     * Confirms passcode once, then runs [block] with an unlocked signer for multiple txs.
     */
    suspend fun <T> unlockAndSign(
        context: Context,
        wallet: WalletEntity,
        reason: PasscodeReason,
        block: suspend UnlockedWalletSigner.() -> T,
    ): T = withContext(Dispatchers.Main) {
        val activity =
            context.activity ?: throw IllegalArgumentException("Context must be an Activity")
        val signer = signTransaction.unlock(activity, wallet, reason)
        signer.block()
    }

    // Before the passcode: a TON transaction from a wallet whose 2FA turned off signing by key would only be rejected.
    // Public for callers that sign TON alongside other chains under one passcode.
    suspend fun requireSignatureEnabled(wallet: WalletEntity) {
        if (pluginsRepository.isSignatureDisabled(wallet)) {
            throw SignatureDisabledException()
        }
    }
}
