package com.tonapps.tonkeeper.usecase.sign

import android.util.Log
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519.sign
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.ledger.ton.Transaction
import com.tonapps.security.tryCallGC
import com.tonapps.tonkeeper.core.signer.SignerHelper
import com.tonapps.tonkeeper.extensions.requestPrivateKey
import com.tonapps.tonkeeper.extensions.sign
import com.tonapps.tonkeeper.ui.screen.external.qr.keystone.sign.KeystoneSignScreen
import com.tonapps.tonkeeper.ui.screen.external.qr.signer.sign.SignerSignScreen
import com.tonapps.tonkeeper.ui.screen.ledger.sign.LedgerSignScreen
import com.tonapps.tonkeeper.ui.screen.send.main.SendException
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ton.bitstring.BitString
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.crypto.hex
import uikit.extensions.addForResult
import uikit.navigation.NavigationActivity
import java.util.concurrent.CancellationException

class SignTransaction(
    private val accountRepository: AccountRepository,
    private val passcodeManager: PasscodeManager,
    private val rnLegacy: RNLegacy,
) {

    suspend fun ledger(
        activity: NavigationActivity,
        wallet: WalletEntity,
        ledgerTransaction: Transaction,
        transactionIndex: Int,
        transactionCount: Int
    ): Cell {
        val fragment = LedgerSignScreen.newInstance(
            transaction = ledgerTransaction,
            walletId = wallet.id,
            transactionIndex = transactionIndex,
            transactionCount = transactionCount
        )
        val result = activity.addForResult(fragment)
        val signerMessage = result.getByteArray(LedgerSignScreen.SIGNED_MESSAGE)
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
            Wallet.Type.SignerQR -> signerQR(activity, wallet, unsignedBody)
            Wallet.Type.Signer -> signerApp(activity, wallet, unsignedBody)
            Wallet.Type.Default, Wallet.Type.Testnet, Wallet.Type.Lockup -> default(activity, wallet, unsignedBody)
            Wallet.Type.Keystone -> keystone(activity, wallet, unsignedBody)
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
        val fragment = KeystoneSignScreen.newInstance(
            unsignedBody = unsignedBody.hex(),
            isTransaction = true,
            address = wallet.address,
            keystone = wallet.keystone ?: throw IllegalArgumentException("Keystone is not set")
        )
        val result = activity.addForResult(fragment)
        return fragment.contract.parseResult(result)
    }

    private suspend fun signerQR(
        activity: NavigationActivity,
        wallet: WalletEntity,
        unsignedBody: Cell
    ): BitString {
        val fragment = SignerSignScreen.newInstance(
            publicKey = wallet.publicKey,
            unsignedBody = unsignedBody
        )
        val result = activity.addForResult(fragment)
        return fragment.contract.parseResult(result)
    }

    private suspend fun signerApp(
        activity: NavigationActivity,
        wallet: WalletEntity,
        unsignedBody: Cell
    ): BitString {
        val hash = SignerHelper.invoke(activity, wallet.publicKey, unsignedBody)
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
            throw SendException.WrongPasscode()
        }
        val privateKey = accountRepository.requestPrivateKey(activity, rnLegacy, wallet.id) ?: throw SendException.UnableSendTransaction()
        val hash = privateKey.sign(unsignedBody.hash())
        BitString(hash)
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
            throw SendException.WrongPasscode()
        }
        accountRepository.sign(activity, rnLegacy, wallet.id, bytes)
    }
}