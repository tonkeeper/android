package com.tonapps.bus.core.aptabase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

class AptabaseEventStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private var now = 1_725_000_000_000L
    private val directory: File by lazy { File(folder.root, "analytics") }
    private val file: File get() = File(directory, "aptabase_events.jsonl")

    private fun store(
        maxEventCount: Int = 1000,
        maxByteCount: Int = 1024 * 1024,
    ) = AptabaseEventStore(
        directory = directory,
        clock = { now },
        maxEventCount = maxEventCount,
        maxByteCount = maxByteCount,
    )

    private fun names(batch: AptabaseEventStore.Batch) = batch.events.map { it.eventName }

    @Test
    fun appendPeekRemoveRoundTrip() {
        val store = store()
        store.append(testEvent("a", timestamp = now))
        store.append(testEvent("b", timestamp = now))
        store.append(testEvent("c", timestamp = now))

        val batch = store.peek(2)
        assertEquals(listOf("a", "b"), names(batch))
        assertEquals(2L, batch.upperBound)

        store.remove(batch.upperBound)
        assertEquals(listOf("c"), names(store.peek(10)))
        assertEquals(1, store.count)
    }

    @Test
    fun eventsSurviveRestart() {
        store().apply {
            append(testEvent("a", timestamp = now))
            append(testEvent("b", timestamp = now))
        }

        val reloaded = store()
        assertEquals(listOf("a", "b"), names(reloaded.peek(10)))
        assertEquals(testEvent("a", timestamp = now), reloaded.peek(1).events.single())
    }

    @Test
    fun upperBoundStaysValidAcrossRestart() {
        store().apply {
            append(testEvent("a", timestamp = now))
            append(testEvent("b", timestamp = now))
            remove(peek(1).upperBound)
        }

        val reloaded = store()
        val batch = reloaded.peek(10)
        assertEquals(listOf("b"), names(batch))
        assertEquals(1L, batch.upperBound)
        reloaded.remove(batch.upperBound)
        assertEquals(0, reloaded.count)
        assertEquals(0, store().count)
    }

    @Test
    fun corruptLinesAreDroppedOnLoad() {
        directory.mkdirs()
        file.writeText(
            AptabaseCoding.encodeStorage(testEvent("a", timestamp = now)) + "\n" +
                "garbage\n" +
                "\n" +
                AptabaseCoding.encodeStorage(testEvent("b", timestamp = now)) + "\n" +
                "{\"truncated\":"
        )

        val store = store()
        assertEquals(listOf("a", "b"), names(store.peek(10)))

        assertEquals(2, file.readLines().filter { it.isNotBlank() }.size)
    }

    @Test
    fun oldestEventsAreDroppedBeyondCountLimit() {
        val store = store(maxEventCount = 3)
        (1..5).forEach { store.append(testEvent("e$it", timestamp = now)) }

        assertEquals(listOf("e3", "e4", "e5"), names(store.peek(10)))
        assertEquals(2L, store.droppedCount)
        assertEquals(3, file.readLines().size)
    }

    @Test
    fun oldestEventsAreDroppedBeyondByteLimit() {
        val lineSize = (AptabaseCoding.encodeStorage(testEvent("e1", timestamp = now)) + "\n").toByteArray().size
        val store = store(maxByteCount = lineSize * 2)
        (1..4).forEach { store.append(testEvent("e$it", timestamp = now)) }

        assertEquals(listOf("e3", "e4"), names(store.peek(10)))
        assertEquals(2L, store.droppedCount)
        assertTrue(file.length() <= lineSize * 2)
    }

    @Test
    fun removeAfterPruneOnlyDropsPeekedEvents() {
        val store = store(maxEventCount = 3)
        store.append(testEvent("a", timestamp = now))
        store.append(testEvent("b", timestamp = now))
        val batch = store.peek(2)

        store.append(testEvent("c", timestamp = now))
        store.append(testEvent("d", timestamp = now))
        store.remove(batch.upperBound)

        assertEquals(listOf("c", "d"), names(store.peek(10)))
    }

    @Test
    fun expiredEventsAreDroppedOnAppend() {
        val store = store()
        store.append(testEvent("old", timestamp = now))
        now += TimeUnit.HOURS.toMillis(23) + 1
        store.append(testEvent("fresh", timestamp = now))

        val batch = store.peek(10)
        assertEquals(listOf("fresh"), names(batch))
        assertEquals(2L, batch.upperBound)
        assertEquals(1L, store.droppedCount)
        assertEquals(1, file.readLines().size)
    }

    @Test
    fun expiredEventsAreDroppedOnLoad() {
        store().append(testEvent("old", timestamp = now))
        now += TimeUnit.HOURS.toMillis(24)

        assertEquals(0, store().count)
        assertEquals(0L, file.length())
    }

    @Test
    fun expiryOnlyDropsPrefixWhenClockJumpedBackwards() {
        val store = store()
        store.append(testEvent("fresh", timestamp = now))
        store.append(testEvent("ancient", timestamp = now - TimeUnit.DAYS.toMillis(2)))

        assertEquals(listOf("fresh", "ancient"), names(store.peek(10)))
    }

    @Test
    fun purgeRemovesFileAndMemory() {
        val store = store()
        store.append(testEvent("a", timestamp = now))
        store.purge()

        assertEquals(0, store.count)
        assertFalse(file.exists())
        assertEquals(0, store().count)
    }

    @Test
    fun staticPurgeRemovesQueueLeftByPreviousRun() {
        store().append(testEvent("a", timestamp = now))

        AptabaseEventStore.purge(directory)

        assertFalse(file.exists())
        assertFalse(File(directory, "aptabase_events.jsonl.tmp").exists())
    }

    @Test
    fun rewriteLeavesNoTemporaryFile() {
        val store = store(maxEventCount = 1)
        store.append(testEvent("a", timestamp = now))
        store.append(testEvent("b", timestamp = now))

        assertEquals(listOf("aptabase_events.jsonl"), directory.list()!!.toList())
    }

    @Test
    fun removeIgnoresStaleBounds() {
        val store = store()
        store.append(testEvent("a", timestamp = now))
        store.remove(0L)
        store.remove(-5L)
        assertEquals(1, store.count)

        store.remove(100L)
        assertEquals(0, store.count)
    }
}
