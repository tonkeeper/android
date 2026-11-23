package com.tonapps.blockchain

import com.tonapps.blockchain.ton.MnemonicType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MnemonicHelperTest {

    private val bip39Words = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        .split(" ")

    private val otherBip39Words = "legal winner thank year wave sausage worth useful legal winner thank yellow"
        .split(" ")

    @Test
    fun candidatesHoldTheBip39KeyOfABip39Seed() {
        val bip39Key = MnemonicHelper.privateKey(bip39Words, MnemonicType.Bip39).publicKey()

        assertTrue(MnemonicHelper.candidatePrivateKeys(bip39Words).any { it.publicKey() == bip39Key })
    }

    @Test
    fun bip39WalletIsRecognisedByItsOwnKey() {
        val publicKey = MnemonicHelper.privateKey(bip39Words, MnemonicType.Bip39).publicKey()

        assertTrue(MnemonicHelper.isBip39Wallet(bip39Words, publicKey))
    }

    @Test
    fun walletWithAnotherKeyIsNotBip39() {
        val other = MnemonicHelper.privateKey(otherBip39Words, MnemonicType.Bip39).publicKey()

        assertFalse(MnemonicHelper.isBip39Wallet(bip39Words, other))
    }

    @Test
    fun wordsOutsideBip39AreNeverBip39() {
        val publicKey = MnemonicHelper.privateKey(bip39Words, MnemonicType.Bip39).publicKey()

        assertFalse(MnemonicHelper.isBip39Wallet(listOf("not", "a", "mnemonic"), publicKey))
    }
}
