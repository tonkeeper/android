package com.tonapps.ledgerkit.app.eth

import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerAppSetting
import com.tonapps.ledgerkit.LedgerDiagnostics
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.LedgerFallbackException
import com.tonapps.ledgerkit.apdu.Apdu
import com.tonapps.ledgerkit.apdu.ApduChannel
import com.tonapps.ledgerkit.apdu.Bip32Path
import com.tonapps.ledgerkit.apdu.LedgerOperation
import com.tonapps.ledgerkit.apdu.StatusWord
import com.tonapps.ledgerkit.apdu.requireOk
import com.tonapps.ledgerkit.session.LedgerVersion
import com.tonapps.log.L
import org.web3j.crypto.Hash

class EthApp(private val channel: ApduChannel) {

    suspend fun getAddress(path: Bip32Path, display: Boolean, chainId: Long? = null): EthAccount {
        val payload = if (display && chainId != null) {
            path.encode() + ByteArray(Long.SIZE_BYTES) { index -> (chainId ushr (8 * (Long.SIZE_BYTES - 1 - index))).toByte() }
        } else {
            path.encode()
        }
        val response = channel.exchange(
            apdu = Apdu.build(CLA, INS_GET_ADDRESS, if (display) { P1_DISPLAY } else { P1_SILENT }, P2_NO_CHAIN_CODE, payload),
            timeout = if (display) { null } else { ApduChannel.NON_INTERACTIVE_TIMEOUT },
        ).requireOk(LedgerAppId.Ethereum, LedgerOperation.Address)
        return parseAddress(response)
    }

    suspend fun getConfiguration(): EthAppConfiguration {
        val data = channel.exchange(Apdu.build(CLA, INS_GET_CONFIGURATION, 0, 0), ApduChannel.NON_INTERACTIVE_TIMEOUT)
            .requireOk(LedgerAppId.Ethereum, LedgerOperation.Configuration)
        if (data.size < 4) {
            throw LedgerFailureException(LedgerFailure.Unexpected("Unexpected Ethereum app configuration"))
        }
        val flags = data[0].toInt() and 0xff
        return EthAppConfiguration(
            blindSigningEnabled = flags and FLAG_BLIND_SIGNING != 0,
            version = "${data[1].toInt() and 0xff}.${data[2].toInt() and 0xff}.${data[3].toInt() and 0xff}",
        )
    }

    suspend fun provideTokenInformation(descriptor: ByteArray) {
        channel.exchange(
            apdu = Apdu.build(CLA, INS_PROVIDE_TOKEN_INFORMATION, 0, 0, descriptor),
            timeout = ApduChannel.NON_INTERACTIVE_TIMEOUT,
        ).requireOk(LedgerAppId.Ethereum, LedgerOperation.Other)
    }

    suspend fun signTransaction(path: Bip32Path, unsignedTransaction: ByteArray, chainId: Long): EthSignature {
        val pathBytes = path.encode()
        val payload = pathBytes + unsignedTransaction
        val isLegacy = EthTransactionFormat.isLegacy(unsignedTransaction)
        val chunkSize = if (isLegacy) {
            EthTransactionFormat.legacySafeChunkSize(payload.size, pathBytes.size, chainId)
        } else {
            Apdu.MAX_DATA_LENGTH
        }
        val data = try {
            sendChunked(INS_SIGN_TRANSACTION, payload, chunkSize, LedgerOperation.SignTransaction, interactiveChunks = true)
        } catch (e: LedgerFailureException) {
            throw refineInvalidData(e)
        }
        val signature = parseSignature(data)
        val parity = if (isLegacy) { EthTransactionFormat.legacyParity(signature.v, chainId) } else { signature.v and 1 }
        return signature.copy(v = parity)
    }

    suspend fun signPersonalMessage(path: Bip32Path, message: ByteArray): EthSignature {
        val length = byteArrayOf(
            (message.size ushr 24).toByte(),
            (message.size ushr 16).toByte(),
            (message.size ushr 8).toByte(),
            message.size.toByte(),
        )
        val data = sendChunked(INS_SIGN_PERSONAL_MESSAGE, path.encode() + length + message, Apdu.MAX_DATA_LENGTH, LedgerOperation.SignMessage, interactiveChunks = true)
        return parseSignature(data)
    }

    suspend fun signTypedDataHashes(path: Bip32Path, domainSeparator: ByteArray, messageHash: ByteArray): EthSignature {
        require(domainSeparator.size == HASH_LENGTH && messageHash.size == HASH_LENGTH) { "EIP-712 hashes must be 32 bytes" }
        val data = channel.exchange(
            Apdu.build(CLA, INS_SIGN_EIP712, 0, P2_EIP712_HASHES, path.encode() + domainSeparator + messageHash),
            timeout = null,
        ).requireOk(LedgerAppId.Ethereum, LedgerOperation.SignTypedData)
        return parseSignature(data)
    }

    suspend fun signTypedData(
        path: Bip32Path,
        typedData: Eip712TypedData,
        supportsFullImplementation: Boolean,
        clearSigning: Eip712ClearSigning? = null,
    ): EthSignature {
        if (typedData.primaryType == EIP712_DOMAIN_TYPE) {
            throw LedgerFailureException(LedgerFailure.Unsupported("EIP-712 data whose primary type is EIP712Domain"))
        }
        val hashes = try {
            Eip712Hasher.domainSeparator(typedData) to Eip712Hasher.messageHash(typedData)
        } catch (e: IllegalArgumentException) {
            throw LedgerFailureException(LedgerFailure.InvalidPayload(0, e.message ?: "Invalid EIP-712 data"), cause = e)
        }
        val configuration = getConfiguration()
        val canUseFullImplementation = supportsFullImplementation &&
            LedgerVersion.compare(configuration.version, FULL_EIP712_MIN_VERSION) >= 0
        val fullCommands = if (canUseFullImplementation) { encodeFullImplementation(typedData, null) } else { null }
        val canFilter = fullCommands != null && clearSigning != null &&
            LedgerVersion.compare(configuration.version, EIP712_FILTERING_MIN_VERSION) >= 0
        val filteredCommands = if (canFilter) { encodeFullImplementation(typedData, clearSigning) } else { null }
        if (filteredCommands != null) {
            try {
                return signTypedDataFull(path, filteredCommands)
            } catch (e: LedgerFailureException) {
                if (!e.failure.allowsHashFallback()) {
                    throw e
                }
                L.w(e)
                LedgerDiagnostics.report(LedgerFallbackException("The Ethereum app rejected filtered EIP-712 data", e))
            }
        }
        if (fullCommands != null) {
            try {
                return signTypedDataFull(path, fullCommands)
            } catch (e: LedgerFailureException) {
                val invalidData = (e.failure as? LedgerFailure.InvalidPayload)?.statusWord == StatusWord.INVALID_DATA
                if (invalidData && !configuration.blindSigningEnabled) {
                    throw LedgerFailureException(LedgerFailure.SettingRequired(LedgerAppId.Ethereum, LedgerAppSetting.RawMessages))
                }
                if (!e.failure.allowsHashFallback()) {
                    throw e
                }
                LedgerDiagnostics.report(LedgerFallbackException("The Ethereum app rejected EIP-712 data, signing its hashes", e))
            }
        }
        if (!configuration.blindSigningEnabled) {
            throw LedgerFailureException(LedgerFailure.SettingRequired(LedgerAppId.Ethereum, LedgerAppSetting.BlindSigning))
        }
        return signTypedDataHashes(path, hashes.first, hashes.second)
    }

    private fun encodeFullImplementation(typedData: Eip712TypedData, clearSigning: Eip712ClearSigning?): Eip712Commands? = try {
        val encoder = Eip712LedgerEncoder(typedData, clearSigning)
        Eip712Commands(encoder.structDefinitionCommands(), encoder.structImplementationCommands())
    } catch (e: IllegalArgumentException) {
        val what = if (clearSigning == null) { "EIP-712 data" } else { "Filtered EIP-712 data" }
        LedgerDiagnostics.report(LedgerFallbackException("$what could not be encoded for the Ethereum app", e))
        null
    }

    private suspend fun signTypedDataFull(path: Bip32Path, commands: Eip712Commands): EthSignature {
        for (apdu in commands.definitions) {
            channel.exchange(apdu, ApduChannel.NON_INTERACTIVE_TIMEOUT).requireOk(LedgerAppId.Ethereum, LedgerOperation.SignTypedData)
        }
        for (apdu in commands.implementation) {
            channel.exchange(apdu, timeout = null).requireOk(LedgerAppId.Ethereum, LedgerOperation.SignTypedData)
        }
        val data = channel.exchange(
            Apdu.build(CLA, INS_SIGN_EIP712, 0, P2_EIP712_FULL, path.encode()),
            timeout = null,
        ).requireOk(LedgerAppId.Ethereum, LedgerOperation.SignTypedData)
        return parseSignature(data)
    }

    private suspend fun refineInvalidData(error: LedgerFailureException): LedgerFailureException {
        val failure = error.failure
        if (failure !is LedgerFailure.InvalidPayload || failure.statusWord != StatusWord.INVALID_DATA) {
            return error
        }
        val blindSigningEnabled = try {
            getConfiguration().blindSigningEnabled
        } catch (_: LedgerFailureException) {
            return error
        }
        return if (blindSigningEnabled) {
            error
        } else {
            LedgerFailureException(LedgerFailure.SettingRequired(LedgerAppId.Ethereum, LedgerAppSetting.BlindSigning))
        }
    }

    private fun LedgerFailure.allowsHashFallback(): Boolean = this is LedgerFailure.InvalidPayload || this is LedgerFailure.DeviceError

    private suspend fun sendChunked(
        ins: Int,
        payload: ByteArray,
        chunkSize: Int,
        operation: LedgerOperation,
        interactiveChunks: Boolean = false,
    ): ByteArray {
        var offset = 0
        var p1 = P1_FIRST_CHUNK
        var last = ByteArray(0)
        while (offset < payload.size) {
            val end = minOf(offset + chunkSize, payload.size)
            val isLast = end == payload.size
            last = channel.exchange(
                apdu = Apdu.build(CLA, ins, p1, 0, payload.copyOfRange(offset, end)),
                timeout = if (isLast || interactiveChunks) { null } else { ApduChannel.NON_INTERACTIVE_TIMEOUT },
            ).requireOk(LedgerAppId.Ethereum, operation)
            p1 = P1_NEXT_CHUNK
            offset = end
        }
        return last
    }

    internal companion object {
        private const val EIP712_DOMAIN_TYPE = "EIP712Domain"
        const val CLA = 0xe0
        const val INS_GET_ADDRESS = 0x02
        const val INS_SIGN_TRANSACTION = 0x04
        const val INS_GET_CONFIGURATION = 0x06
        const val INS_SIGN_PERSONAL_MESSAGE = 0x08
        const val INS_PROVIDE_TOKEN_INFORMATION = 0x0a
        const val INS_SIGN_EIP712 = 0x0c
        const val P1_SILENT = 0x00
        const val P1_DISPLAY = 0x01
        const val P1_FIRST_CHUNK = 0x00
        const val P1_NEXT_CHUNK = 0x80
        const val P2_NO_CHAIN_CODE = 0x00
        const val P2_EIP712_HASHES = 0x00
        const val P2_EIP712_FULL = 0x01
        const val FLAG_BLIND_SIGNING = 0x01
        const val HASH_LENGTH = 32
        const val PUBLIC_KEY_LENGTH = 65
        const val SIGNATURE_LENGTH = 65
        const val FULL_EIP712_MIN_VERSION = "1.11.1"
        const val EIP712_FILTERING_MIN_VERSION = "1.12.0"

        fun parseAddress(data: ByteArray): EthAccount {
            if (data.isEmpty()) {
                throw LedgerFailureException(LedgerFailure.Unexpected("Empty Ethereum address response"))
            }
            var offset = 0
            val publicKeyLength = data[offset++].toInt() and 0xff
            if (publicKeyLength != PUBLIC_KEY_LENGTH || offset + publicKeyLength >= data.size) {
                throw LedgerFailureException(LedgerFailure.Unexpected("Unexpected Ethereum public key length"))
            }
            val publicKey = data.copyOfRange(offset, offset + publicKeyLength)
            offset += publicKeyLength
            val addressLength = data[offset++].toInt() and 0xff
            if (offset + addressLength > data.size) {
                throw LedgerFailureException(LedgerFailure.Unexpected("Truncated Ethereum address"))
            }
            val reportedAddress = String(data, offset, addressLength, Charsets.US_ASCII).removePrefix("0x").lowercase()
            val derivedAddress = addressFromPublicKey(publicKey)
            if (derivedAddress != reportedAddress) {
                throw LedgerFailureException(LedgerFailure.InvalidPayload(0x9000, "Address does not match the public key"))
            }
            return EthAccount(address = "0x$derivedAddress", publicKey = publicKey)
        }

        fun addressFromPublicKey(uncompressedPublicKey: ByteArray): String {
            require(uncompressedPublicKey.size == PUBLIC_KEY_LENGTH && uncompressedPublicKey[0] == 0x04.toByte()) {
                "Expected an uncompressed secp256k1 public key"
            }
            val hash = Hash.sha3(uncompressedPublicKey.copyOfRange(1, PUBLIC_KEY_LENGTH))
            return hash.copyOfRange(12, 32).joinToString("") { "%02x".format(it) }
        }

        fun parseSignature(data: ByteArray): EthSignature {
            if (data.size < SIGNATURE_LENGTH) {
                throw LedgerFailureException(LedgerFailure.Unexpected("Truncated Ethereum signature"))
            }
            return EthSignature(
                v = data[0].toInt() and 0xff,
                r = data.copyOfRange(1, 33),
                s = data.copyOfRange(33, 65),
            )
        }
    }
}

data class EthAccount(val address: String, val publicKey: ByteArray) {

    override fun equals(other: Any?): Boolean =
        other is EthAccount && other.address == address && other.publicKey.contentEquals(publicKey)

    override fun hashCode(): Int = address.hashCode()
}

data class EthAppConfiguration(val blindSigningEnabled: Boolean, val version: String)

data class EthSignature(val v: Int, val r: ByteArray, val s: ByteArray) {

    fun toRsvWithRecoveryId(): ByteArray = r + s + byteArrayOf((v and 1).toByte())

    fun toRsv(): ByteArray = r + s + byteArrayOf(v.toByte())

    override fun equals(other: Any?): Boolean =
        other is EthSignature && other.v == v && other.r.contentEquals(r) && other.s.contentEquals(s)

    override fun hashCode(): Int = 31 * v + r.contentHashCode()
}

private class Eip712Commands(
    val definitions: List<ByteArray>,
    val implementation: List<ByteArray>,
)
