package com.tonapps.signer.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.tonapps.signer.core.entities.KeyEntity
import com.tonapps.signer.extensions.emptyRawQuery
import com.tonapps.signer.extensions.withTransaction
import org.ton.api.pub.PublicKeyEd25519

class Database(
    private val context: Context
): SQLiteOpenHelper(context, NAME_DATABASE, null, 1) {

    private companion object {
        private const val NAME_DATABASE = "db"
        private const val KEYS_TABLE = "keys"
        private const val ID_COLUMN = "id"
        private const val NAME_COLUMN = "name"
        private const val PK_COLUMN = "pk"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
        db.setMaxSqlCacheSize(SQLiteDatabase.MAX_SQL_CACHE_SIZE)
        db.emptyRawQuery("PRAGMA temp_store = MEMORY")
        db.emptyRawQuery("PRAGMA secure_delete = TRUE")
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $KEYS_TABLE (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, pk BLOB);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

    }

    fun clearAll() {
        context.deleteDatabase(NAME_DATABASE)
    }

    fun setKeyName(id: Long, name: String) {
        val values = ContentValues()
        values.put(NAME_COLUMN, name)
        writableDatabase.update(KEYS_TABLE, values, "$ID_COLUMN = ?", arrayOf(id.toString()))
    }

    fun getKey(id: Long): KeyEntity? {
        var entity: KeyEntity? = null
        val cursor = readableDatabase.query(KEYS_TABLE, arrayOf(
            NAME_COLUMN,
            PK_COLUMN
        ), "$ID_COLUMN = ?", arrayOf(id.toString()), null, null, null)
        if (cursor != null && cursor.count > 0) {
            cursor.moveToFirst()
            val nameIndex = cursor.getColumnIndex(NAME_COLUMN)
            val pkIndex = cursor.getColumnIndex(PK_COLUMN)
            do {
                val name = cursor.getString(nameIndex)
                val pk = cursor.getBlob(pkIndex)
                entity = KeyEntity(id, name, PublicKeyEd25519(pk))
            } while (cursor.moveToNext())
        }
        cursor?.close()
        return entity
    }

    fun getAllKeys(): List<KeyEntity> {
        val result = mutableListOf<KeyEntity>()
        val cursor = readableDatabase.query(KEYS_TABLE, null, null, null, null, null, null)
        if (cursor != null && cursor.count > 0) {
            cursor.moveToFirst()
            val idIndex = cursor.getColumnIndex(ID_COLUMN)
            val nameIndex = cursor.getColumnIndex(NAME_COLUMN)
            val pkIndex = cursor.getColumnIndex(PK_COLUMN)
            do {
                val id = cursor.getLong(idIndex)
                val name = cursor.getString(nameIndex)
                val pk = cursor.getBlob(pkIndex)
                result.add(KeyEntity(id, name, PublicKeyEd25519(pk)))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return result
    }

    fun deleteKey(id: Long) {
        writableDatabase.delete(KEYS_TABLE, "$ID_COLUMN = ?", arrayOf(id.toString()))
    }

    fun insertKey(name: String, publicKey: PublicKeyEd25519): Long {
        return insertKey(name, publicKey.key.toByteArray())
    }

    private fun insertKey(name: String, publicKey: ByteArray): Long {
        val id = writableDatabase.withTransaction {
            val values = ContentValues()
            values.put(NAME_COLUMN, name)
            values.put(PK_COLUMN, publicKey)
            insert(KEYS_TABLE, null, values)
        }
        if (id == -1L) {
            throw IllegalStateException("Can't insert key")
        }
        return id
    }
}

