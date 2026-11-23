package com.tonapps.ledgerkit.app.solana

import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.apdu.Apdu
import com.tonapps.ledgerkit.apdu.ApduChannel
import com.tonapps.ledgerkit.apdu.Bip32Path
import com.tonapps.ledgerkit.apdu.LedgerOperation
import com.tonapps.ledgerkit.apdu.requireOk

class SolanaApp(private val channel: ApduChannel) {

    suspend fun getConfiguration(): SolanaAppConfiguration {
        val data = channel.exchange(Apdu.build(CLA, INS_GET_CONFIGURATION, 0, 0), ApduChannel.NON_INTERACTIVE_TIMEOUT)
            .requireOk(LedgerAppId.Solana, LedgerOperation.Configuration)
        if (data.size < CONFIGURATION_LENGTH) {
            throw LedgerFailureException(LedgerFailure.Unexpected("Unexpected Solana app configuration"))
        }
        val version = "${data[2].toInt() and 0xff}.${data[3].toInt() and 0xff}.${data[4].toInt() and 0xff}"
        return SolanaAppConfiguration(version = version, blindSigningEnabled = data[0].toInt() != 0)
    }

    suspend fun getPublicKey(path: Bip32Path, display: Boolean): ByteArray {
        val data = channel.exchange(
            apdu = Apdu.build(CLA, INS_GET_PUBKEY, if (display) { P1_CONFIRM } else { P1_NON_CONFIRM }, P2_NONE, path.encode()),
            timeout = if (display) { null } else { ApduChannel.NON_INTERACTIVE_TIMEOUT },
        ).requireOk(LedgerAppId.Solana, LedgerOperation.Address)
        if (data.size < PUBLIC_KEY_LENGTH) {
            throw LedgerFailureException(LedgerFailure.Unexpected("Truncated Solana public key"))
        }
        return data.copyOfRange(0, PUBLIC_KEY_LENGTH)
    }

    suspend fun getChallenge(): ByteArray {
        val data = channel.exchange(Apdu.build(CLA, INS_GET_CHALLENGE, P1_NON_CONFIRM, P2_NONE), ApduChannel.NON_INTERACTIVE_TIMEOUT)
            .requireOk(LedgerAppId.Solana, LedgerOperation.Other)
        if (data.size < CHALLENGE_LENGTH) {
            throw LedgerFailureException(LedgerFailure.Unexpected("Truncated Solana challenge"))
        }
        return data.copyOfRange(0, CHALLENGE_LENGTH)
    }

    suspend fun provideTrustedName(descriptor: ByteArray) {
        if (descriptor.size > Apdu.MAX_DATA_LENGTH) {
            throw LedgerFailureException(LedgerFailure.InvalidPayload(0, "The trusted name is too long for the device"))
        }
        channel.exchange(Apdu.build(CLA, INS_PROVIDE_TRUSTED_NAME, P1_NON_CONFIRM, P2_NONE, descriptor), ApduChannel.NON_INTERACTIVE_TIMEOUT)
            .requireOk(LedgerAppId.Solana, LedgerOperation.Other)
    }

    suspend fun signMessage(path: Bip32Path, message: ByteArray): ByteArray {
        val chunks = chunk(path, message)
        var result = ByteArray(0)
        chunks.forEachIndexed { index, chunk ->
            val isLast = index == chunks.lastIndex
            result = channel.exchange(
                apdu = Apdu.build(CLA, INS_SIGN_MESSAGE, P1_CONFIRM, p2(index, isLast), chunk),
                timeout = if (isLast) { null } else { ApduChannel.NON_INTERACTIVE_TIMEOUT },
            ).requireOk(LedgerAppId.Solana, LedgerOperation.SignTransaction)
        }
        if (result.size < SIGNATURE_LENGTH) {
            throw LedgerFailureException(LedgerFailure.Unexpected("Truncated Solana signature"))
        }
        return result.copyOfRange(0, SIGNATURE_LENGTH)
    }

    companion object {
        internal const val CLA = 0xe0
        internal const val INS_GET_CONFIGURATION = 0x04
        internal const val INS_GET_PUBKEY = 0x05
        internal const val INS_SIGN_MESSAGE = 0x06
        internal const val INS_GET_CHALLENGE = 0x20
        internal const val INS_PROVIDE_TRUSTED_NAME = 0x21
        internal const val P1_NON_CONFIRM = 0x00
        internal const val P1_CONFIRM = 0x01
        internal const val P2_NONE = 0x00
        internal const val P2_EXTEND = 0x01
        internal const val P2_MORE = 0x02
        internal const val SIGNER_COUNT = 1
        internal const val CONFIGURATION_LENGTH = 5
        internal const val PUBLIC_KEY_LENGTH = 32
        internal const val SIGNATURE_LENGTH = 64
        internal const val CHALLENGE_LENGTH = 4
        const val MIN_APP_VERSION = "1.3.1"
        const val TRUSTED_NAME_MIN_APP_VERSION = "1.9.2"

        internal fun chunk(path: Bip32Path, message: ByteArray): List<ByteArray> {
            require(message.isNotEmpty()) { "Empty Solana message" }
            val payload = byteArrayOf(SIGNER_COUNT.toByte()) + path.encode() + message
            return (payload.indices step Apdu.MAX_DATA_LENGTH).map { start ->
                payload.copyOfRange(start, minOf(start + Apdu.MAX_DATA_LENGTH, payload.size))
            }
        }

        internal fun p2(index: Int, isLast: Boolean): Int {
            var p2 = P2_NONE
            if (index > 0) {
                p2 = p2 or P2_EXTEND
            }
            if (!isLast) {
                p2 = p2 or P2_MORE
            }
            return p2
        }
    }
}

data class SolanaAppConfiguration(
    val version: String,
    val blindSigningEnabled: Boolean,
)
