package com.tonapps.wallet.data.collectibles.source

import android.content.Context
import android.database.sqlite.SQLiteDatabase
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

    fun save(
        accountId: String,
        testnet: Boolean,
        list: List<NftEntity>,
    ) {
        val query = "INSERT INTO collectibles (id, object, accountId) VALUES (?, ?, ?);"
        writableDatabase.withTransaction {
            delete(TABLE_NAME, "$COLUMN_ACCOUNT_ID = ?", arrayOf(accountId))
            val statement = compileStatement(query)
            for (nft in list) {
                statement.bindString(1, nft.id)
                statement.bindBlob(2, nft.toByteArray())
                statement.bindString(3, accountId(accountId, testnet))
                statement.execute()
                statement.clearBindings()
            }
        }
    }

    fun getSingle(
        accountId: String,
        testnet: Boolean,
        id: String
    ): NftEntity? {
        val query = "SELECT $COLUMN_OBJECT FROM $TABLE_NAME WHERE $COLUMN_ACCOUNT_ID = ? AND $COLUMN_ID = ?;"
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