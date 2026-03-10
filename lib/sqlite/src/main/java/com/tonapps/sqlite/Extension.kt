package com.tonapps.sqlite

import android.database.sqlite.SQLiteDatabase

fun SQLiteDatabase.query(
    query: String,
    args: Array<String>
): ByteArray? {
    val cursor = rawQuery(query, args)
    val result = if (cursor.moveToNext()) {
        cursor.getBlob(0)
    } else {
        null
    }
    cursor.close()
    if (result == null || result.isEmpty()) {
        return null
    }
    return result

}

fun SQLiteDatabase.withTransaction(block: SQLiteDatabase.() -> Unit) {
    beginTransaction()
    try {
        block()
        setTransactionSuccessful()
    } catch (ignored: Exception) {
    } finally {
        endTransaction()
    }
}

fun SQLiteDatabase.emptyRawQuery(sql: String) {
    val cursor = rawQuery(sql, null)
    cursor?.close()
}
