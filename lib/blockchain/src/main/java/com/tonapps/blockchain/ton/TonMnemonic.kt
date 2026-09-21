package com.tonapps.blockchain.ton

import com.tonapps.blockchain.ton.extensions.hmac_sha512
import com.tonapps.blockchain.ton.extensions.pbkdf2_sha512
import org.ton.kotlin.crypto.mnemonic.Mnemonic

object TonMnemonic {
    fun isValidTONKeychain(list: List<String>): Boolean {
        val mnemonicHash = hmac_sha512("TON Keychain", list.joinToString(" "))
        val result = pbkdf2_sha512(mnemonicHash, "TON Keychain Version".toByteArray(), 1, 64)
        return result.first() == 0.toByte()
    }
}