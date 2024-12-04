package com.tonapps.blockchain.ton

import com.tonapps.blockchain.ton.extensions.hmac_sha512
import com.tonapps.blockchain.ton.extensions.pbkdf2_sha512
import org.ton.mnemonic.Mnemonic

object TonMnemonic {

    private val mnemonicDividers = listOf(" ", "\n", ",", ";")
    private val mnemonicWords = Mnemonic.mnemonicWords()

    fun findWords(prefix: String): List<String> {
        if (prefix.isBlank()) {
            return emptyList()
        }
        return mnemonicWords.filter { it.startsWith(prefix, ignoreCase = true) }
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

    fun isValidTONKeychain(list: List<String>): Boolean {
        val mnemonicHash = hmac_sha512("TON Keychain", list.joinToString(" "))
        val result = pbkdf2_sha512(mnemonicHash, "TON Keychain Version".toByteArray(), 1, 64)
        return result.first() == 0.toByte()
    }

    fun parseMnemonic(value: String): List<String> {
        if (3 >= value.length) {
            return emptyList()
        }
        for (divider in mnemonicDividers) {
            if (value.contains(divider)) {
                return value.split(divider).filter { it.isNotBlank() }.map {
                    it.trim()
                }.map {
                    it.removePrefix(",").removeSuffix(",")
                }.map {
                    it.removePrefix(";").removeSuffix(";")
                }
            }
        }
        return emptyList()
    }
}