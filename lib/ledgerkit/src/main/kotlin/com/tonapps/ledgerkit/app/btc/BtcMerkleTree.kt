package com.tonapps.ledgerkit.app.btc

class BtcMerkleTree(private val leaves: List<ByteArray>) {

    init {
        require(leaves.all { it.size == HASH_LENGTH }) { "Merkle leaves must be 32-byte hashes" }
    }

    val size: Int
        get() = leaves.size

    val root: ByteArray by lazy { if (leaves.isEmpty()) { ByteArray(HASH_LENGTH) } else { subtreeRoot(0, leaves.size) }}

    fun leaf(index: Int): ByteArray = leaves[index]

    fun indexOf(leafHash: ByteArray): Int = leaves.indexOfFirst { it.contentEquals(leafHash) }

    fun proof(index: Int): List<ByteArray> {
        require(index in leaves.indices) { "Leaf index out of range" }
        val path = mutableListOf<ByteArray>()
        collectProof(0, leaves.size, index, path)
        return path.asReversed().toList()
    }

    private fun collectProof(begin: Int, count: Int, index: Int, path: MutableList<ByteArray>) {
        if (count == 1) {
            return
        }
        val leftCount = largestPowerOfTwoBelow(count)
        if (index < begin + leftCount) {
            path += subtreeRoot(begin + leftCount, count - leftCount)
            collectProof(begin, leftCount, index, path)
        } else {
            path += subtreeRoot(begin, leftCount)
            collectProof(begin + leftCount, count - leftCount, index, path)
        }
    }

    private fun subtreeRoot(begin: Int, count: Int): ByteArray {
        if (count == 1) {
            return leaves[begin]
        }
        val leftCount = largestPowerOfTwoBelow(count)
        return combine(subtreeRoot(begin, leftCount), subtreeRoot(begin + leftCount, count - leftCount))
    }

    companion object {

        const val HASH_LENGTH = 32

        fun elementHash(element: ByteArray): ByteArray = sha256(byteArrayOf(0x00) + element)

        fun combine(left: ByteArray, right: ByteArray): ByteArray = sha256(byteArrayOf(0x01) + left + right)

        fun ofElements(elements: List<ByteArray>): BtcMerkleTree = BtcMerkleTree(elements.map(::elementHash))

        private fun largestPowerOfTwoBelow(count: Int): Int {
            var power = 1
            while (power * 2 < count) {
                power *= 2
            }
            return power
        }
    }
}

class BtcMerkleizedMap(entries: Map<ByteArrayKey, ByteArray>) {

    val sortedKeys: List<ByteArray> = entries.keys.sorted().map { it.bytes }
    val sortedValues: List<ByteArray> = entries.keys.sorted().map { entries.getValue(it) }

    val size: Int
        get() = sortedKeys.size

    fun commitment(): ByteArray = CompactSize.encode(size) +
        BtcMerkleTree.ofElements(sortedKeys).root +
        BtcMerkleTree.ofElements(sortedValues).root
}

class ByteArrayKey(val bytes: ByteArray) : Comparable<ByteArrayKey> {

    override fun compareTo(other: ByteArrayKey): Int {
        for (index in 0 until minOf(bytes.size, other.bytes.size)) {
            val difference = (bytes[index].toInt() and 0xff) - (other.bytes[index].toInt() and 0xff)
            if (difference != 0) {
                return difference
            }
        }
        return bytes.size - other.bytes.size
    }

    override fun equals(other: Any?): Boolean = other is ByteArrayKey && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()
}
