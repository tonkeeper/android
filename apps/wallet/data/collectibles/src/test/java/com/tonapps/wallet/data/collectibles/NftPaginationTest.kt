package com.tonapps.wallet.data.collectibles

import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class NftPaginationTest {
    private data class Nft(val address: String, val revision: Int = 0)
    private fun items(count: Int) = List(count) { Nft("nft-$it") }

    @Test fun boundariesAndRawOffsets() {
        for (count in listOf(0, 1, 999, 1000, 1001, 2501)) {
            val source = items(count)
            val offsets = mutableListOf<Int>()
            val result = loadAllNftPages({ offset, limit ->
                offsets.add(offset)
                assertEquals(1000, limit)
                source.drop(offset).take(limit)
            }, Nft::address)
            assertEquals(source, result)
            assertEquals(count, offsets.last())
        }
    }

    @Test fun shortPagesDoNotTruncate() {
        val source = items(2501)
        assertEquals(source, loadAllNftPages({ offset, _ -> source.drop(offset).take(73) }, Nft::address))
    }

    @Test fun overlapsDeduplicateAndKeepRawOffsets() {
        val pages = mutableListOf(listOf(Nft("a"), Nft("b")), listOf(Nft("b", 1), Nft("c")), emptyList())
        val offsets = mutableListOf<Int>()
        val result = loadAllNftPages({ offset, _ -> offsets.add(offset); pages.removeAt(0) }, Nft::address)
        assertEquals(listOf(0, 2, 4), offsets)
        assertEquals(listOf(Nft("a"), Nft("b", 1), Nft("c")), result)
    }

    @Test fun repeatedPageFailsInsteadOfLooping() {
        var calls = 0
        assertNull(loadAllNftPages({ _, _ -> calls++; items(1000) }, Nft::address))
        assertEquals(2, calls)
    }

    @Test fun nullPageDoesNotReturnPartialInventory() {
        assertNull(loadAllNftPages({ offset, _ -> if (offset == 0) items(1000) else null }, Nft::address))
        assertEquals(emptyList<Nft>(), loadAllNftPages({ _, _ -> emptyList<Nft>() }, Nft::address))
    }

    @Test fun thrownFailureDoesNotReturnPartialInventory() {
        val failure = IllegalStateException("offline")
        val actual = assertThrows(IllegalStateException::class.java) {
            loadAllNftPages({ offset, _ -> if (offset == 0) items(1000) else throw failure }, Nft::address)
        }
        assertSame(failure, actual)
    }

    @Test fun cancellationBeforeFetch() {
        var calls = 0
        assertThrows(CancellationException::class.java) {
            loadAllNftPages({ _, _ -> calls++; items(1000) }, Nft::address) { throw CancellationException() }
        }
        assertEquals(0, calls)
    }

    @Test fun cancellationAfterFetchIncludingEmptyOrFailedResponses() {
        for (page in listOf(items(1000), emptyList(), null)) {
            var cancelled = false
            var calls = 0
            assertThrows(CancellationException::class.java) {
                loadAllNftPages({ _, _ -> calls++; cancelled = true; page }, Nft::address) {
                    if (cancelled) throw CancellationException()
                }
            }
            assertEquals(1, calls)
        }
    }

    @Test fun independentInventoriesDoNotShareState() {
        for (owner in listOf("a", "b")) {
            assertEquals(listOf(Nft(owner)), loadAllNftPages({ offset, _ ->
                if (offset == 0) listOf(Nft(owner)) else emptyList()
            }, Nft::address))
        }
    }
}
