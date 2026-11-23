package com.tonapps.ledgerkit.app.btc

import com.tonapps.ledgerkit.util.Base58
import org.web3j.crypto.Hash
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

object CompactSize {

    fun encode(value: Long): ByteArray = when {
        value < 0 -> throw IllegalArgumentException("Negative CompactSize")
        value <= 0xfc -> byteArrayOf(value.toByte())
        value <= 0xffff -> byteArrayOf(0xfd.toByte()) + littleEndian(value, 2)
        value <= 0xffffffffL -> byteArrayOf(0xfe.toByte()) + littleEndian(value, 4)
        else -> byteArrayOf(0xff.toByte()) + littleEndian(value, 8)
    }

    fun encode(value: Int): ByteArray = encode(value.toLong())

    fun decode(data: ByteArray, offset: Int): Pair<Long, Int> {
        require(offset < data.size) { "Truncated CompactSize" }
        return when (val first = data[offset].toInt() and 0xff) {
            0xfd -> readLittleEndian(data, offset + 1, 2) to 3
            0xfe -> readLittleEndian(data, offset + 1, 4) to 5
            0xff -> readLittleEndian(data, offset + 1, 8) to 9
            else -> first.toLong() to 1
        }
    }
}

internal fun littleEndian(value: Long, size: Int): ByteArray = ByteArray(size) { index -> (value ushr (8 * index)).toByte() }

internal fun readLittleEndian(data: ByteArray, offset: Int, size: Int): Long {
    require(offset + size <= data.size) { "Truncated integer" }
    var result = 0L
    for (index in size - 1 downTo 0) {
        result = (result shl 8) or (data[offset + index].toLong() and 0xff)
    }
    return result
}

internal fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

internal fun doubleSha256(data: ByteArray): ByteArray = sha256(sha256(data))

internal fun hash160(data: ByteArray): ByteArray = Hash.sha256hash160(data)

internal fun ByteArrayOutputStream.appendBytes(bytes: ByteArray) = write(bytes, 0, bytes.size)

object Bech32 {

    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val GENERATOR = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

    fun segwitV0Address(hrp: String, program: ByteArray): String {
        require(program.size == 20 || program.size == 32) { "Invalid witness v0 program" }
        val data = intArrayOf(0) + convertBits(program, 8, 5, pad = true)
        val checksum = checksum(hrp, data)
        return hrp + "1" + (data + checksum).joinToString("") { CHARSET[it].toString() }
    }

    fun decodeSegwitV0(hrp: String, address: String): ByteArray {
        val lower = address.lowercase()
        require(lower.startsWith(hrp + "1")) { "Unexpected address prefix" }
        val values = lower.substring(hrp.length + 1).map { char ->
            CHARSET.indexOf(char).also { require(it >= 0) { "Invalid bech32 character" } }
        }.toIntArray()
        require(values.size > 6) { "Bech32 value is too short" }
        require(polymod(expandHrp(hrp) + values) == 1) { "Invalid bech32 checksum" }
        val data = values.copyOfRange(0, values.size - 6)
        require(data.isNotEmpty() && data[0] == 0) { "Only witness v0 addresses are supported" }
        return convertBitsBack(data.copyOfRange(1, data.size))
    }

    private fun checksum(hrp: String, data: IntArray): IntArray {
        val values = expandHrp(hrp) + data + IntArray(6)
        val polymod = polymod(values) xor 1
        return IntArray(6) { (polymod ushr (5 * (5 - it))) and 31 }
    }

    private fun polymod(values: IntArray): Int {
        var checksum = 1
        for (value in values) {
            val top = checksum ushr 25
            checksum = ((checksum and 0x1ffffff) shl 5) xor value
            for (index in 0 until 5) {
                if ((top ushr index) and 1 == 1) {
                    checksum = checksum xor GENERATOR[index]
                }
            }
        }
        return checksum
    }

    private fun expandHrp(hrp: String): IntArray =
        hrp.map { it.code ushr 5 }.toIntArray() + intArrayOf(0) + hrp.map { it.code and 31 }.toIntArray()

    private fun convertBits(data: ByteArray, from: Int, to: Int, pad: Boolean): IntArray {
        var accumulator = 0
        var bits = 0
        val result = mutableListOf<Int>()
        val maxValue = (1 shl to) - 1
        for (byte in data) {
            accumulator = (accumulator shl from) or (byte.toInt() and 0xff)
            bits += from
            while (bits >= to) {
                bits -= to
                result += (accumulator ushr bits) and maxValue
            }
        }
        if (pad && bits > 0) {
            result += (accumulator shl (to - bits)) and maxValue
        }
        return result.toIntArray()
    }

    private fun convertBitsBack(data: IntArray): ByteArray {
        var accumulator = 0
        var bits = 0
        val result = ByteArrayOutputStream()
        for (value in data) {
            accumulator = (accumulator shl 5) or value
            bits += 5
            if (bits >= 8) {
                bits -= 8
                result.write((accumulator ushr bits) and 0xff)
            }
        }
        require(bits < 5 && (accumulator and ((1 shl bits) - 1)) == 0) { "Invalid bech32 padding" }
        return result.toByteArray()
    }
}

data class BtcExtendedPublicKey(
    val version: Int,
    val depth: Int,
    val parentFingerprint: ByteArray,
    val childNumber: Long,
    val chainCode: ByteArray,
    val publicKey: ByteArray,
) {

    fun encode(version: Int = this.version): String {
        val out = ByteArrayOutputStream()
        out.appendBytes(byteArrayOf((version ushr 24).toByte(), (version ushr 16).toByte(), (version ushr 8).toByte(), version.toByte()))
        out.write(depth)
        out.appendBytes(parentFingerprint)
        out.appendBytes(byteArrayOf((childNumber ushr 24).toByte(), (childNumber ushr 16).toByte(), (childNumber ushr 8).toByte(), childNumber.toByte()))
        out.appendBytes(chainCode)
        out.appendBytes(publicKey)
        return Base58.encodeWithChecksum(out.toByteArray())
    }

    fun toZpub(): String = encode(ZPUB_VERSION)

    override fun equals(other: Any?): Boolean = other is BtcExtendedPublicKey && encode() == other.encode()

    override fun hashCode(): Int = publicKey.contentHashCode()

    companion object {

        const val XPUB_VERSION = 0x0488b21e
        const val ZPUB_VERSION = 0x04b24746
        private const val SERIALIZED_LENGTH = 78

        fun parse(encoded: String): BtcExtendedPublicKey {
            val data = Base58.decodeWithChecksum(encoded)
            require(data.size == SERIALIZED_LENGTH) { "Invalid extended public key length" }
            val version = ((data[0].toInt() and 0xff) shl 24) or ((data[1].toInt() and 0xff) shl 16) or
                ((data[2].toInt() and 0xff) shl 8) or (data[3].toInt() and 0xff)
            val childNumber = ((data[9].toLong() and 0xff) shl 24) or ((data[10].toLong() and 0xff) shl 16) or
                ((data[11].toLong() and 0xff) shl 8) or (data[12].toLong() and 0xff)
            val publicKey = data.copyOfRange(45, 78)
            require(publicKey[0] == 0x02.toByte() || publicKey[0] == 0x03.toByte()) { "Extended key is not public" }
            return BtcExtendedPublicKey(
                version = version,
                depth = data[4].toInt() and 0xff,
                parentFingerprint = data.copyOfRange(5, 9),
                childNumber = childNumber,
                chainCode = data.copyOfRange(13, 45),
                publicKey = publicKey,
            )
        }
    }
}

object BtcScripts {

    const val MAINNET_HRP = "bc"

    fun p2wpkhScript(publicKey: ByteArray): ByteArray {
        require(publicKey.size == 33) { "Expected a compressed public key" }
        return byteArrayOf(0x00, 0x14) + hash160(publicKey)
    }

    fun p2wpkhAddress(publicKey: ByteArray, hrp: String = MAINNET_HRP): String =
        Bech32.segwitV0Address(hrp, hash160(publicKey))

    fun scriptForSegwitAddress(address: String, hrp: String = MAINNET_HRP): ByteArray {
        val program = Bech32.decodeSegwitV0(hrp, address)
        return byteArrayOf(0x00, program.size.toByte()) + program
    }
}
