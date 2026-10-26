package com.tonapps.bus.core.aptabase

import com.tonapps.log.L
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val FILE_NAME = "aptabase_events.jsonl"
private const val TEMP_FILE_NAME = "$FILE_NAME.tmp"
private const val DEFAULT_MAX_EVENT_COUNT = 1000
private const val DEFAULT_MAX_BYTE_COUNT = 1024 * 1024
private val DEFAULT_TIME_TO_LIVE_MILLIS = TimeUnit.HOURS.toMillis(23)
private const val NEWLINE = '\n'.code.toByte()

class AptabaseEventStore(
    private val directory: File,
    private val clock: () -> Long,
    private val maxEventCount: Int = DEFAULT_MAX_EVENT_COUNT,
    private val maxByteCount: Int = DEFAULT_MAX_BYTE_COUNT,
    private val timeToLiveMillis: Long = DEFAULT_TIME_TO_LIVE_MILLIS,
) {

    class Batch(
        val events: List<AptabaseEvent>,
        val upperBound: Long,
    )

    private class Entry(
        val event: AptabaseEvent,
        val line: ByteArray,
    )

    private val file: File
        get() = File(directory, FILE_NAME)

    private val tempFile: File
        get() = File(directory, TEMP_FILE_NAME)

    private val entries = ArrayList<Entry>()
    private var byteCount = 0
    private var isLoaded = false
    private var removedCount = 0L

    var droppedCount = 0L
        private set

    val count: Int
        get() {
            load()
            return entries.size
        }

    fun load() {
        if (isLoaded) {
            return
        }
        isLoaded = true
        val bytes = readFile() ?: return
        var start = 0
        while (start < bytes.size) {
            var end = start
            while (end < bytes.size && bytes[end] != NEWLINE) {
                end++
            }
            if (end > start) {
                val line = String(bytes, start, end - start, Charsets.UTF_8)
                AptabaseCoding.decodeStorage(line)?.let { event ->
                    entries.add(Entry(event, bytes.copyOfRange(start, end) + NEWLINE))
                }
            }
            start = end + 1
        }
        byteCount = entries.sumOf { it.line.size }
        prune()
        rewrite()
    }

    fun append(event: AptabaseEvent) {
        load()
        val line = (AptabaseCoding.encodeStorage(event) + "\n").toByteArray(Charsets.UTF_8)
        entries.add(Entry(event, line))
        byteCount += line.size
        if (prune()) {
            rewrite()
        } else {
            appendToFile(line)
        }
    }

    fun peek(limit: Int): Batch {
        load()
        if (pruneExpired()) {
            rewrite()
        }
        val events = entries.take(limit).map { it.event }
        return Batch(events, removedCount + events.size)
    }

    fun remove(upTo: Long) {
        load()
        val count = (upTo - removedCount).coerceIn(0L, entries.size.toLong()).toInt()
        if (count == 0) {
            return
        }
        dropFirst(count)
        rewrite()
    }

    fun purge() {
        isLoaded = true
        entries.clear()
        byteCount = 0
        purge(directory)
    }

    private fun prune(): Boolean {
        var didDrop = pruneExpired()
        while (entries.isNotEmpty() && (entries.size > maxEventCount || byteCount > maxByteCount)) {
            dropFirst(1)
            droppedCount++
            didDrop = true
        }
        return didDrop
    }

    private fun pruneExpired(): Boolean {
        val deadline = clock() - timeToLiveMillis
        val firstFresh = entries.indexOfFirst { it.event.timestamp >= deadline }
        val expired = if (firstFresh < 0) {
            entries.size
        } else {
            firstFresh
        }
        if (expired == 0) {
            return false
        }
        dropFirst(expired)
        droppedCount += expired
        return true
    }

    private fun dropFirst(count: Int) {
        val removed = entries.subList(0, count)
        byteCount -= removed.sumOf { it.line.size }
        removed.clear()
        removedCount += count
    }

    private fun readFile(): ByteArray? {
        val file = file
        if (!file.exists()) {
            return null
        }
        return try {
            file.readBytes()
        } catch (e: IOException) {
            L.w(e, "Aptabase: failed to read the event queue")
            null
        }
    }

    private fun rewrite() {
        try {
            directory.mkdirs()
            FileOutputStream(tempFile).use { output ->
                entries.forEach { output.write(it.line) }
            }
            if (!tempFile.renameTo(file)) {
                file.delete()
                if (!tempFile.renameTo(file)) {
                    throw IOException("rename failed")
                }
            }
        } catch (e: IOException) {
            L.w(e, "Aptabase: failed to rewrite the event queue")
        }
    }

    private fun appendToFile(line: ByteArray) {
        try {
            directory.mkdirs()
            FileOutputStream(file, true).use { output ->
                output.write(line)
            }
        } catch (e: IOException) {
            L.w(e, "Aptabase: failed to append to the event queue")
        }
    }

    companion object {

        fun purge(directory: File) {
            File(directory, FILE_NAME).delete()
            File(directory, TEMP_FILE_NAME).delete()
        }
    }
}
