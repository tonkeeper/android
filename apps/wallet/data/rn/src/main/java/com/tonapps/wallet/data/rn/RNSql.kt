package com.tonapps.wallet.data.rn

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.tonapps.sqlite.SQLiteHelper
import org.json.JSONObject

internal class RNSql(context: Context): SQLiteHelper(context, DATABASE_NAME, DATABASE_VERSION) {

    private companion object {
        private const val DATABASE_NAME = "RKStorage"
        private const val DATABASE_VERSION = 1
        private const val KV_TABLE_NAME = "catalystLocalStorage"
        private const val KV_TABLE_KEY_COLUMN = "key"
        private const val KV_TABLE_VALUE_COLUMN = "value"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $KV_TABLE_NAME ($KV_TABLE_KEY_COLUMN TEXT PRIMARY KEY, $KV_TABLE_VALUE_COLUMN TEXT NOT NULL);")
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.execSQL("PRAGMA foreign_keys=OFF;")
    }

    fun getValue(key: String): String? {
        val db = readableDatabase
        val cursor = db.query(KV_TABLE_NAME, arrayOf(KV_TABLE_VALUE_COLUMN), "$KV_TABLE_KEY_COLUMN = ?", arrayOf(key), null, null, null)
        val value = if (cursor.moveToFirst()) cursor.getString(0) else null
        cursor.close()
        db.close()
        return value
    }

    fun setValue(key: String, value: String) {
        val values = ContentValues().apply {
            put(KV_TABLE_KEY_COLUMN, key)
            put(KV_TABLE_VALUE_COLUMN, value)
        }
        val db = writableDatabase
        db.insertWithOnConflict(KV_TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        db.close()
    }

    fun getJSONObject(key: String): JSONObject? {
        val string = getValue(key) ?: return null
        return try {
            JSONObject(string)
        } catch (e: Throwable) {
            null
        }
    }

    fun setJSONObject(key: String, value: JSONObject) {
        setValue(key, value.toString())
    }

}