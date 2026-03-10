package com.tonapps.blockchain.ton

import android.util.Base64

data class TonAddressTags(
    val userFriendly: Boolean,
    val isTestnet: Boolean?,
    val isBounceable: Boolean,
) {

    companion object {

        private const val BOUNCEABLE_TAG: Int = 0x11
        private const val NON_BOUNCEABLE_TAG: Int = 0x51
        private const val TEST_FLAG: Int = 0x80

        @JvmStatic
        val EMPTY = TonAddressTags(false, null, false)

        private fun decodeUserFriendly(value: String): ByteArray? {
            val normalizedValue = value
                .replace("-", "+")
                .replace("_", "/")
            return try {
                val bytes = Base64.decode(normalizedValue, Base64.DEFAULT)
                if (bytes.size != 36) {
                    null
                } else {
                    bytes
                }
            } catch (e: Throwable) {
                null
            }
        }

        fun of(value: String): TonAddressTags {
            if (value.contains(":")) {
                return EMPTY
            }
            val decoded = decodeUserFriendly(value) ?: return EMPTY
            var tag = decoded[0].toInt() and 0xFF
            val isTestnet = (tag and TEST_FLAG) != 0
            if (isTestnet) {
                tag = tag xor TEST_FLAG
            }
            val isBounceable = when (tag) {
                BOUNCEABLE_TAG -> true
                NON_BOUNCEABLE_TAG -> false
                else -> return EMPTY
            }
            return TonAddressTags(
                userFriendly = true,
                isTestnet = isTestnet,
                isBounceable = isBounceable
            )
        }
    }

}