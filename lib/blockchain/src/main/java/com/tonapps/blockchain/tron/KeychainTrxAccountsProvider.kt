package com.tonapps.blockchain.tron

import org.bitcoinj.core.Base58
import org.bouncycastle.jcajce.provider.digest.Keccak
import org.bouncycastle.util.encoders.Hex
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.MnemonicUtils
import java.math.BigInteger

class KeychainTrxAccountsProvider private constructor(
    val mnemonics: List<String>,
    private val hdAccount: Bip32ECKeyPair
) {

    companion object {
        private const val MNEMONICS_WORDS_NUMBER = 12
        private const val CHECKSUM_BITS = 4
        private const val BASE_DERIVATION_PATH = "m/44'/195'/0'/0"
        private const val NETWORK_LABEL = "trx-0x2b6653dc_root"

        fun fromEntropy(entropy: ByteArray): KeychainTrxAccountsProvider {
            val networkEntropy = patchEntropy(entropy)
            val mnemonics = MnemonicUtils.generateMnemonic(networkEntropy)
            val seed = MnemonicUtils.generateSeed(mnemonics, "")
            val masterKeypair = Bip32ECKeyPair.generateKeyPair(seed)
            val hdAccount = Bip32ECKeyPair.deriveKeyPair(masterKeypair, parseDerivationPath(BASE_DERIVATION_PATH))

            return KeychainTrxAccountsProvider(mnemonics.split(" "), hdAccount)
        }

        fun fromMnemonic(mnemonics: List<String>): KeychainTrxAccountsProvider {
            val seed = MnemonicUtils.generateSeed(mnemonics.joinToString(" "), "")
            val masterKeypair = Bip32ECKeyPair.generateKeyPair(seed)
            val hdAccount = Bip32ECKeyPair.deriveKeyPair(masterKeypair, parseDerivationPath(BASE_DERIVATION_PATH))

            return KeychainTrxAccountsProvider(mnemonics, hdAccount)
        }

        private fun patchEntropy(seed: ByteArray): ByteArray {
            val hmac = Mac.getInstance("HmacSHA256")
            hmac.init(SecretKeySpec(NETWORK_LABEL.toByteArray(), "HmacSHA256"))
            val hash = hmac.doFinal(seed)
            val entropySize = (MNEMONICS_WORDS_NUMBER * 11 - CHECKSUM_BITS) / 8
            return hash.copyOfRange(0, entropySize)
        }

        private fun parseDerivationPath(path: String): IntArray {
            return path.split("/").drop(1).map {
                when {
                    it.endsWith("'") -> Integer.parseInt(it.dropLast(1)) or -0x80000000
                    else -> Integer.parseInt(it)
                }
            }.toIntArray()
        }
    }

    private fun getAccount(index: Int = 0): Bip32ECKeyPair {
        return Bip32ECKeyPair.deriveKeyPair(hdAccount, intArrayOf(index))
    }

    fun getAddress(): String {
        val keyPair = getAccount()
        val ecKeyPair = ECKeyPair.create(keyPair.privateKey)

        // Step 1: Get Keccak-256 hash of public key (skip 0x04 prefix for uncompressed)
        val pubKeyBytes = Hex.decode(String.format("%0128x", ecKeyPair.publicKey))
        val keccak = Keccak.Digest256()
        val hash = keccak.digest(pubKeyBytes)

        // Step 2: Take last 20 bytes
        val addressBytes = ByteArray(21)
        addressBytes[0] = 0x41.toByte() // Tron mainnet prefix
        System.arraycopy(hash, 12, addressBytes, 1, 20)

        // Step 3: Calculate checksum (SHA256 x2)
        val checksum = MessageDigest.getInstance("SHA-256")
            .digest(MessageDigest.getInstance("SHA-256").digest(addressBytes))
            .copyOfRange(0, 4)

        // Step 4: Append checksum and encode in Base58
        val addressWithChecksum = addressBytes + checksum
        return Base58.encode(addressWithChecksum)
    }

    fun getPrivateKey(): BigInteger {
        val keyPair = getAccount()
        val ecKeyPair = ECKeyPair.create(keyPair.privateKey)
        return ecKeyPair.privateKey
    }
}
