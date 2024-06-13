package com.tonapps.sqlite

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

abstract class SQLiteHelper(context: Context, name: String, version: Int): SQLiteOpenHelper(context, name, null, version) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
        db.setMaxSqlCacheSize(SQLiteDatabase.MAX_SQL_CACHE_SIZE)
        db.emptyRawQuery("PRAGMA temp_store = MEMORY; PRAGMA secure_delete = TRUE;")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    }

}
