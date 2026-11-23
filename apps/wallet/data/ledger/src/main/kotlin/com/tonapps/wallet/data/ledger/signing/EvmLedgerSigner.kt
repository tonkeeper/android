package com.tonapps.wallet.data.ledger.signing

import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.core
import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerConfirmKind
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.apdu.Bip32Path
import com.tonapps.ledgerkit.app.eth.Eip712ClearSigning
import com.tonapps.ledgerkit.app.eth.Eip712TypedData
import com.tonapps.ledgerkit.app.eth.EthApp
import com.tonapps.ledgerkit.app.eth.EthSignature
import com.tonapps.ledgerkit.controller.LedgerScope
import com.tonapps.ledgerkit.transport.LedgerModel
import com.tonapps.wallet.data.ledger.analytics.LedgerNonFatals
import com.trustwallet.core.ethereum.SigningInput
import com.trustwallet.core.ethereum.SigningOutput
import java.math.BigInteger

internal class EvmLedgerSigner {

    suspend fun signTransaction(
        scope: LedgerScope,
        path: Bip32Path,
        chain: Chain,
        input: ByteArray,
        tokenDescriptor: ByteArray?,
        step: Int,
        totalSteps: Int,
    ): ByteArray {
        val coin = chain.coin.core
        val unsigned = WalletCoreCompiler.preImage(coin, input)
        val chainId = BigInteger(1, SigningInput.ADAPTER.decode(input).chain_id.toByteArray()).toLong()
        scope.ensureApp(LedgerAppId.Ethereum)
        val app = EthApp(scope.session)
        tokenDescriptor?.let { provideTokenInformation(app, it) }
        scope.report(LedgerProgress.Confirm(LedgerAppId.Ethereum, LedgerConfirmKind.Transaction, step, totalSteps))
        val signature = app.signTransaction(path, unsigned, chainId)
        val compiled = WalletCoreCompiler.compile(coin, input, signature.toRsvWithRecoveryId())
        val output = SigningOutput.ADAPTER.decode(compiled)
        WalletCoreCompiler.requireSigned(output.error, output.error_message)
        return compiled
    }

    suspend fun signPersonalMessage(scope: LedgerScope, path: Bip32Path, message: String): String {
        val bytes = personalMessageBytes(message)
        scope.ensureApp(LedgerAppId.Ethereum)
        scope.report(LedgerProgress.Confirm(LedgerAppId.Ethereum, LedgerConfirmKind.Message))
        return EthApp(scope.session).signPersonalMessage(path, bytes).toMessageSignature()
    }

    suspend fun signTypedData(scope: LedgerScope, path: Bip32Path, json: String, clearSigning: Eip712ClearSigning?): String {
        val typedData = Eip712TypedData.parse(json)
        scope.ensureApp(LedgerAppId.Ethereum)
        scope.report(LedgerProgress.Confirm(LedgerAppId.Ethereum, LedgerConfirmKind.TypedData))
        val supportsFullImplementation = scope.session.model != LedgerModel.NanoS
        return EthApp(scope.session).signTypedData(path, typedData, supportsFullImplementation, clearSigning).toMessageSignature()
    }

    suspend fun readAddress(
        scope: LedgerScope,
        path: Bip32Path,
        display: Boolean,
        expectedAddress: String?,
        chainId: Long? = null,
    ): String {
        scope.ensureApp(LedgerAppId.Ethereum)
        if (display && expectedAddress != null) {
            scope.report(LedgerProgress.VerifyAddress(LedgerAppId.Ethereum, expectedAddress))
        }
        return EthApp(scope.session).getAddress(path, display, chainId).address
    }

    private suspend fun provideTokenInformation(app: EthApp, descriptor: ByteArray) {
        try {
            app.provideTokenInformation(descriptor)
        } catch (e: LedgerFailureException) {
            if (!e.failure.isDescriptorRejection()) {
                throw e
            }
            LedgerNonFatals.reportFallback("The Ethereum app rejected an ERC-20 descriptor", e)
        }
    }

    private fun LedgerFailure.isDescriptorRejection(): Boolean = this is LedgerFailure.InvalidPayload ||
        this is LedgerFailure.DeviceError ||
        this is LedgerFailure.WrongAppOpen

    private fun EthSignature.toMessageSignature(): String = "0x" + toRsv().toHexString()
}

internal fun personalMessageBytes(message: String): ByteArray {
    val hex = message.removePrefix("0x")
    val isHex = hex.length % 2 == 0 && hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    return if (isHex) { hex.hexToBytes() } else { message.toByteArray(Charsets.UTF_8) }
}
