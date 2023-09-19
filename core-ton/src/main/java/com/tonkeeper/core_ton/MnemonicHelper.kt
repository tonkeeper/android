package com.tonkeeper.core_ton

import com.tonkeeper.core_ton.data.TonAccount
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.crypto.Ed25519
import org.ton.mnemonic.Mnemonic

object MnemonicHelper {

    suspend fun generate(): List<String> {
        return Mnemonic.generate()
    }

    fun getSeed(mnemonic: List<String>): ByteArray {
        return Mnemonic.toSeed(mnemonic)
    }

    fun search(text: String, max: Int = 3): List<String> {
        val words = Mnemonic.mnemonicWords()
        val result = mutableListOf<String>()
        for (word in words) {
            if (word.startsWith(text)) {
                result.add(word)
                if (result.size == max) {
                    break
                }
            }
        }
        return result
    }

    fun isExistWord(word: String): Boolean {
        return Mnemonic.mnemonicWords().contains(word)
    }

    /*fun getAccount(mnemonic: List<String>): TonAccount {
        val seed = getSeed(mnemonic)
        val privateKey = PrivateKeyEd25519(seed).key.toByteArray()
        val publicKey = Ed25519.publicKey(privateKey)
        val sharedKey = Ed25519.sharedKey(privateKey, publicKey)

        return TonAccount(mnemonic, privateKey, publicKey, sharedKey)
    }*/
}
