package com.tonapps.blockchain.contract

interface Derivation {

    object Default : Derivation

    data class Path(val value: String) : Derivation

    data class Binary(val value: ByteArray) : Derivation {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Binary

            if (!value.contentEquals(other.value)) return false

            return true
        }

        override fun hashCode(): Int {
            return value.contentHashCode()
        }
    }
}