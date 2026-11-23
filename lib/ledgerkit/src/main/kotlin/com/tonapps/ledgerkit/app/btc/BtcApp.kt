package com.tonapps.ledgerkit.app.btc

import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.apdu.Apdu
import com.tonapps.ledgerkit.apdu.ApduChannel
import com.tonapps.ledgerkit.apdu.ApduResponse
import com.tonapps.ledgerkit.apdu.Bip32Path
import com.tonapps.ledgerkit.apdu.LedgerOperation
import com.tonapps.ledgerkit.apdu.requireOk
import java.io.ByteArrayOutputStream
import kotlin.time.Duration

class BtcApp(private val channel: ApduChannel) {

    suspend fun getMasterFingerprint(): ByteArray {
        val data = run(Apdu.build(CLA, INS_GET_MASTER_FINGERPRINT, 0, PROTOCOL_VERSION), null, ApduChannel.NON_INTERACTIVE_TIMEOUT)
            .requireOk(LedgerAppId.Bitcoin, LedgerOperation.Address)
        if (data.size != 4) {
            throw LedgerFailureException(LedgerFailure.Unexpected("Unexpected master fingerprint length"))
        }
        return data
    }

    suspend fun getExtendedPublicKey(path: Bip32Path, display: Boolean): BtcExtendedPublicKey {
        val payload = byteArrayOf(if (display) { 1 } else { 0 }) + path.encode()
        val data = run(
            apdu = Apdu.build(CLA, INS_GET_EXTENDED_PUBKEY, 0, PROTOCOL_VERSION, payload),
            interpreter = null,
            timeout = if (display) { null } else { ApduChannel.NON_INTERACTIVE_TIMEOUT },
        ).requireOk(LedgerAppId.Bitcoin, LedgerOperation.Address)
        return try {
            BtcExtendedPublicKey.parse(String(data, Charsets.US_ASCII))
        } catch (e: IllegalArgumentException) {
            throw LedgerFailureException(LedgerFailure.Unexpected(e.message ?: "Invalid extended public key"), cause = e)
        }
    }

    suspend fun getWalletAddress(policy: BtcWalletPolicy, change: Boolean, addressIndex: Long, display: Boolean): String {
        val interpreter = BtcClientCommandInterpreter().apply { registerPolicy(policy) }
        val payload = ByteArrayOutputStream().apply {
            write(if (display) { 1 } else { 0 })
            appendBytes(policy.id)
            appendBytes(ByteArray(HMAC_LENGTH))
            write(if (change) { 1 } else { 0 })
            appendBytes(byteArrayOf((addressIndex ushr 24).toByte(), (addressIndex ushr 16).toByte(), (addressIndex ushr 8).toByte(), addressIndex.toByte()))
        }.toByteArray()
        val data = run(
            apdu = Apdu.build(CLA, INS_GET_WALLET_ADDRESS, 0, PROTOCOL_VERSION, payload),
            interpreter = interpreter,
            timeout = if (display) { null } else { ApduChannel.NON_INTERACTIVE_TIMEOUT },
        ).requireOk(LedgerAppId.Bitcoin, LedgerOperation.Address)
        return String(data, Charsets.US_ASCII)
    }

    suspend fun signPsbt(psbt: BtcPsbtV2, policy: BtcWalletPolicy): List<BtcPartialSignature> {
        val interpreter = BtcClientCommandInterpreter().apply {
            registerPolicy(policy)
            addKnownMapping(psbt.global)
            psbt.inputs.forEach(::addKnownMapping)
            psbt.outputs.forEach(::addKnownMapping)
            addKnownList(psbt.inputs.map { it.commitment() })
            addKnownList(psbt.outputs.map { it.commitment() })
        }
        val payload = ByteArrayOutputStream().apply {
            appendBytes(psbt.global.commitment())
            appendBytes(CompactSize.encode(psbt.inputs.size))
            appendBytes(BtcMerkleTree.ofElements(psbt.inputs.map { it.commitment() }).root)
            appendBytes(CompactSize.encode(psbt.outputs.size))
            appendBytes(BtcMerkleTree.ofElements(psbt.outputs.map { it.commitment() }).root)
            appendBytes(policy.id)
            appendBytes(ByteArray(HMAC_LENGTH))
        }.toByteArray()
        run(Apdu.build(CLA, INS_SIGN_PSBT, 0, PROTOCOL_VERSION, payload), interpreter, timeout = null)
            .requireOk(LedgerAppId.Bitcoin, LedgerOperation.SignTransaction)
        return interpreter.yielded.mapNotNull(::parseYieldedSignature)
    }

    private suspend fun run(apdu: ByteArray, interpreter: BtcClientCommandInterpreter?, timeout: Duration?): ApduResponse {
        var response = channel.exchange(apdu, timeout)
        while (response.statusWord == SW_INTERRUPTED_EXECUTION) {
            val commandInterpreter = interpreter
                ?: throw LedgerFailureException(LedgerFailure.Unexpected("Unexpected client command from the Bitcoin app"))
            val reply = try {
                commandInterpreter.execute(response.data)
            } catch (e: IllegalStateException) {
                throw LedgerFailureException(LedgerFailure.Unexpected(e.message ?: "Client command failed"), cause = e)
            } catch (e: IllegalArgumentException) {
                throw LedgerFailureException(LedgerFailure.Unexpected(e.message ?: "Client command failed"), cause = e)
            }
            response = channel.exchange(Apdu.build(CLA_FRAMEWORK, INS_CONTINUE, 0, PROTOCOL_VERSION, reply), timeout)
        }
        return response
    }

    private fun BtcClientCommandInterpreter.registerPolicy(policy: BtcWalletPolicy) {
        addKnownList(policy.keysInfoBytes)
        addKnownPreimage(policy.serialize())
        addKnownPreimage(policy.templateBytes)
    }

    private fun parseYieldedSignature(value: ByteArray): BtcPartialSignature? {
        val (tagOrIndex, tagLength) = CompactSize.decode(value, 0)
        if (tagOrIndex >= RESERVED_TAG_START) {
            return null
        }
        var offset = tagLength
        val publicKeyLength = value[offset++].toInt() and 0xff
        val publicKey = value.copyOfRange(offset, offset + publicKeyLength)
        offset += publicKeyLength
        return BtcPartialSignature(inputIndex = tagOrIndex.toInt(), publicKey = publicKey, signature = value.copyOfRange(offset, value.size))
    }

    companion object {
        const val MIN_APP_VERSION = "2.1.0"
        internal const val CLA = 0xe1
        internal const val CLA_FRAMEWORK = 0xf8
        internal const val INS_GET_EXTENDED_PUBKEY = 0x00
        internal const val INS_GET_WALLET_ADDRESS = 0x03
        internal const val INS_SIGN_PSBT = 0x04
        internal const val INS_GET_MASTER_FINGERPRINT = 0x05
        internal const val INS_CONTINUE = 0x01
        internal const val PROTOCOL_VERSION = 0x01
        internal const val HMAC_LENGTH = 32
        internal const val SW_INTERRUPTED_EXECUTION = 0xe000
        internal const val RESERVED_TAG_START = 0x80000000L
    }
}

class BtcPartialSignature(val inputIndex: Int, val publicKey: ByteArray, val signature: ByteArray)
