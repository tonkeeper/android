package com.tonapps.signer.core.source

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.blockchain.ton.extensions.publicKeyFromHex
import com.tonapps.signer.core.entities.KeyEntity
import com.tonapps.signer.extensions.emptyRawQuery
import com.tonapps.signer.extensions.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import org.ton.api.pub.PublicKeyEd25519

class SQLSource(
    private val context: Context
): SQLiteOpenHelper(context, DATABASE_NAME, null, 1) {

    private companion object {
        private const val DATABASE_NAME = "signer"

        private const val TON_KEYS_TABLE_NAME = "ton_keys"

        private const val TON_KEYS_NAME_COLUMN = "ton_key_name"
        private const val TON_KEYS_PK_COLUMN = "ton_key_pk"
        private const val TON_KEYS_TIMESTAMP_COLUMN = "ton_key_timestamp"
        private const val TON_KEYS_ID_COLUMN = "ton_key_id"
    }

    private val coroutineContext = Dispatchers.IO + SupervisorJob()
    private val backup = BackupSource(context)

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // To prevent data from being lost
        db.execSQL("PRAGMA synchronous = EXTRA")
        db.execSQL("PRAGMA temp_store = FILE")
        db.emptyRawQuery("PRAGMA journal_mode = DELETE")
        db.emptyRawQuery("PRAGMA secure_delete = TRUE")
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $TON_KEYS_TABLE_NAME ($TON_KEYS_ID_COLUMN INTEGER PRIMARY KEY AUTOINCREMENT, $TON_KEYS_NAME_COLUMN TEXT, $TON_KEYS_PK_COLUMN TEXT, $TON_KEYS_TIMESTAMP_COLUMN INTEGER);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { }

    suspend fun deleteAll() = withContext(coroutineContext) {
        context.deleteDatabase(DATABASE_NAME)
        backup.deleteAll()
    }

    suspend fun deleteKey(id: Long) = withContext(coroutineContext) {
        writableDatabase.delete(TON_KEYS_TABLE_NAME, "$TON_KEYS_ID_COLUMN = ?", arrayOf(id.toString()))
        backup.delete(id)
    }

    suspend fun findIdByPublicKey(publicKey: PublicKeyEd25519): Long? = withContext(coroutineContext) {
        getEntities().find { it.publicKey == publicKey }?.id
    }

    suspend fun setName(id: Long, name: String) = withContext(coroutineContext) {
        val values = ContentValues()
        values.put(TON_KEYS_NAME_COLUMN, name)
        writableDatabase.update(TON_KEYS_TABLE_NAME, values, "$TON_KEYS_ID_COLUMN = ?", arrayOf(id.toString()))
    }

    suspend fun get(id: Long): KeyEntity? = withContext(coroutineContext) {
        val cursor = readableDatabase.query(TON_KEYS_TABLE_NAME, null, "$TON_KEYS_ID_COLUMN = ?", arrayOf(id.toString()), null, null, null)
        val result = cursor?.let { readCursor(it) } ?: emptyList()
        result.firstOrNull()
    }

    suspend fun getEntities(): List<KeyEntity> = withContext(coroutineContext) {
        val cursor = readableDatabase.query(TON_KEYS_TABLE_NAME, null, null, null, null, null, null)
        val list = cursor?.let { readCursor(it) } ?: emptyList()
        if (list.isNotEmpty()) {
            return@withContext list
        }
        val keys = backup.get() ?: return@withContext emptyList()
        restoreFromBackup(keys.keys)
        keys.keys
    }

    private suspend fun restoreFromBackup(keys: List<KeyEntity>) = withContext(coroutineContext) {
        keys.forEach { add(it.name, it.publicKey) }
    }

    private fun readCursor(cursor: Cursor): List<KeyEntity> {
        val result = mutableListOf<KeyEntity>()
        if (cursor.count > 0) {
            cursor.moveToFirst()
            val idIndex = cursor.getColumnIndex(TON_KEYS_ID_COLUMN)
            val nameIndex = cursor.getColumnIndex(TON_KEYS_NAME_COLUMN)
            val pkIndex = cursor.getColumnIndex(TON_KEYS_PK_COLUMN)
            do {
                val id = cursor.getLong(idIndex)
                val name = cursor.getString(nameIndex)
                val pk = cursor.getString(pkIndex)
                result.add(KeyEntity(id, name, pk.publicKeyFromHex()))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return result
    }

    suspend fun add(
        name: String,
        publicKey: PublicKeyEd25519
    ): Long = withContext(coroutineContext) {
        val id = writableDatabase.withTransaction {
            val values = ContentValues()
            values.put(TON_KEYS_NAME_COLUMN, name)
            values.put(TON_KEYS_PK_COLUMN, publicKey.hex())
            values.put(TON_KEYS_TIMESTAMP_COLUMN, System.currentTimeMillis())
            insertOrThrow(TON_KEYS_TABLE_NAME, null, values)
        }
        if (id == -1L) {
            throw IllegalStateException("Can't insert key")
        }
        backup.add(KeyEntity(id, name, publicKey))
        id
    }

}