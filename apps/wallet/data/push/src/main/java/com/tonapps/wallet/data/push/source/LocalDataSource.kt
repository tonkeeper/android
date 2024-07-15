package com.tonapps.wallet.data.push.source

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.tonapps.extensions.toByteArray
import com.tonapps.extensions.toParcel
import com.tonapps.sqlite.SQLiteHelper
import com.tonapps.sqlite.withTransaction
import com.tonapps.wallet.data.push.entities.AppPushEntity

internal class LocalDataSource(context: Context): SQLiteHelper(context, "dapp_push", 1) {

    private companion object {
        private const val TABLE_NAME = "push"
        private const val COLUMN_OBJECT = "object"
        private const val COLUMN_WALLET_ID = "walletId"
        private const val COLUMN_ACCOUNT_ID = "accountId"
        private const val COLUMN_DATE = "date"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $TABLE_NAME (" +
                "$COLUMN_OBJECT BLOB," +
                "$COLUMN_WALLET_ID TEXT," +
                "$COLUMN_ACCOUNT_ID TEXT," +
                "$COLUMN_DATE INTEGER" +
                ");")
        db.execSQL("CREATE INDEX idx_accountId ON $TABLE_NAME($COLUMN_ACCOUNT_ID);")
        db.execSQL("CREATE INDEX idx_walletId ON $TABLE_NAME($COLUMN_WALLET_ID);")
    }

    fun save(
        walletId: String,
        list: List<AppPushEntity>
    ) {
        val query = "INSERT INTO $TABLE_NAME ($COLUMN_OBJECT, $COLUMN_ACCOUNT_ID, $COLUMN_DATE, $COLUMN_WALLET_ID) VALUES (?, ?, ?, ?);"
        writableDatabase.withTransaction {
            delete(TABLE_NAME, "$COLUMN_WALLET_ID = ?", arrayOf(walletId))
            val statement = compileStatement(query)
            for (push in list) {
                statement.bindBlob(1, push.toByteArray())
                statement.bindString(2, push.account)
                statement.bindLong(3, push.dateUnix)
                statement.bindString(4, walletId)
                statement.execute()
                statement.clearBindings()
            }
        }
    }

    fun insert(walletId: String, push: AppPushEntity) {
        writableDatabase.delete(TABLE_NAME, "$COLUMN_WALLET_ID = ? AND $COLUMN_DATE = ?", arrayOf(walletId.toString(), push.dateUnix.toString()))
        val values = ContentValues()
        values.put(COLUMN_OBJECT, push.toByteArray())
        values.put(COLUMN_ACCOUNT_ID, push.account)
        values.put(COLUMN_DATE, push.dateUnix)
        values.put(COLUMN_WALLET_ID, walletId)
        writableDatabase.insert(TABLE_NAME, null, values)
    }

    fun get(walletId: String): List<AppPushEntity> {
        val query = "SELECT $COLUMN_OBJECT FROM $TABLE_NAME WHERE $COLUMN_WALLET_ID = ? ORDER BY $COLUMN_DATE DESC;"
        val cursor = readableDatabase.rawQuery(query, arrayOf(walletId))
        val result = mutableListOf<AppPushEntity>()
        while (cursor.moveToNext()) {
            val bytes = cursor.getBlob(0)
            bytes.toParcel<AppPushEntity>()?.let {
                result.add(it)
            }
        }
        cursor.close()
        return result
    }

}