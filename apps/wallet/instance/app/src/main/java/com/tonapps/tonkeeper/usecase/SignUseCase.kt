package com.tonapps.tonkeeper.usecase

import android.content.Context
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.tonkeeper.core.signer.SignerHelper
import com.tonapps.tonkeeper.ui.screen.external.qr.keystone.sign.KeystoneSignScreen
import com.tonapps.tonkeeper.ui.screen.external.qr.signer.sign.SignerSignScreen
import com.tonapps.tonkeeper.ui.screen.send.main.SendException
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ton.bitstring.BitString
import org.ton.cell.Cell
import org.ton.crypto.hex
import uikit.extensions.activity
import uikit.extensions.addForResult
import java.util.concurrent.CancellationException

class SignUseCase(
    private val accountRepository: AccountRepository,
    private val passcodeManager: PasscodeManager
) {

    suspend operator fun invoke(
        context: Context,
        wallet: WalletEntity,
        body: Cell,
    ): BitString = withContext(Dispatchers.Main) {
        when (wallet.type) {
            Wallet.Type.SignerQR -> signerQR(context, wallet, body)
            Wallet.Type.Signer -> signerApp(context, wallet, body)
            Wallet.Type.Default, Wallet.Type.Testnet, Wallet.Type.Lockup -> default(context, wallet, body)
            Wallet.Type.Keystone -> keystone(context, wallet, body)
            else -> {
                throw IllegalArgumentException("Unsupported wallet type: ${wallet.type}")
            }
        }
    }

    private suspend fun keystone(
        context: Context,
        wallet: WalletEntity,
        unsignedBody: Cell
    ): BitString {
        val activity = context.activity ?: throw IllegalArgumentException("Context must be an Activity")
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
        context: Context,
        wallet: WalletEntity,
        unsignedBody: Cell
    ): BitString {
        val activity = context.activity ?: throw IllegalArgumentException("Context must be an Activity")
        val fragment = SignerSignScreen.newInstance(
            publicKey = wallet.publicKey,
            unsignedBody = unsignedBody
        )
        val result = activity.addForResult(fragment)
        return fragment.contract.parseResult(result)
    }

    private suspend fun signerApp(
        context: Context,
        wallet: WalletEntity,
        unsignedBody: Cell
    ): BitString {
        val hash = SignerHelper.invoke(context, wallet.publicKey, unsignedBody)
        return hash ?: throw CancellationException("Signer cancelled")
    }

    private suspend fun default(
        context: Context,
        wallet: WalletEntity,
        unsignedBody: Cell
    ): BitString = withContext(Dispatchers.IO) {
        if (!wallet.hasPrivateKey) {
            throw SendException.UnableSendTransaction()
        }
        val isValidPasscode = passcodeManager.confirmation(context, context.getString(Localization.app_name))
        if (!isValidPasscode) {
            throw SendException.WrongPasscode()
        }
        val privateKey = accountRepository.getPrivateKey(wallet.id)
        val hash = hex(privateKey.sign(unsignedBody.hash()))
        BitString(hash)
    }

}