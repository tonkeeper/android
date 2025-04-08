package com.tonapps.tonkeeper.usecase.sign

import android.content.Context
import com.tonapps.blockchain.ton.connect.TONProof
import com.tonapps.ledger.ton.Transaction
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.rn.RNLegacy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ton.bitstring.BitString
import org.ton.cell.Cell
import uikit.extensions.activity
import uikit.navigation.NavigationActivity

class SignUseCase(
    private val accountRepository: AccountRepository,
    private val passcodeManager: PasscodeManager,
    private val rnLegacy: RNLegacy,
) {

    private val signTransaction = SignTransaction(accountRepository, passcodeManager, rnLegacy)
    private val signProof = SignProof(accountRepository, passcodeManager, rnLegacy)

    suspend operator fun invoke(
        context: Context,
        wallet: WalletEntity,
        bytes: ByteArray
    ): ByteArray {
        val activity = context.activity ?: throw IllegalArgumentException("Context must be an Activity")
        return signTransaction.default(activity, wallet, bytes)
    }

    suspend operator fun invoke(
        context: Context,
        wallet: WalletEntity,
        domain: String,
        payload: String,
    ): TONProof.Result = withContext(Dispatchers.Main) {
        val activity = context.activity ?: throw IllegalArgumentException("Context must be an Activity")
        if (wallet.type == Wallet.Type.Keystone) {
            signProof.keystone(activity, wallet, payload, domain)
        } else if (wallet.type == Wallet.Type.Ledger) {
            signProof.ledger(activity, wallet, payload, domain)
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
        unsignedBody: Cell
    ): BitString {
        val activity = context.activity ?: throw IllegalArgumentException("Context must be an Activity")
        return signTransaction.requestSignature(activity, wallet, unsignedBody)
    }

    suspend operator fun invoke(
        context: Context,
        wallet: WalletEntity,
        unsignedBody: Cell,
        seqNo: Int,
        ledgerTransaction: Transaction? = null,
        transactionIndex: Int = 0,
        transactionCount: Int = 1,
    ): Cell = withContext(Dispatchers.Main) {
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
            signTransaction.requestSignedMessage(activity, wallet, unsignedBody)
        }

        contract.createTransferMessageCell(
            address = contract.address,
            seqno = seqNo,
            transferBody = signedBody
        )
    }

}