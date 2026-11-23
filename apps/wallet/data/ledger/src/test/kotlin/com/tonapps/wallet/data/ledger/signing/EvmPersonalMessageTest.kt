package com.tonapps.wallet.data.ledger.signing

import com.trustwallet.core.Curve
import com.trustwallet.core.EthereumMessageSigner
import com.trustwallet.core.Hash
import com.trustwallet.core.PrivateKey
import com.trustwallet.core.WalletCoreLibLoader
import org.junit.Assert.assertEquals
import org.junit.Test

class EvmPersonalMessageTest {

    init {
        WalletCoreLibLoader.loadLibrary()
    }

    private val key = PrivateKey(ByteArray(32) { (it + 1).toByte() })

    @Test
    fun `ledger signs the same bytes of a personal message as a mnemonic wallet`() {
        val messages = listOf(
            "hello",
            "deadbeef",
            "0xdeadbeef",
            "DEADBEEF",
            "0XDEADBEEF",
            "12345678",
            "abc",
            "0xabc",
            "0x",
            "0x0",
            "",
            "ab cd",
            "١٢",
            "Sign in to app.example.com\nNonce: 12345678",
        )

        messages.forEach { message ->
            assertEquals(message, softwareSignature(message), ledgerSignature(personalMessageBytes(message)))
        }
    }

    private fun softwareSignature(message: String): String =
        EthereumMessageSigner.signMessage(key, message).removePrefix("0x").substring(0, SIGNATURE_RS_HEX)

    private fun ledgerSignature(bytes: ByteArray): String {
        val prefixed = "\u0019Ethereum Signed Message:\n${bytes.size}".toByteArray() + bytes
        val signature = requireNotNull(key.sign(Hash.keccak256(prefixed), Curve.SECP256k1))
        return signature.copyOfRange(0, SIGNATURE_RS_BYTES).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val SIGNATURE_RS_BYTES = 64
        const val SIGNATURE_RS_HEX = 128
    }
}
