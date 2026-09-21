package com.tonapps.wallet.data.core

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import com.tonapps.async.Async
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

// BundledSQLiteDriver has no counterpart to the framework's DefaultDatabaseErrorHandler, which
// dropped a malformed file on open; without it a corrupted database throws on every connection.
// https://console.firebase.google.com/u/0/project/tonkeeper-3b1dc/crashlytics/app/android:com.ton_keeper/issues/0d9fb9dec84b233f3b6992ae707cf6e8
object DatabaseCorruptionHandler {

    private const val SQLITE_CORRUPT = 11
    private const val SQLITE_NOTADB = 26

    private val errorCodeRegex = Regex("Error code: (\\d+)")

    fun deleteIfCorrupted(context: Context, name: String): Deferred<Boolean> {
        return Async.globalScope().async {
            val file = context.getDatabasePath(name)

            if (!file.exists()) {
                return@async false
            }

            val corruption = findCorruption(file.absolutePath)
                ?: return@async false

            recordException(corruption)
            context.deleteDatabase(name)

            return@async true
        }
    }

    private fun findCorruption(path: String): Throwable? {
        var connection: SQLiteConnection? = null
        try {
            connection = BundledSQLiteDriver().open(path, SQLITE_OPEN_READWRITE)
            // Preparing forces the schema read, which is where a malformed file throws.
            connection.prepare("SELECT 1 FROM sqlite_master LIMIT 0").close()
            return null
        } catch (e: Throwable) {
            if (isCorruption(e)) {
                return e
            }
            return null
        } finally {
            runCatching { connection?.close() }
        }
    }

    private fun isCorruption(e: Throwable): Boolean {
        val message = e.message ?: return false
        if (message.contains("malformed") || message.contains("not a database")) {
            return true
        }
        val code = errorCodeRegex.find(message)?.groupValues?.get(1)?.toIntOrNull() ?: return false
        val primaryCode = code and 0xFF
        return primaryCode == SQLITE_CORRUPT || primaryCode == SQLITE_NOTADB
    }
}