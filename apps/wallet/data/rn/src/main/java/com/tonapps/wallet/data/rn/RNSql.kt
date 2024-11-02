package com.tonapps.wallet.data.rn

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.SystemClock
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.sqlite.SQLiteHelper
import org.json.JSONArray
import org.json.JSONObject
import android.database.CursorWindow

internal class RNSql(context: Context): SQLiteHelper(context, DATABASE_NAME, DATABASE_VERSION) {

    private companion object {
        private const val DATABASE_NAME = "RKStorage"
        private const val DATABASE_VERSION = 1
        private const val KV_TABLE_NAME = "catalystLocalStorage"
        private const val KV_TABLE_KEY_COLUMN = "key"
        private const val KV_TABLE_VALUE_COLUMN = "value"
    }

    override fun create(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $KV_TABLE_NAME ($KV_TABLE_KEY_COLUMN TEXT PRIMARY KEY, $KV_TABLE_VALUE_COLUMN TEXT NOT NULL);")
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.execSQL("PRAGMA foreign_keys=OFF;")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            db.setMaxSqlCacheSize(SQLiteDatabase.MAX_SQL_CACHE_SIZE)
        }
        initCursorWindowSize()
    }

    fun getValue(key: String): String? {
        return getValue(key, 0)
    }

    private fun getValue(key: String, attempt: Int = 0): String? {
        try {
            val db = readableDatabase
            val cursor = db.query(KV_TABLE_NAME, arrayOf(KV_TABLE_VALUE_COLUMN), "$KV_TABLE_KEY_COLUMN = ?", arrayOf(key), null, null, null)
            val value = if (cursor.moveToFirst()) cursor.getString(0) else null
            cursor.close()
            return value
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            if (attempt > 3) {
                return null
            }
            SystemClock.sleep(100)
            return getValue(key, attempt + 1)
        }
    }

    fun setValue(key: String, value: String) {
        setValue(key, value, 0)
    }

    private fun setValue(key: String, value: String, attempt: Int = 0) {
        try {
            val values = ContentValues().apply {
                put(KV_TABLE_KEY_COLUMN, key)
                put(KV_TABLE_VALUE_COLUMN, value)
            }
            val db = writableDatabase
            db.insertWithOnConflict(KV_TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            if (attempt > 3) {
                return
            }
            SystemClock.sleep(100)
            return setValue(key, value, attempt + 1)
        }
    }

    fun getJSONObject(key: String): JSONObject? {
        val string = getValue(key) ?: return null
        return try {
            JSONObject(string)
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            null
        }
    }

    fun getJSONArray(key: String): JSONArray? {
        val string = getValue(key) ?: return null
        return try {
            JSONArray(string)
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            null
        }
    }

    fun setJSONObject(key: String, value: JSONObject) {
        setValue(key, value.toString())
    }

    private fun initCursorWindowSize() {
        try {
            val field = CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
            field.isAccessible = true
            field.set(null, 100 * 1024 * 1024) //the 100MB is the new size
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

}