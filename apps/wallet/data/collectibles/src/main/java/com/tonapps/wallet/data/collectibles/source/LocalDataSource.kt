package com.tonapps.wallet.data.collectibles.source

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.tonapps.extensions.toByteArray
import com.tonapps.extensions.toParcel
import com.tonapps.sqlite.SQLiteHelper
import com.tonapps.sqlite.withTransaction
import com.tonapps.wallet.data.collectibles.entities.NftEntity

internal class LocalDataSource(context: Context): SQLiteHelper(context, "collectibles", 1) {

    private companion object {
        private const val TABLE_NAME = "collectibles"
        private const val COLUMN_ID = "id"
        private const val COLUMN_OBJECT = "object"
        private const val COLUMN_ACCOUNT_ID = "accountId"

        fun accountId(value: String, testnet: Boolean): String {
            return if (testnet) {
                "testnet:$value"
            } else {
                value
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $TABLE_NAME (" +
                "$COLUMN_ID TEXT UNIQUE," +
                "$COLUMN_OBJECT BLOB," +
                "$COLUMN_ACCOUNT_ID TEXT" +
                ");")
        db.execSQL("CREATE INDEX idx_accountId ON $TABLE_NAME($COLUMN_ACCOUNT_ID);")
    }

    fun clear(
        accountId: String,
        testnet: Boolean
    ) {
        writableDatabase.delete(TABLE_NAME, "$COLUMN_ACCOUNT_ID = ?", arrayOf(accountId(accountId, testnet)))
    }

    fun save(
        accountId: String,
        testnet: Boolean,
        list: List<NftEntity>,
    ) {
        clear(accountId, testnet)
        writableDatabase.withTransaction {
            for (nft in list) {
                val values = ContentValues()
                values.put(COLUMN_ID, nft.id)
                values.put(COLUMN_OBJECT, nft.toByteArray())
                values.put(COLUMN_ACCOUNT_ID, accountId(accountId, testnet))
                writableDatabase.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
    }

    fun getSingle(
        accountId: String,
        testnet: Boolean,
        id: String
    ): NftEntity? {
        val query = "SELECT $COLUMN_OBJECT FROM $TABLE_NAME WHERE $COLUMN_ACCOUNT_ID = ? AND $COLUMN_ID = ? LIMIT 1;"
        val cursor = readableDatabase.rawQuery(query, arrayOf(accountId(accountId, testnet), id))
        val result = if (cursor.moveToNext()) {
            val bytes = cursor.getBlob(0)
            bytes.toParcel<NftEntity>()
        } else {
            null
        }
        cursor.close()
        return result
    }

    fun get(
        accountId: String,
        testnet: Boolean
    ): List<NftEntity> {
        val query = "SELECT $COLUMN_OBJECT FROM $TABLE_NAME WHERE $COLUMN_ACCOUNT_ID = ? LIMIT 1000;"
        val cursor = readableDatabase.rawQuery(query, arrayOf(accountId(accountId, testnet)))
        val result = mutableListOf<NftEntity>()
        while (cursor.moveToNext()) {
            val bytes = cursor.getBlob(0)
            bytes.toParcel<NftEntity>()?.let {
                result.add(it)
            }
        }
        cursor.close()
        return result
    }
}