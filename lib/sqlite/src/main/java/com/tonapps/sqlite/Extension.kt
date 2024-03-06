package com.tonapps.sqlite

import android.database.sqlite.SQLiteDatabase

fun SQLiteDatabase.withTransaction(block: SQLiteDatabase.() -> Long): Long {
    val id: Long
    beginTransaction()
    try {
        id = block()
        setTransactionSuccessful()
    } finally {
        endTransaction()
    }
    return id
}

fun SQLiteDatabase.emptyRawQuery(sql: String) {
    val cursor = rawQuery(sql, null)
    cursor?.close()
}