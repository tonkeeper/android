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

fun SQLiteDatabase.emptyRawQuery(sql: String) {
    val cursor = rawQuery(sql, null)
    cursor?.close()
}

fun SQLiteDatabase.read(block: SQLiteDatabase.() -> Unit) {
    beginTransaction()
    try {
        block()
        setTransactionSuccessful()
    } finally {
        endTransaction()
    }
}

fun SQLiteDatabase.write(block: SQLiteDatabase.() -> Unit) {
    beginTransaction()
    try {
        block()
        setTransactionSuccessful()
    } finally {
        endTransaction()
    }
}