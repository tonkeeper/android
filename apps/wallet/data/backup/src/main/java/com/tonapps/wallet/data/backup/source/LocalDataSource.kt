package com.tonapps.wallet.data.backup.source

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.tonapps.sqlite.SQLiteHelper
import com.tonapps.wallet.data.backup.entities.BackupEntity

internal class LocalDataSource(context: Context): SQLiteHelper(context, "backup", 1) {

    private companion object {
        private const val BACKUP_TABLE_NAME = "backup"
        private const val BACKUP_COLUMN_ID = "_id"
        private const val BACKUP_COLUMN_DATE = "date"
        private const val BACKUP_COLUMN_WALLET_ID = "wallet_id"
        private const val BACKUP_COLUMN_SOURCE = "source"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $BACKUP_TABLE_NAME (" +
                "$BACKUP_COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                "$BACKUP_COLUMN_DATE INTEGER, " +
                "$BACKUP_COLUMN_WALLET_ID TEXT," +
                "$BACKUP_COLUMN_SOURCE TEXT NOT NULL" +
                ");")
        db.execSQL("CREATE INDEX idx_walletId ON $BACKUP_TABLE_NAME($BACKUP_COLUMN_WALLET_ID);")
    }

    fun getAllBackups(): List<BackupEntity> {
        val query = "SELECT $BACKUP_COLUMN_ID,$BACKUP_COLUMN_SOURCE,$BACKUP_COLUMN_WALLET_ID,$BACKUP_COLUMN_DATE FROM $BACKUP_TABLE_NAME;"
        return getBackups(query)
    }

    fun getBackups(walletId: Long): List<BackupEntity> {
        val query = "SELECT $BACKUP_COLUMN_ID,$BACKUP_COLUMN_SOURCE,$BACKUP_COLUMN_WALLET_ID,$BACKUP_COLUMN_DATE FROM $BACKUP_TABLE_NAME WHERE $BACKUP_COLUMN_WALLET_ID = ?;"
        return getBackups(query, arrayOf(walletId.toString()))
    }

    fun getBackup(backupId: Long): BackupEntity? {
        val query = "SELECT $BACKUP_COLUMN_ID,$BACKUP_COLUMN_SOURCE,$BACKUP_COLUMN_WALLET_ID,$BACKUP_COLUMN_DATE FROM $BACKUP_TABLE_NAME WHERE $BACKUP_COLUMN_ID = ?;"
        return getBackups(query, arrayOf(backupId.toString())).firstOrNull()
    }

    private fun getBackups(query: String, selectionArgs: Array<String>? = null): List<BackupEntity> {
        val cursor = readableDatabase.rawQuery(query, selectionArgs)
        val backups = mutableListOf<BackupEntity>()
        val idIndex = cursor.getColumnIndex(BACKUP_COLUMN_ID)
        val sourceIndex = cursor.getColumnIndex(BACKUP_COLUMN_SOURCE)
        val dateIndex = cursor.getColumnIndex(BACKUP_COLUMN_DATE)
        val walletIdIndex = cursor.getColumnIndex(BACKUP_COLUMN_WALLET_ID)
        while (cursor.moveToNext()) {
            backups.add(BackupEntity(
                id = cursor.getLong(idIndex),
                source = BackupEntity.Source.valueOf(cursor.getString(sourceIndex)),
                walletId = cursor.getString(walletIdIndex),
                date = cursor.getLong(dateIndex)
            ))
        }
        cursor.close()
        return backups.toList()
    }

    fun updateBackup(backupId: Long): BackupEntity? {
        val values = ContentValues()
        values.put(BACKUP_COLUMN_DATE, System.currentTimeMillis())
        writableDatabase.update(BACKUP_TABLE_NAME, values, "$BACKUP_COLUMN_ID = ?", arrayOf(backupId.toString()))
        return getBackup(backupId)
    }

    fun addBackup(
        walletId: String,
        source: BackupEntity.Source,
        date: Long = System.currentTimeMillis(),
    ): BackupEntity {
        val values = ContentValues()
        values.put(BACKUP_COLUMN_WALLET_ID, walletId)
        values.put(BACKUP_COLUMN_SOURCE, source.name)
        values.put(BACKUP_COLUMN_DATE, date)
        val id = writableDatabase.insertOrThrow(BACKUP_TABLE_NAME, null, values)
        return BackupEntity(
            id = id,
            walletId = walletId,
            source = source,
            date = date
        )
    }
}