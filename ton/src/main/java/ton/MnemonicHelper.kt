package ton

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

    fun isValidWord(word: String): Boolean {
        return Mnemonic.mnemonicWords().contains(word)
    }

    fun isValidWords(words: List<String>): Boolean {
        return words.all { word -> isValidWord(word) }
    }
}
