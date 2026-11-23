package com.tonapps.ledgerkit.app.btc

import java.io.ByteArrayOutputStream

class BtcClientCommandInterpreter {

    private val preimages = HashMap<ByteArrayKey, ByteArray>()
    private val trees = HashMap<ByteArrayKey, BtcMerkleTree>()
    private val queue = ArrayDeque<ByteArray>()
    private val yieldedValues = mutableListOf<ByteArray>()

    val yielded: List<ByteArray>
        get() = yieldedValues

    fun addKnownPreimage(element: ByteArray) {
        preimages[ByteArrayKey(sha256(element))] = element
    }

    fun addKnownList(elements: List<ByteArray>) {
        elements.forEach { addKnownPreimage(byteArrayOf(0x00) + it) }
        val tree = BtcMerkleTree.ofElements(elements)
        trees[ByteArrayKey(tree.root)] = tree
    }

    fun addKnownMapping(map: BtcMerkleizedMap) {
        addKnownList(map.sortedKeys)
        addKnownList(map.sortedValues)
    }

    fun execute(request: ByteArray): ByteArray {
        require(request.isNotEmpty()) { "Empty client command" }
        return when (request[0].toInt() and 0xff) {
            YIELD -> {
                yieldedValues += request.copyOfRange(1, request.size)
                ByteArray(0)
            }
            GET_PREIMAGE -> getPreimage(request)
            GET_MERKLE_LEAF_PROOF -> getMerkleLeafProof(request)
            GET_MERKLE_LEAF_INDEX -> getMerkleLeafIndex(request)
            GET_MORE_ELEMENTS -> getMoreElements(request)
            else -> throw IllegalStateException("Unexpected client command ${request[0].toInt() and 0xff}")
        }
    }

    private fun getPreimage(request: ByteArray): ByteArray {
        require(request.size == 34 && request[1] == 0.toByte()) { "Unsupported GET_PREIMAGE request" }
        val hash = request.copyOfRange(2, 34)
        val preimage = preimages[ByteArrayKey(hash)] ?: throw IllegalStateException("Unknown preimage requested")
        val lengthPrefix = CompactSize.encode(preimage.size)
        val maxPayload = 255 - lengthPrefix.size - 1
        val payloadSize = minOf(maxPayload, preimage.size)
        for (index in payloadSize until preimage.size) {
            queue.addLast(byteArrayOf(preimage[index]))
        }
        return lengthPrefix + byteArrayOf(payloadSize.toByte()) + preimage.copyOfRange(0, payloadSize)
    }

    private fun getMerkleLeafProof(request: ByteArray): ByteArray {
        require(request.size >= 35) { "Truncated GET_MERKLE_LEAF_PROOF request" }
        val root = request.copyOfRange(1, 33)
        val (treeSize, treeSizeLength) = CompactSize.decode(request, 33)
        val (leafIndex, leafIndexLength) = CompactSize.decode(request, 33 + treeSizeLength)
        require(33 + treeSizeLength + leafIndexLength == request.size) { "Malformed GET_MERKLE_LEAF_PROOF request" }
        val tree = trees[ByteArrayKey(root)] ?: throw IllegalStateException("Unknown Merkle root requested")
        require(leafIndex < treeSize && tree.size.toLong() == treeSize) { "Invalid Merkle proof request" }
        check(queue.isEmpty()) { "Merkle proof requested while elements are pending" }
        val proof = tree.proof(leafIndex.toInt())
        val inline = minOf(MAX_INLINE_PROOF_ELEMENTS, proof.size)
        proof.drop(inline).forEach(queue::addLast)
        val out = ByteArrayOutputStream()
        out.appendBytes(tree.leaf(leafIndex.toInt()))
        out.write(proof.size)
        out.write(inline)
        proof.take(inline).forEach { out.appendBytes(it) }
        return out.toByteArray()
    }

    private fun getMerkleLeafIndex(request: ByteArray): ByteArray {
        require(request.size == 65) { "Malformed GET_MERKLE_LEAF_INDEX request" }
        val root = request.copyOfRange(1, 33)
        val leafHash = request.copyOfRange(33, 65)
        val tree = trees[ByteArrayKey(root)] ?: throw IllegalStateException("Unknown Merkle root requested")
        val index = tree.indexOf(leafHash)
        return if (index >= 0) { byteArrayOf(1) + CompactSize.encode(index) } else { byteArrayOf(0) + CompactSize.encode(0) }
    }

    private fun getMoreElements(request: ByteArray): ByteArray {
        require(request.size == 1) { "Malformed GET_MORE_ELEMENTS request" }
        check(queue.isNotEmpty()) { "No pending elements" }
        val elementLength = queue.first().size
        check(queue.all { it.size == elementLength }) { "Pending elements have different lengths" }
        val out = ByteArrayOutputStream()
        var count = 0
        while (queue.isNotEmpty() && out.size() + elementLength <= MAX_MORE_ELEMENTS_PAYLOAD) {
            out.appendBytes(queue.removeFirst())
            count++
        }
        return byteArrayOf(count.toByte(), elementLength.toByte()) + out.toByteArray()
    }

    private companion object {
        const val YIELD = 0x10
        const val GET_PREIMAGE = 0x40
        const val GET_MERKLE_LEAF_PROOF = 0x41
        const val GET_MERKLE_LEAF_INDEX = 0x42
        const val GET_MORE_ELEMENTS = 0xa0
        const val MAX_INLINE_PROOF_ELEMENTS = (255 - 32 - 1 - 1) / 32
        const val MAX_MORE_ELEMENTS_PAYLOAD = 253
    }
}
