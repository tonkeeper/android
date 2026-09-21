package com.tonapps.blockchain

import com.tonapps.blockchain.ton.MnemonicType
import io.ktor.util.hex
import org.ton.kotlin.crypto.Pbkdf2
import org.ton.kotlin.crypto.PrivateKeyEd25519
import org.ton.kotlin.crypto.Sha512
import org.ton.kotlin.crypto.mnemonic.Mnemonic
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object MnemonicHelper {

    private val mnemonicDividers = listOf(" ", "\n", ",", ";")
    private val mnemonicWords = Mnemonic.bip39English()

    private val pathRegex = Regex("^m(/[0-9]+')+$")
    private const val ED25519_CURVE = "ed25519 seed"
    private const val HARDENED_OFFSET = 0x80000000


    fun findWords(prefix: String): List<String> {
        if (prefix.isBlank()) {
            return emptyList()
        }
        return mnemonicWords.filter { it.startsWith(prefix, ignoreCase = true) }
    }

    // Local BIP39 word<->index wrapper. This wordlist is canonical BIP39 English and byte-identical
    // to chain-kit's internal BIP39_WORDS, so indices built here are valid for chain-kit
    // Mnemonic.from(IntArray) / toWordsUnsafe(). TODO repoint to chain-kit index API.
    fun wordAt(index: Int): String = mnemonicWords[index]

    fun indexOf(word: String): Int = mnemonicWords.indexOf(word)

    fun indexes(words: List<String>): IntArray = IntArray(words.size) { i ->
        val index = mnemonicWords.indexOf(words[i])
        require(index >= 0) { "Unknown mnemonic word" }
        index
    }

    fun findWord(prefix: String): String? {
        return findWords(prefix).firstOrNull()
    }

    fun isValid(word: String?): Boolean {
        if (word.isNullOrBlank()) {
            return false
        }

        return mnemonicWords.contains(word)
    }

    fun isValid(list: List<String>): Boolean {
        return list.all { mnemonicWords.contains(it) }
    }

    fun parseMnemonic(value: String): List<String> {
        if (3 >= value.length) {
            return emptyList()
        }
        for (divider in mnemonicDividers) {
            if (value.contains(divider)) {
                return value.split(divider).filter { it.isNotBlank() }.map {
                    it.removePrefix(",").removeSuffix(",")
                }.map {
                    it.removePrefix(";").removeSuffix(";")
                }.map {
                    it.trim()
                }
            }
        }
        return emptyList()
    }

    fun mnemonicType(mnemonic: List<String>): MnemonicType? {
        val isTon = isValidStandardTonMnemonic(mnemonic)
        val isBep39 = isValidBip39Mnemonic(mnemonic)
        if (isTon && isBep39) {
            return MnemonicType.Both
        }

        if (isTon) {
            return MnemonicType.Ton
        }

        if (isBep39) {
            return MnemonicType.Bip39
        }

        return null
    }

    fun isValidStandardTonMnemonic(mnemonic: List<String>): Boolean {
        if (mnemonic.isEmpty()) {
            return false
        }
        return Mnemonic(mnemonic).isValid()
    }

    private fun isValidBip39Mnemonic(mnemonic: List<String>): Boolean {
        if (mnemonic.isEmpty()) {
            return false
        }
        val words = Mnemonic.bip39English()

        if (mnemonic.size % 3 != 0) {
            return false
        }

        val belongToList = mnemonic.all { word ->
            word in words
        }
        if (!belongToList) {
            return false
        }

        try {
            val bits = mnemonic.joinToString("") { word ->
                val index = words.indexOf(word)
                index.toString(2).padStart(11, '0')
            }

            val dividerIndex = (bits.length / 33) * 32
            val entropy = bits.substring(0, dividerIndex)
            val checksum = bits.substring(dividerIndex)

            val entropyBytes = entropy.chunked(8).map { bin ->
                bin.toInt(2).toByte()
            }.toByteArray()

            val newChecksum = checksumBits(entropyBytes)
            return newChecksum == checksum
        } catch (e: Exception) {
            return false
        }
    }

    private fun checksumBits(entropyBytes: ByteArray): String {
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val hash = messageDigest.digest(entropyBytes)
        val ENT = entropyBytes.size * 8
        val CS = ENT / 32
        return hash.toBinaryString().take(CS)
    }

    fun privateKey(mnemonic: List<String>): PrivateKeyEd25519 {
        return if (isValidStandardTonMnemonic(mnemonic)) {
            PrivateKeyEd25519(Mnemonic(mnemonic).toSeed().copyOf(32))
        } else {
            bip39ToPrivateKey(mnemonic)
        }
    }

    private fun bip39ToPrivateKey(mnemonic: List<String>): PrivateKeyEd25519 {
        if (!isValidBip39Mnemonic(mnemonic)) {
            // TODO TK-535 handle everywhere
            throw IllegalArgumentException("Invalid mnemonic words")
        }

        val seed = bip39ToSeed(mnemonic)
        val derivedKeys = deriveED25519Path("m/44'/607'/0'", hex(seed))
        return PrivateKeyEd25519(derivedKeys.first)
    }

    private fun bip39ToSeed(mnemonic: List<String>): ByteArray {
        val pass = mnemonic.joinToString(" ")
        val salt = "mnemonic"
        val pbdkf2Sha512 = Pbkdf2(
            digest = Sha512(),
            password = pass.toByteArray(),
            salt = salt.toByteArray(),
            iterationCount = 2048,
        )

        return pbdkf2Sha512.deriveKey(512)
            .sliceArray(0 until 64)
    }

    private fun isValidPath(path: String): Boolean {
        if (!pathRegex.matches(path)) {
            return false
        }
        return path.split("/")
            .drop(1)
            .map { it.replace("'", "").toIntOrNull() }
            .none { it == null }
    }

    private fun deriveED25519Path(path: String, seed: String, offset: Long = HARDENED_OFFSET): Pair<ByteArray, ByteArray> {
        if (!isValidPath(path)) {
            throw IllegalArgumentException("Invalid derivation path")
        }
        val masterKeys = getMasterKeyFromSeed(seed)
        val segments = path.split("/")
            .drop(1)
            .map { it.replace("'", "").toInt() + offset }

        return segments.fold(masterKeys) { acc, segment -> CKDPriv(acc, segment) }
    }

    private fun getMasterKeyFromSeed(seed: String): Pair<ByteArray, ByteArray> {
        val hmac = Mac.getInstance("HmacSHA512")
        hmac.init(SecretKeySpec(ED25519_CURVE.toByteArray(), "HmacSHA512"))
        val data = hmac.doFinal(hex(seed))
        return Pair(
            first = data.sliceArray(0 until 32),
            second = data.sliceArray(32 until 64)
        )
    }

    private fun CKDPriv(keys: Pair<ByteArray, ByteArray>, index: Long): Pair<ByteArray, ByteArray> {
        val indexBytes = ByteArray(4)
        indexBytes[3] = (index and 0xFF).toByte()
        indexBytes[2] = ((index shr 8) and 0xFF).toByte()
        indexBytes[1] = ((index shr 16) and 0xFF).toByte()
        indexBytes[0] = ((index shr 24) and 0xFF).toByte()

        val data = byteArrayOf(0) + keys.first + indexBytes
        val hmac = Mac.getInstance("HmacSHA512")
        hmac.init(SecretKeySpec(keys.second, "HmacSHA512"))
        val bytes = hmac.doFinal(data)
        return Pair(
            first = bytes.sliceArray(0 until 32),
            second = bytes.sliceArray(32 until 64)
        )
    }

    private fun ByteArray.toBinaryString(): String {
        return joinToString("") { byte ->
            byte.toUByte().toString(2).padStart(8, '0')
        }
    }
}

