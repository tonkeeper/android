package com.tonapps.blockchain.ton.extensions

import org.junit.Assert.assertEquals
import org.junit.Test
import org.ton.bitstring.BitString
import org.ton.cell.Cell
import org.ton.cell.buildCell

class BocSerializationTest {

    @Test
    fun sharedSubCellsKeepTopologicalOrder() {
        val root = batchOfTwoMessages()

        val hex = root.hex()

        assertEquals(root.hash(), hex.cellFromHex().hash())
        assertEquals(distinctCells(root).size, hex.bocFromHex().count())
    }

    @Test
    fun sharedSubCellsAreDeduplicated() {
        val root = batchOfTwoMessages()

        assertEquals(20, distinctCells(root).size)
        assertEquals(20, root.hex().bocFromHex().count())
    }

    @Test
    fun singleMessageRoundTrips() {
        val root = message(1)

        assertEquals(root.hash(), root.hex().cellFromHex().hash())
        assertEquals(root.hash(), root.base64().cellFromBase64().hash())
    }

    @Test
    fun emptyCellRoundTrips() {
        val root = Cell.empty()

        assertEquals(root.hash(), root.hex().cellFromHex().hash())
    }

    private fun batchOfTwoMessages(): Cell {
        val actions = buildCell {
            storeUInt(ACTION_SEND_MSG, 32)
            storeUInt(3, 8)
            storeRefs(
                buildCell {
                    storeUInt(ACTION_SEND_MSG, 32)
                    storeUInt(3, 8)
                    storeRefs(Cell.empty(), internalMessage(1))
                },
                internalMessage(2),
            )
        }
        return buildCell {
            storeUInt(EXTERNAL_MESSAGE_INFO, 32)
            storeRef(
                buildCell {
                    storeUInt(SIGN_MAGIC, 32)
                    storeRef(actions)
                }
            )
        }
    }

    private fun internalMessage(marker: Long) = buildCell {
        storeUInt(marker, 64)
        storeRef(message(marker))
    }

    private fun message(marker: Long): Cell {
        val payload = buildCell {
            storeUInt(marker, 256)
            storeRefs(unique(marker, 868), shared(108), shared(204), shared(660))
        }
        val forward = buildCell {
            storeUInt(marker, 256)
            storeRef(payload)
        }
        return buildCell {
            storeUInt(NFT_TRANSFER, 32)
            storeUInt(marker, 64)
            storeRefs(forward, shared(32), shared(4))
        }
    }

    private fun shared(bits: Int) = buildCell { storeUInt(SHARED_VALUE, bits) }

    private fun unique(marker: Long, bits: Int) = buildCell { storeUInt(marker, bits) }

    private fun distinctCells(
        cell: Cell,
        acc: MutableSet<BitString> = mutableSetOf(),
    ): Set<BitString> {
        if (acc.add(cell.hash())) {
            cell.refs.forEach { distinctCells(it, acc) }
        }
        return acc
    }

    private companion object {
        const val ACTION_SEND_MSG = 0x0ec3c86dL
        const val EXTERNAL_MESSAGE_INFO = 0x88014d2eL
        const val SIGN_MAGIC = 0x7369676eL
        const val NFT_TRANSFER = 0x5fcc3d14L
        const val SHARED_VALUE = 1L
    }
}
