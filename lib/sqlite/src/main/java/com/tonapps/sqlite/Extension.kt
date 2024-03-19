package com.tonapps.sqlite

import android.database.sqlite.SQLiteDatabase

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