package com.tonapps.blockchain.model.account

interface Derivation {

    object DefaultPath : Derivation

    data class Path(val value: String) : Derivation

    data class Binary(val value: ByteArray) : Derivation {
        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }

            if (other == null || this::class != other::class) {
                return false
            }

            other as Binary

            return value.contentEquals(other.value)
        }

        override fun hashCode(): Int {
            return value.contentHashCode()
        }
    }
}