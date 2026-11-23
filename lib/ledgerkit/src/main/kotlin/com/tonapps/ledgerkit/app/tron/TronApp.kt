package com.tonapps.ledgerkit.app.tron

import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.apdu.Apdu
import com.tonapps.ledgerkit.apdu.ApduChannel
import com.tonapps.ledgerkit.apdu.Bip32Path
import com.tonapps.ledgerkit.apdu.LedgerOperation
import com.tonapps.ledgerkit.apdu.requireOk
import com.tonapps.ledgerkit.session.LedgerVersion
import com.tonapps.ledgerkit.util.Base58
import org.web3j.crypto.Hash

class TronApp(private val channel: ApduChannel) {

    suspend fun getAddress(path: Bip32Path, display: Boolean): TronAccount {
        val data = channel.exchange(
            apdu = Apdu.build(CLA, INS_GET_ADDRESS, if (display) { P1_DISPLAY } else { P1_SILENT }, 0, path.encode()),
            timeout = if (display) { null } else { ApduChannel.NON_INTERACTIVE_TIMEOUT },
        ).requireOk(LedgerAppId.Tron, LedgerOperation.Address)
        return parseAddress(data)
    }

    suspend fun getConfiguration(): TronAppConfiguration {
        val data = channel.exchange(Apdu.build(CLA, INS_GET_CONFIGURATION, 0, 0), ApduChannel.NON_INTERACTIVE_TIMEOUT)
            .requireOk(LedgerAppId.Tron, LedgerOperation.Configuration)
        if (data.size < 4) {
            throw LedgerFailureException(LedgerFailure.Unexpected("Unexpected Tron app configuration"))
        }
        val flags = data[0].toInt() and 0xff
        val version = "${data[1].toInt() and 0xff}.${data[2].toInt() and 0xff}.${data[3].toInt() and 0xff}"
        val signByHashBit = if (LedgerVersion.compare(version, SIGN_BY_HASH_BIT_MOVED_IN) >= 0) { 0x04 } else { 0x08 }
        return TronAppConfiguration(
            version = version,
            dataAllowed = flags and 0x01 != 0,
            customContractsAllowed = flags and 0x02 != 0,
            signByHashAllowed = flags and signByHashBit != 0,
        )
    }

    suspend fun signTransaction(path: Bip32Path, rawData: ByteArray): ByteArray {
        val chunks = TronTransactionChunker.chunk(path.encode(), rawData)
        var result = ByteArray(0)
        chunks.forEachIndexed { index, chunk ->
            val p1 = when {
                chunks.size == 1 -> P1_SIGN_SINGLE
                index == 0 -> P1_SIGN_FIRST
                index == chunks.lastIndex -> P1_SIGN_LAST
                else -> P1_SIGN_MORE
            }
            val isLast = index == chunks.lastIndex
            result = channel.exchange(
                apdu = Apdu.build(CLA, INS_SIGN, p1, 0, chunk),
                timeout = if (isLast) { null } else { ApduChannel.NON_INTERACTIVE_TIMEOUT },
            ).requireOk(LedgerAppId.Tron, LedgerOperation.SignTransaction)
        }
        return parseSignature(result)
    }

    suspend fun signTransactionHash(path: Bip32Path, transactionHash: ByteArray): ByteArray {
        require(transactionHash.size == HASH_LENGTH) { "Tron transaction hash must be 32 bytes" }
        val data = channel.exchange(
            apdu = Apdu.build(CLA, INS_SIGN_HASH, 0, 0, path.encode() + transactionHash),
            timeout = null,
        ).requireOk(LedgerAppId.Tron, LedgerOperation.SignTransaction)
        return parseSignature(data)
    }

    suspend fun signPersonalMessage(path: Bip32Path, message: ByteArray): ByteArray {
        val pathBytes = path.encode()
        val packed = byteArrayOf(
            (message.size ushr 24).toByte(),
            (message.size ushr 16).toByte(),
            (message.size ushr 8).toByte(),
            message.size.toByte(),
        ) + message
        val firstLength = minOf(packed.size, CHUNK_LIMIT - pathBytes.size)
        val chunks = mutableListOf(pathBytes + packed.copyOfRange(0, firstLength))
        var offset = firstLength
        while (offset < packed.size) {
            val end = minOf(offset + CHUNK_LIMIT, packed.size)
            chunks += packed.copyOfRange(offset, end)
            offset = end
        }
        var result = ByteArray(0)
        chunks.forEachIndexed { index, chunk ->
            val isLast = index == chunks.lastIndex
            result = channel.exchange(
                apdu = Apdu.build(CLA, INS_SIGN_PERSONAL_MESSAGE, if (index == 0) { P1_MESSAGE_FIRST } else { P1_MESSAGE_MORE }, 0, chunk),
                timeout = if (isLast) { null } else { ApduChannel.NON_INTERACTIVE_TIMEOUT },
            ).requireOk(LedgerAppId.Tron, LedgerOperation.SignMessage)
        }
        return parseSignature(result)
    }

    internal companion object {
        const val CLA = 0xe0
        const val INS_GET_ADDRESS = 0x02
        const val INS_SIGN = 0x04
        const val INS_SIGN_HASH = 0x05
        const val INS_GET_CONFIGURATION = 0x06
        const val INS_SIGN_PERSONAL_MESSAGE = 0x08
        const val P1_SILENT = 0x00
        const val P1_DISPLAY = 0x01
        const val P1_SIGN_SINGLE = 0x10
        const val P1_SIGN_FIRST = 0x00
        const val P1_SIGN_MORE = 0x80
        const val P1_SIGN_LAST = 0x90
        const val P1_MESSAGE_FIRST = 0x00
        const val P1_MESSAGE_MORE = 0x80
        const val CHUNK_LIMIT = 250
        const val HASH_LENGTH = 32
        const val PUBLIC_KEY_LENGTH = 65
        const val SIGNATURE_LENGTH = 65
        const val ADDRESS_PREFIX: Byte = 0x41
        const val SIGN_BY_HASH_BIT_MOVED_IN = "0.7.5"

        fun parseAddress(data: ByteArray): TronAccount {
            if (data.isEmpty()) {
                throw LedgerFailureException(LedgerFailure.Unexpected("Empty Tron address response"))
            }
            var offset = 0
            val publicKeyLength = data[offset++].toInt() and 0xff
            if (publicKeyLength != PUBLIC_KEY_LENGTH || offset + publicKeyLength >= data.size) {
                throw LedgerFailureException(LedgerFailure.Unexpected("Unexpected Tron public key length"))
            }
            val publicKey = data.copyOfRange(offset, offset + publicKeyLength)
            offset += publicKeyLength
            val addressLength = data[offset++].toInt() and 0xff
            if (offset + addressLength > data.size) {
                throw LedgerFailureException(LedgerFailure.Unexpected("Truncated Tron address"))
            }
            val reportedAddress = String(data, offset, addressLength, Charsets.US_ASCII)
            val derivedAddress = addressFromPublicKey(publicKey)
            if (derivedAddress != reportedAddress) {
                throw LedgerFailureException(LedgerFailure.InvalidPayload(0x9000, "Address does not match the public key"))
            }
            return TronAccount(address = derivedAddress, publicKey = publicKey)
        }

        fun addressFromPublicKey(uncompressedPublicKey: ByteArray): String {
            require(uncompressedPublicKey.size == PUBLIC_KEY_LENGTH && uncompressedPublicKey[0] == 0x04.toByte()) {
                "Expected an uncompressed secp256k1 public key"
            }
            val hash = Hash.sha3(uncompressedPublicKey.copyOfRange(1, PUBLIC_KEY_LENGTH))
            return Base58.encodeWithChecksum(byteArrayOf(ADDRESS_PREFIX) + hash.copyOfRange(12, 32))
        }

        fun parseSignature(data: ByteArray): ByteArray {
            if (data.size < SIGNATURE_LENGTH) {
                throw LedgerFailureException(LedgerFailure.Unexpected("Truncated Tron signature"))
            }
            return data.copyOfRange(0, SIGNATURE_LENGTH)
        }
    }
}

data class TronAccount(val address: String, val publicKey: ByteArray) {

    override fun equals(other: Any?): Boolean =
        other is TronAccount && other.address == address && other.publicKey.contentEquals(publicKey)

    override fun hashCode(): Int = address.hashCode()
}

data class TronAppConfiguration(
    val version: String,
    val dataAllowed: Boolean,
    val customContractsAllowed: Boolean,
    val signByHashAllowed: Boolean,
)

object TronMessages {

    private val PREFIX = "\u0019TRON Signed Message:\n".toByteArray(Charsets.UTF_8)

    fun personalMessageHash(message: ByteArray): ByteArray =
        Hash.sha3(PREFIX + message.size.toString().toByteArray(Charsets.UTF_8) + message)
}
