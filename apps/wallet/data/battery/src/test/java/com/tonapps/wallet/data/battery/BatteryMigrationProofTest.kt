package com.tonapps.wallet.data.battery

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ton.kotlin.crypto.PrivateKeyEd25519

class BatteryMigrationProofTest {

    private val walletId = "aia4kzm6zmtrdhv3iqy3knmnjaxoqy54migq"

    @Test
    fun messageLengthPrefixesTheDomainTheWalletIdAndThePublicKey() {
        val publicKey = ByteArray(32) { 7 }

        assertArrayEquals(
            byteArrayOf(25) + "keeper.battery.migrate.v1".toByteArray() +
                byteArrayOf(36) + walletId.toByteArray() +
                byteArrayOf(32) + publicKey,
            BatteryMigrationProof.message(walletId, publicKey),
        )
    }

    @Test
    fun proofIsTheLegacyKeysSignatureOfTheMessage() {
        val privateKey = PrivateKeyEd25519(ByteArray(32) { 3 })
        val publicKey = privateKey.publicKey()

        val proof = BatteryMigrationProof.create(walletId, publicKey.key.toByteArray()) {
            privateKey.signToByteArray(it)
        }

        val signature = requireNotNull(proof).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val message = requireNotNull(BatteryMigrationProof.message(walletId, publicKey.key.toByteArray()))
        assertEquals(64, signature.size)
        assertTrue(publicKey.verifySignature(message, signature))
    }

    @Test
    fun fieldLongerThanItsLengthByteHasNoMessage() {
        assertNull(BatteryMigrationProof.message("a".repeat(256), ByteArray(32)))
    }
}
