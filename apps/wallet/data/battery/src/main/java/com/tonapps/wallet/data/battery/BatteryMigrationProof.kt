package com.tonapps.wallet.data.battery

import com.tonapps.extensions.toHex

object BatteryMigrationProof {

    private const val DOMAIN = "keeper.battery.migrate.v1"
    private const val MAX_FIELD_SIZE = 255

    fun create(walletId: String, publicKey: ByteArray, sign: (ByteArray) -> ByteArray): String? {
        return message(walletId, publicKey)?.let { sign(it).toHex() }
    }

    internal fun message(walletId: String, publicKey: ByteArray): ByteArray? {
        val fields = listOf(DOMAIN.toByteArray(), walletId.toByteArray(), publicKey)
        if (fields.any { it.size > MAX_FIELD_SIZE }) {
            return null
        }
        return fields.fold(ByteArray(0)) { message, field -> message + field.size.toByte() + field }
    }
}
