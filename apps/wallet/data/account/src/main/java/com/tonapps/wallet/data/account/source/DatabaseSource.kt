package com.tonapps.wallet.data.account.source

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.tonapps.blockchain.ton.contract.walletVersion
import com.tonapps.extensions.toByteArray
import com.tonapps.extensions.toParcel
import com.tonapps.sqlite.SQLiteHelper
import com.tonapps.sqlite.withTransaction
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.entities.WalletLabel
import com.tonapps.wallet.data.account.walletType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import org.ton.api.pub.PublicKeyEd25519

internal class DatabaseSource(
    context: Context,
    private val scope: CoroutineScope
): SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private companion object {
        private const val DATABASE_NAME = "account"
        private const val DATABASE_VERSION = 1

        private const val WALLET_TABLE_NAME = "wallet"
        private const val WALLET_TABLE_ID_COLUMN = "id"
        private const val WALLET_TABLE_ID_PUBLIC_KEY = "public_key"
        private const val WALLET_TABLE_TYPE = "type"
        private const val WALLET_TABLE_VERSION = "version"
        private const val WALLET_TABLE_LABEL = "label"

        private fun WalletEntity.toValues(): ContentValues {
            val values = ContentValues()
            values.put(WALLET_TABLE_ID_COLUMN, id)
            values.put(WALLET_TABLE_ID_PUBLIC_KEY, publicKey.key.toByteArray())
            values.put(WALLET_TABLE_TYPE, type.id)
            values.put(WALLET_TABLE_VERSION, version.id)
            values.put(WALLET_TABLE_LABEL, label.toByteArray())
            return values
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $WALLET_TABLE_NAME (" +
                "$WALLET_TABLE_ID_COLUMN TEXT PRIMARY KEY," +
                "$WALLET_TABLE_ID_PUBLIC_KEY BLOB," +
                "$WALLET_TABLE_TYPE INTEGER," +
                "$WALLET_TABLE_VERSION TEXT," +
                "$WALLET_TABLE_LABEL BLOB" +
                ");")

        val walletIndexPrefix = "idx_$WALLET_TABLE_NAME"
        db.execSQL("CREATE UNIQUE INDEX ${walletIndexPrefix}_${WALLET_TABLE_ID_COLUMN} ON $WALLET_TABLE_NAME($WALLET_TABLE_ID_COLUMN);")
        db.execSQL("CREATE INDEX ${walletIndexPrefix}_${WALLET_TABLE_TYPE} ON $WALLET_TABLE_NAME($WALLET_TABLE_TYPE);")
        db.execSQL("CREATE INDEX ${walletIndexPrefix}_${WALLET_TABLE_VERSION} ON $WALLET_TABLE_NAME($WALLET_TABLE_VERSION);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

    }

    suspend fun clearAccounts() = withContext(scope.coroutineContext) {
        writableDatabase.delete(WALLET_TABLE_NAME, null, null)
    }

    suspend fun deleteAccount(id: String) = withContext(scope.coroutineContext) {
        val count = writableDatabase.delete(WALLET_TABLE_NAME, "$WALLET_TABLE_ID_COLUMN = ?", arrayOf(id))
        if (count == 0) {
            throw IllegalStateException("Account with id $id not found")
        }
    }

    suspend fun editAccount(id: String, label: WalletLabel) = withContext(scope.coroutineContext) {
        val values = ContentValues()
        values.put(WALLET_TABLE_LABEL, label.toByteArray())
        val count = writableDatabase.update(WALLET_TABLE_NAME, values, "$WALLET_TABLE_ID_COLUMN = ?", arrayOf(id))
        if (count == 0) {
            throw IllegalStateException("Account with id $id not found")
        }
    }

    suspend fun insertAccounts(wallets: List<WalletEntity>) = withContext(scope.coroutineContext) {
        writableDatabase.withTransaction {
            for (wallet in wallets) {
                writableDatabase.insertOrThrow(WALLET_TABLE_NAME, null, wallet.toValues())
            }
        }
    }

    suspend fun getAccounts(): List<WalletEntity> = withContext(scope.coroutineContext) {
        val query = "SELECT $WALLET_TABLE_ID_COLUMN, $WALLET_TABLE_ID_PUBLIC_KEY, $WALLET_TABLE_TYPE, $WALLET_TABLE_VERSION, $WALLET_TABLE_LABEL FROM $WALLET_TABLE_NAME LIMIT 100;"
        val cursor = readableDatabase.rawQuery(query, null)
        readAccounts(cursor)
    }

    suspend fun getAccount(id: String): WalletEntity? = withContext(scope.coroutineContext) {
        if (id.isNotBlank()) {
            val query = "SELECT $WALLET_TABLE_ID_COLUMN, $WALLET_TABLE_ID_PUBLIC_KEY, $WALLET_TABLE_TYPE, $WALLET_TABLE_VERSION, $WALLET_TABLE_LABEL FROM $WALLET_TABLE_NAME WHERE $WALLET_TABLE_ID_COLUMN = ?;"
            val cursor = readableDatabase.rawQuery(query, arrayOf(id))
            readAccounts(cursor).firstOrNull()
        } else {
            null
        }
    }

    suspend fun getFirstAccountId(): String? = withContext(scope.coroutineContext) {
        val query = "SELECT $WALLET_TABLE_ID_COLUMN FROM $WALLET_TABLE_NAME LIMIT 1;"
        val cursor = readableDatabase.rawQuery(query, null)
        val idIndex = cursor.getColumnIndex(WALLET_TABLE_ID_COLUMN)
        val id = if (cursor.moveToFirst()) {
            cursor.getString(idIndex)
        } else {
            null
        }
        cursor.close()
        id
    }

    private fun readAccounts(cursor: Cursor): List<WalletEntity> {
        val idIndex = cursor.getColumnIndex(WALLET_TABLE_ID_COLUMN)
        val publicKeyIndex = cursor.getColumnIndex(WALLET_TABLE_ID_PUBLIC_KEY)
        val typeIndex = cursor.getColumnIndex(WALLET_TABLE_TYPE)
        val versionIndex = cursor.getColumnIndex(WALLET_TABLE_VERSION)
        val labelIndex = cursor.getColumnIndex(WALLET_TABLE_LABEL)
        val accounts = mutableListOf<WalletEntity>()
        while (cursor.moveToNext()) {
            val wallet = WalletEntity(
                id = cursor.getString(idIndex),
                publicKey = PublicKeyEd25519(cursor.getBlob(publicKeyIndex)),
                type = walletType(cursor.getInt(typeIndex)),
                version = walletVersion(cursor.getInt(versionIndex)),
                label = cursor.getBlob(labelIndex).toParcel<WalletLabel>()!!
            )
            accounts.add(wallet)
        }
        cursor.close()
        return accounts
    }

}