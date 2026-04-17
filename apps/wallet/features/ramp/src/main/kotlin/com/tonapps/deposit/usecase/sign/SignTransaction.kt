package com.tonapps.deposit.usecase.sign

import android.content.Context
import android.net.Uri
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.SignatureDomain
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.blockchain.tron.TronTransaction
import com.tonapps.core.requestPrivateKey
import com.tonapps.core.sign
import com.tonapps.deposit.screens.send.SendException
import com.tonapps.ledger.ton.Transaction
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ton.api.pub.PublicKeyEd25519
import org.ton.bitstring.BitString
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import uikit.base.BaseFragment
import uikit.extensions.addForResult
import uikit.navigation.NavigationActivity
import java.util.UUID
import java.util.concurrent.CancellationException

class SignTransaction(
    private val accountRepository: AccountRepository,
    private val passcodeManager: PasscodeManager,
    private val rnLegacy: RNLegacy,
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
            label: String = ""
        ): Pair<BaseFragment, BaseFragment.ResultContract<Uri, BitString>>

        suspend fun invoke(
            context: Context,
            publicKey: PublicKeyEd25519,
            body: Cell,
        ): BitString?
    }

    suspend fun tron(
        activity: NavigationActivity,
        wallet: WalletEntity,
        transaction: TronTransaction,
    ): TronTransaction {
        if (!wallet.hasPrivateKey) {
            throw SendException.UnableSendTransaction()
        }
        val isValidPasscode = passcodeManager.confirmation(activity, activity.getString(Localization.app_name))
        if (!isValidPasscode) {
            throw CancellationException()
        }

        val privateKey = accountRepository.getTronPrivateKey(wallet.id)
            ?: throw SendException.UnableSendTransaction()

        return transaction.sign(privateKey)
    }

    suspend fun ledger(
        activity: NavigationActivity,
        wallet: WalletEntity,
        ledgerTransaction: Transaction,
        transactionIndex: Int,
        transactionCount: Int
    ): Cell {
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
        unsignedBody: Cell
    ): BitString {
        return when (wallet.type) {
            WalletType.SignerQR -> signerQR(activity, wallet, unsignedBody)
            WalletType.Signer -> signerApp(activity, wallet, unsignedBody)
            WalletType.Default, WalletType.Tetra, WalletType.Testnet, WalletType.Lockup -> default(
                activity,
                wallet,
                unsignedBody
            )

            WalletType.Keystone -> keystone(activity, wallet, unsignedBody)
            else -> {
                throw IllegalArgumentException("Unsupported wallet type: ${wallet.type}")
            }
        }
    }

    suspend fun requestSignedMessage(
        activity: NavigationActivity,
        wallet: WalletEntity,
        unsignedBody: Cell
    ): Cell {
        val signature = requestSignature(activity, wallet, unsignedBody)
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
            unsignedBody = unsignedBody
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

        val hash = activity.invoke(activity, wallet.publicKey, unsignedBody)
        return hash ?: throw CancellationException("Signer cancelled")
    }

    suspend fun default(
        activity: NavigationActivity,
        wallet: WalletEntity,
        unsignedBody: Cell
    ): BitString = withContext(Dispatchers.IO) {
        if (!wallet.hasPrivateKey) {
            throw SendException.UnableSendTransaction()
        }
        val isValidPasscode = passcodeManager.confirmation(activity, activity.getString(Localization.app_name))
        if (!isValidPasscode) {
            throw CancellationException()
        }
        val privateKey = accountRepository.requestPrivateKey(activity, rnLegacy, wallet.id)
            ?: throw SendException.UnableSendTransaction()
        val hash = unsignedBody.hash().toByteArray()
        val dataToSign = wallet.contract.signatureGlobalId?.let { SignatureDomain.prefixedHash(it, hash) } ?: hash
        BitString(privateKey.sign(dataToSign))
    }

    suspend fun default(
        activity: NavigationActivity,
        wallet: WalletEntity,
        bytes: ByteArray
    ): ByteArray = withContext(Dispatchers.IO) {
        if (!wallet.hasPrivateKey) {
            throw SendException.UnableSendTransaction()
        }
        val isValidPasscode = passcodeManager.confirmation(activity, activity.getString(Localization.app_name))
        if (!isValidPasscode) {
            throw CancellationException()
        }

        accountRepository.sign(activity, rnLegacy, wallet.id, bytes)
    }
}