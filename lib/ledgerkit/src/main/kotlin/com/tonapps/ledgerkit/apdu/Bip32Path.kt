package com.tonapps.ledgerkit.apdu

class Bip32Path private constructor(val indices: List<Long>) {

    val depth: Int
        get() = indices.size

    fun encode(): ByteArray {
        val out = ByteArray(1 + indices.size * 4)
        out[0] = indices.size.toByte()
        indices.forEachIndexed { position, index ->
            val offset = 1 + position * 4
            out[offset] = (index ushr 24).toByte()
            out[offset + 1] = (index ushr 16).toByte()
            out[offset + 2] = (index ushr 8).toByte()
            out[offset + 3] = index.toByte()
        }
        return out
    }

    fun isHardened(position: Int): Boolean = indices[position] and HARDENED_BIT != 0L

    override fun toString(): String = "m/" + indices.joinToString("/") { index ->
        if (index and HARDENED_BIT != 0L) { "${index and INDEX_MASK}'" } else { "$index" }
    }

    override fun equals(other: Any?): Boolean = other is Bip32Path && other.indices == indices

    override fun hashCode(): Int = indices.hashCode()

    companion object {

        const val HARDENED_BIT = 0x80000000L
        private const val INDEX_MASK = 0x7fffffffL
        private const val MAX_DEPTH = 10

        fun parse(path: String): Bip32Path {
            val parts = path.trim().removePrefix("m/").removePrefix("M/").split('/').filter { it.isNotEmpty() }
            require(parts.size in 1..MAX_DEPTH) { "Invalid derivation path depth in $path" }
            return Bip32Path(parts.map(::parseComponent))
        }

        fun of(vararg components: Long): Bip32Path {
            require(components.size in 1..MAX_DEPTH) { "Invalid derivation path depth" }
            return Bip32Path(components.toList())
        }

        fun hardened(index: Int): Long {
            require(index >= 0) { "Negative derivation index $index" }
            return index.toLong() or HARDENED_BIT
        }

        fun plain(index: Int): Long {
            require(index >= 0) { "Negative derivation index $index" }
            return index.toLong()
        }

        private fun parseComponent(component: String): Long {
            val isHardened = component.endsWith('\'') || component.endsWith('h') || component.endsWith('H')
            val digits = component.trimEnd('\'', 'h', 'H')
            val index = digits.toLongOrNull()
            require(index != null && index in 0..INDEX_MASK) { "Invalid derivation component $component" }
            return if (isHardened) { index or HARDENED_BIT } else { index }
        }
    }
}
