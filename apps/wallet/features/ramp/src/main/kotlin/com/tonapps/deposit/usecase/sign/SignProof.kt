package com.tonapps.deposit.usecase.sign

import com.tonapps.blockchain.ton.connect.TONProof
import com.tonapps.blockchain.ton.connect.TONProof.Request
import com.tonapps.chainkit.core.chain.model.MessageType
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Derivation
import com.tonapps.chainkit.core.chain.model.account.PubKey
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.localization.Localization
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import uikit.extensions.addForResult
import uikit.navigation.NavigationActivity
import java.util.concurrent.CancellationException
import com.tonapps.blockchain.ton.connect.TCDomain
import com.tonapps.blockchain.ton.connect.TCAddress
import com.tonapps.core.requestPrivateKey
import com.tonapps.deposit.screens.send.SendException
import com.tonapps.extensions.toHex
import io.ktor.util.encodeBase64
import uikit.base.BaseFragment
import java.math.BigInteger

class SignProof(
    private val accountRepository: AccountRepository,
    private val mcAccountRepository: McAccountRepository,
    private val chainKitProvider: ChainKitProvider,
    private val passcodeManager: PasscodeManager,
    private val rnLegacy: RNLegacy,
) {

    interface Delegate {
        fun openLedgerScreen(
            domain: String,
            timestamp: BigInteger,
            payload: String,
            walletId: String
        ): Pair<BaseFragment, String>
    }

    suspend fun ledger(
        activity: NavigationActivity,
        wallet: WalletEntity,
        payload: String,
        domain: String,
    ): TONProof.Result {
        if (activity !is Delegate) {
            throw IllegalArgumentException("Activity must implement SignTransaction.Delegate")
        }

        val timestamp = System.currentTimeMillis() / 1000L
        val (fragment, key) = activity.openLedgerScreen(
            domain = domain,
            timestamp = timestamp.toBigInteger(),
            payload = payload,
            walletId = wallet.id
        )
        val result = activity.addForResult(fragment)
        val signature = result.getByteArray(key)
        if (signature == null || signature.isEmpty()) {
            throw CancellationException("Ledger cancelled")
        }

        return TONProof.Result(
            timestamp = timestamp,
            domain = TCDomain(domain),
            payload = payload,
            signature = signature.encodeBase64()
        )
    }

    suspend fun keystone(
        activity: NavigationActivity,
        wallet: WalletEntity,
        payload: String,
        domain: String
    ): TONProof.Result {
        if (activity !is SignTransaction.Delegate) {
            throw IllegalArgumentException("Activity must implement SignTransaction.Delegate")
        }

        val request = Request(
            payload = payload,
            domain = TCDomain(domain),
            address = TCAddress(wallet.contract.address)
        )

        val (fragment, contract) = activity.showKeystoneSignScreen(
            unsignedBody = request.message.toHex(),
            isTransaction = false,
            address = wallet.address,
            keystone = wallet.keystone ?: throw IllegalArgumentException("Keystone is not set")
        )
        val result = activity.addForResult(fragment)
        val signature = contract.parseResult(result)

        return TONProof.Result(
            timestamp = request.timestamp,
            domain = request.domain,
            payload = request.payload,
            signature = signature.toByteArray().encodeBase64()
        )
    }

    suspend fun default(
        activity: NavigationActivity,
        wallet: WalletEntity,
        payload: String,
        domain: String
    ): TONProof.Result {
        if (!domain.contains(".")) {
            throw IllegalArgumentException("Invalid domain")
        }

        if (!wallet.hasPrivateKey) {
            throw SignException.UnsupportedWalletType(wallet.type)
        }

        val isValidPasscode = passcodeManager.confirmation(activity, activity.getString(Localization.app_name))
        if (!isValidPasscode) {
            throw CancellationException("Passcode cancelled")
        }

        val privateKey = accountRepository.requestPrivateKey(activity, rnLegacy, wallet.id) ?: throw SendException.UnableSendTransaction()

        return TONProof.sign(
            address = wallet.contract.address,
            secretKey = privateKey,
            payload = payload,
            domain = domain
        )
    }

    suspend fun multichain(
        activity: NavigationActivity,
        wallet: WalletEntity,
        payload: String,
        domain: String,
    ): TONProof.Result {
        if (!domain.contains(".")) {
            throw IllegalArgumentException("Invalid domain")
        }

        val tonAccount = mcAccountRepository.getTonAccount(wallet.id)
            ?: throw SendException.UnableSendTransaction()

        val chain = Chain.Ton.Mainnet
        val account = Account(
            address = Address.force(tonAccount.displayAddress, chain),
            asset = chain.toAsset(),
            derivation = Derivation.DefaultPath,
            publicKey = PubKey(tonAccount.publicKey, tonAccount.segwitPublicKey),
        )

        val timestamp = System.currentTimeMillis() / 1000L
        val messageJson = buildJsonObject {
            put("address", JsonPrimitive(tonAccount.displayAddress))
            put("domain", JsonPrimitive(domain))
            put("payload", JsonPrimitive(payload))
            put("timestamp", JsonPrimitive(timestamp))
        }.toString()

        val signResult = passcodeManager.unlockMultichainVault(activity) { coder ->
            val cryptoWallet = mcAccountRepository.getCryptoWallet(wallet.id, coder)
                ?: throw SendException.UnableSendTransaction()
            chainKitProvider.signMessage(messageJson, MessageType.TonConnect, account, cryptoWallet)
                ?: throw SendException.UnableSendTransaction()
        }
        signResult.error?.let { throw IllegalStateException("MC TonConnect sign failed: $it") }
        val signedJson = signResult.unwrap()
        val signedObj = Json.parseToJsonElement(signedJson).jsonObject
        val signature = signedObj["signature"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Missing signature in MC TonConnect response")
        val resultTimestamp = signedObj["timestamp"]?.jsonPrimitive?.longOrNull ?: timestamp

        return TONProof.Result(
            timestamp = resultTimestamp,
            domain = TCDomain(domain),
            payload = payload,
            signature = signature,
        )
    }

}