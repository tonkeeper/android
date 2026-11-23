package com.tonapps.ledgerkit.app.btc

import java.io.ByteArrayOutputStream

class BtcWalletPolicy(
    val name: String,
    val descriptorTemplate: String,
    val keysInfo: List<String>,
) {

    val id: ByteArray
        get() = sha256(serialize())

    val templateBytes: ByteArray
        get() = descriptorTemplate.toByteArray(Charsets.US_ASCII)

    val keysInfoBytes: List<ByteArray>
        get() = keysInfo.map { it.toByteArray(Charsets.US_ASCII) }

    fun serialize(): ByteArray {
        val nameBytes = name.toByteArray(Charsets.US_ASCII)
        require(nameBytes.size <= MAX_NAME_LENGTH) { "Wallet policy name is too long" }
        val templateBytes = descriptorTemplate.toByteArray(Charsets.US_ASCII)
        val out = ByteArrayOutputStream()
        out.write(VERSION_V2)
        out.write(nameBytes.size)
        out.appendBytes(nameBytes)
        out.appendBytes(CompactSize.encode(templateBytes.size))
        out.appendBytes(sha256(templateBytes))
        out.appendBytes(CompactSize.encode(keysInfo.size))
        out.appendBytes(BtcMerkleTree.ofElements(keysInfo.map { it.toByteArray(Charsets.US_ASCII) }).root)
        return out.toByteArray()
    }

    companion object {

        private const val VERSION_V2 = 0x02
        private const val MAX_NAME_LENGTH = 64

        fun nativeSegwit(masterFingerprint: ByteArray, account: Int, accountXpub: String, coinType: Int = 0): BtcWalletPolicy {
            require(masterFingerprint.size == 4) { "Master fingerprint must be 4 bytes" }
            val fingerprintHex = masterFingerprint.joinToString("") { "%02x".format(it) }
            val keyInfo = "[$fingerprintHex/84'/$coinType'/$account']$accountXpub"
            return BtcWalletPolicy(name = "", descriptorTemplate = "wpkh(@0/**)", keysInfo = listOf(keyInfo))
        }
    }
}
