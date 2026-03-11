package com.tonapps.blockchain.ton

import org.ton.crypto.digest.sha256

/**
 * Signature domain for L2 networks.
 *
 * TL scheme:
 *   signature_domain.l2#71b34ee1 global_id:int = SignatureDomain;
 *
 * When the SignatureDomain VM capability is enabled, CHKSIGNU/CHKSIGNS prepend a
 * 32-byte prefix (sha256 of the TL representation) to the verified data.
 *
 * For CHKSIGNU:  data_to_check = prefix(32) + cell_hash(32)
 * For CHKSIGNS:  data_to_check = prefix(32) + sha256(slice_data)
 */
object SignatureDomain {

    private const val L2_TL_ID: Int = 0x71b34ee1.toInt()

    /**
     * Computes the 32-byte domain prefix for a given L2 network global_id:
     *   sha256( TL_ID_LE4 || global_id_LE4 )
     */
    fun l2Prefix(globalId: Int): ByteArray {
        val input = ByteArray(8)
        input[0] = (L2_TL_ID and 0xFF).toByte()
        input[1] = ((L2_TL_ID ushr 8) and 0xFF).toByte()
        input[2] = ((L2_TL_ID ushr 16) and 0xFF).toByte()
        input[3] = ((L2_TL_ID ushr 24) and 0xFF).toByte()
        input[4] = (globalId and 0xFF).toByte()
        input[5] = ((globalId ushr 8) and 0xFF).toByte()
        input[6] = ((globalId ushr 16) and 0xFF).toByte()
        input[7] = ((globalId ushr 24) and 0xFF).toByte()
        return sha256(input)
    }

    /**
     * Prepends the L2 domain prefix to a cell hash (or any 32-byte hash).
     * This is the full 64-byte payload that CHKSIGNU verifies against the ed25519 signature.
     */
    fun prefixedHash(globalId: Int, hash: ByteArray): ByteArray = l2Prefix(globalId) + hash
}
