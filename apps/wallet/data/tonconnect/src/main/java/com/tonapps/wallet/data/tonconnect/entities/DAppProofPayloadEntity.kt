package com.tonapps.wallet.data.tonconnect.entities

data class DAppProofPayloadEntity(
    val bufferToSign: ByteArray,
    val domainBuffer: ByteArray,
    val payload: String,
    val origin: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DAppProofPayloadEntity

        if (!bufferToSign.contentEquals(other.bufferToSign)) return false
        if (!domainBuffer.contentEquals(other.domainBuffer)) return false
        if (payload != other.payload) return false
        if (origin != other.origin) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bufferToSign.contentHashCode()
        result = 31 * result + domainBuffer.contentHashCode()
        result = 31 * result + payload.hashCode()
        result = 31 * result + origin.hashCode()
        return result
    }
}