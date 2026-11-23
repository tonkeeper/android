package com.tonapps.deposit.usecase.sign

import android.content.Context
import android.net.Uri
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.blockchain.tron.TronTransaction
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.core.requestPrivateKey
import com.tonapps.core.sign
import com.tonapps.deposit.screens.send.SendException
import com.tonapps.ledger.ton.Transaction
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.ledger.signing.LedgerSigningInteractor
import com.tonapps.wallet.data.ledger.signing.ledgerCall
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.passcode.PasscodeReason
import com.tonapps.wallet.data.passcode.WalletUnlocker
import com.tonapps.wallet.data.rn.RNLegacy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ton.bitstring.BitString
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.kotlin.crypto.PrivateKeyEd25519
import org.ton.kotlin.crypto.PublicKeyEd25519
import uikit.base.BaseFragment
import uikit.extensions.addForResult
import uikit.navigation.NavigationActivity
import java.util.UUID
import java.util.concurrent.CancellationException

class UnlockedWalletSigner(
    private val wallet: WalletEntity,
    private val tonPrivateKey: PrivateKeyEd25519,
    private val tronPrivateKey: ByteArray?,
) {
    fun signTransfer(
        unsignedBody: Cell,
        seqNo: Int,
    ): Cell {
        val hash = unsignedBody.hash().toByteArray()
        val signature = BitString(tonPrivateKey.signToByteArray(hash))
        val signedBody = wallet.contract.signedBody(signature, unsignedBody)
        return wallet.contract.createTransferMessageCell(
            address = wallet.contract.address,
            seqno = seqNo,
            transferBody = signedBody,
        )
    }

    fun signTron(transaction: TronTransaction): TronTransaction {
        val privateKey = tronPrivateKey ?: throw SendException.UnableSendTransaction()
        return transaction.sign(privateKey)
    }

    fun signMessage(message: ByteArray): ByteArray {
        return tonPrivateKey.signToByteArray(message)
    }
}

class SignTransaction(
    private val accountRepository: AccountRepository,
    private val mcAccountRepository: McAccountRepository,
    private val passcodeManager: PasscodeManager,
    private val walletUnlocker: WalletUnlocker,
    private val rnLegacy: RNLegacy,
    private val ledgerSigner: LedgerSigningInteractor,
) {

    interface Delegate {
        fun showLedgerSignScreen(
            transaction: Transaction,
            walletId: String,
            transactionIndex: Int,
            transactionCount: Int
        ): Pair<BaseFragment, String>

        fun showKeystoneSignScreen(
            requestId: String = UUID.randomUUID().toString(),
            unsignedBody: String,
            isTransaction: Boolean,
            address: String,
            keystone: WalletEntity.Keystone,
        ): Pair<BaseFragment, BaseFragment.ResultContract<ByteArray, BitString>>

        fun newInstance(
            publicKey: PublicKeyEd25519,
            unsignedBody: Cell,
            walletVersion: WalletVersion,
            network: TonNetwork,
            label: String = ""
        ): Pair<BaseFragment, BaseFragment.ResultContract<Uri, BitString>>

        suspend fun invoke(
            context: Context,
            publicKey: PublicKeyEd25519,
            body: Cell,
            walletVersion: WalletVersion,
            network: TonNetwork,
        ): BitString?
    }

    suspend fun unlock(
        activity: NavigationActivity,
        wallet: WalletEntity,
        reason: PasscodeReason,
    ): UnlockedWalletSigner {
        if (!wallet.hasPrivateKey) {
            throw SendException.UnableSendTransaction()
        }
        val isValidPasscode = passcodeManager.confirmation(activity, reason)
        if (!isValidPasscode) {
            throw CancellationException()
        }

        val tonPrivateKey = accountRepository.requestPrivateKey(activity, rnLegacy, wallet.id, reason)
            ?: throw SendException.UnableSendTransaction()
        val tronPrivateKey = accountRepository.getTronPrivateKey(wallet.id)

        return UnlockedWalletSigner(
            wallet = wallet,
            tonPrivateKey = tonPrivateKey,
            tronPrivateKey = tronPrivateKey,
        )
    }

    suspend fun tron(
        activity: NavigationActivity,
        wallet: WalletEntity,
        transaction: TronTransaction,
        reason: PasscodeReason,
    ): TronTransaction {
        return unlock(activity, wallet, reason).signTron(transaction)
    }

    fun multichainTron(
        cryptoWallet: MnemonicWallet,
        transaction: TronTransaction,
    ): TronTransaction {
        return transaction.sign(multichainTronPrivateKey(cryptoWallet))
    }

    fun multichainTronPrivateKey(cryptoWallet: MnemonicWallet): ByteArray {
        return cryptoWallet.getPrivateKey(Chain.Tron.Mainnet).data()
    }

    fun multichainTonPrivateKey(cryptoWallet: MnemonicWallet): PrivateKeyEd25519 {
        return PrivateKeyEd25519(cryptoWallet.getPrivateKey(Chain.Ton.Mainnet).data())
    }

    suspend fun ledger(
        activity: NavigationActivity,
        wallet: WalletEntity,
        ledgerTransaction: Transaction,
        transactionIndex: Int,
        transactionCount: Int
    ): Cell {
        if (wallet.isMultichainLedger) {
            return ledgerCall {
                ledgerSigner.signTonTransaction(wallet.id, ledgerTransaction, transactionIndex + 1, transactionCount)
            }
        }
        if (activity !is Delegate) {
            throw IllegalArgumentException("Activity must implement SignTransaction.Delegate")
        }

        val (fragment, key) = activity.showLedgerSignScreen(
            transaction = ledgerTransaction,
            walletId = wallet.id,
            transactionIndex = transactionIndex,
            transactionCount = transactionCount
        )
        val result = activity.addForResult(fragment)
        val signerMessage = result.getByteArray(key)
        if (signerMessage == null || signerMessage.isEmpty()) {
            throw CancellationException("Ledger cancelled")
        }
        return BagOfCells(signerMessage).first()
    }

    suspend fun requestSignature(
        activity: NavigationActivity,
        wallet: WalletEntity,
        unsignedBody: Cell,
        reason: PasscodeReason,
    ): BitString {
        return when (wallet.type) {
            WalletType.SignerQR -> signerQR(activity, wallet, unsignedBody)
            WalletType.Signer -> signerApp(activity, wallet, unsignedBody)
            WalletType.Default, WalletType.Testnet, WalletType.Lockup -> default(
                activity,
                wallet,
                unsignedBody,
                reason,
            )

            WalletType.Keystone -> keystone(activity, wallet, unsignedBody)
            WalletType.Multichain -> multichain(wallet, unsignedBody, reason)
            else -> {
                throw IllegalArgumentException("Unsupported wallet type: ${wallet.type}")
            }
        }
    }

    suspend fun requestSignedMessage(
        activity: NavigationActivity,
        wallet: WalletEntity,
        unsignedBody: Cell,
        reason: PasscodeReason,
    ): Cell {
        val signature = requestSignature(activity, wallet, unsignedBody, reason)
        return wallet.contract.signedBody(signature, unsignedBody)
    }

    private suspend fun keystone(
        activity: NavigationActivity,
        wallet: WalletEntity,
        unsignedBody: Cell
    ): BitString {
        if (activity !is Delegate) {
            throw IllegalArgumentException("Activity must implement SignTransaction.Delegate")
        }

        val (fragment, contract) = activity.showKeystoneSignScreen(
            unsignedBody = unsignedBody.hex(),
            isTransaction = true,
            address = wallet.address,
            keystone = wallet.keystone ?: throw IllegalArgumentException("Keystone is not set")
        )
        val result = activity.addForResult(fragment)
        return contract.parseResult(result)
    }

    private suspend fun signerQR(
        activity: NavigationActivity,
        wallet: WalletEntity,
        unsignedBody: Cell
    ): BitString {
        if (activity !is Delegate) {
            throw IllegalArgumentException("Activity must implement SignTransaction.Delegate")
        }

        val (fragment, contract) = activity.newInstance(
            publicKey = wallet.publicKey,
            unsignedBody = unsignedBody,
            walletVersion = wallet.version,
            network = wallet.network,
        )
        val result = activity.addForResult(fragment)
        return contract.parseResult(result)
    }

    private suspend fun signerApp(
        activity: NavigationActivity,
        wallet: WalletEntity,
        unsignedBody: Cell
    ): BitString {
        if (activity !is Delegate) {
            throw IllegalArgumentException("Activity must implement SignTransaction.Delegate")
        }

        val hash = activity.invoke(activity, wallet.publicKey, unsignedBody, wallet.version, wallet.network)
        return hash ?: throw CancellationException("Signer cancelled")
    }

    suspend fun default(
        activity: NavigationActivity,
        wallet: WalletEntity,
        unsignedBody: Cell,
        reason: PasscodeReason,
    ): BitString = withContext(Dispatchers.IO) {
        if (!wallet.hasPrivateKey) {
            throw SendException.UnableSendTransaction()
        }
        val isValidPasscode = passcodeManager.confirmation(activity, reason)
        if (!isValidPasscode) {
            throw CancellationException()
        }
        val privateKey = accountRepository.requestPrivateKey(activity, rnLegacy, wallet.id, reason)
            ?: throw SendException.UnableSendTransaction()
        val hash = unsignedBody.hash().toByteArray()
        BitString(privateKey.signToByteArray(hash))
    }

    suspend fun default(
        activity: NavigationActivity,
        wallet: WalletEntity,
        bytes: ByteArray,
        reason: PasscodeReason,
    ): ByteArray = withContext(Dispatchers.IO) {
        if (!wallet.hasPrivateKey) {
            throw SendException.UnableSendTransaction()
        }
        val isValidPasscode = passcodeManager.confirmation(activity, reason)
        if (!isValidPasscode) {
            throw CancellationException()
        }

        accountRepository.sign(activity, rnLegacy, wallet.id, bytes, reason)
    }

    suspend fun multichain(
        wallet: WalletEntity,
        unsignedBody: Cell,
        reason: PasscodeReason,
    ): BitString = withContext(Dispatchers.IO) {
        if (wallet.isMultichainLedger) {
            throw SendException.UnableSendTransaction()
        }
        val privateKey = unlockTonPrivateKey(wallet.id, reason)
        val hash = unsignedBody.hash().toByteArray()
        BitString(privateKey.signToByteArray(hash))
    }

    suspend fun multichain(
        wallet: WalletEntity,
        bytes: ByteArray,
        reason: PasscodeReason,
    ): ByteArray = withContext(Dispatchers.IO) {
        if (wallet.isMultichainLedger) {
            throw SendException.UnableSendTransaction()
        }
        val privateKey = unlockTonPrivateKey(wallet.id, reason)
        privateKey.signToByteArray(bytes)
    }

    private suspend fun unlockTonPrivateKey(walletId: String, reason: PasscodeReason): PrivateKeyEd25519 {
        val cryptoWallet = walletUnlocker.unlockVault(reason) { coder ->
            mcAccountRepository.getCryptoWallet(walletId, coder)
                ?: throw SendException.UnableSendTransaction()
        }
        return multichainTonPrivateKey(cryptoWallet)
    }
}