package com.tonapps.signer.extensions

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