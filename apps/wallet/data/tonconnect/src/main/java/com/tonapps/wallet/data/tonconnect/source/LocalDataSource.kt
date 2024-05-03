package com.tonapps.wallet.data.tonconnect.source

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.tonapps.extensions.toByteArray
import com.tonapps.extensions.toParcel
import com.tonapps.sqlite.SQLiteHelper
import com.tonapps.sqlite.query
import com.tonapps.wallet.data.core.accountId
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppManifestEntity

internal class LocalDataSource(context: Context): SQLiteHelper(context, "tonconnect", 2) {

    private companion object {
        private const val MANIFEST_TABLE_NAME = "manifest"
        private const val MANIFEST_COLUMN_URL = "url"
        private const val MANIFEST_COLUMN_OBJECT = "object"

        private const val APP_TABLE_NAME = "app"
        private const val APP_COLUMN_CLIENT_ID = "client_id"
        private const val APP_COLUMN_OBJECT = "object"
        private const val APP_COLUMN_ACCOUNT_ID = "account_id"
        private const val APP_COLUMN_URL = "url"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $MANIFEST_TABLE_NAME (" +
                "$MANIFEST_COLUMN_URL TEXT UNIQUE," +
                "$MANIFEST_COLUMN_OBJECT BLOB" +
                ");")
        createAppTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        super.onUpgrade(db, oldVersion, newVersion)
        if (oldVersion == 1 && newVersion == 2) {
            createAppTable(db)
        }
    }

    private fun createAppTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $APP_TABLE_NAME (" +
                "$APP_COLUMN_CLIENT_ID TEXT UNIQUE," +
                "$APP_COLUMN_ACCOUNT_ID TEXT," +
                "$APP_COLUMN_OBJECT BLOB," +
                "$APP_COLUMN_URL TEXT" +
                ");")
        db.execSQL("CREATE INDEX idx_accountId ON $APP_TABLE_NAME($APP_COLUMN_ACCOUNT_ID);")
        db.execSQL("CREATE INDEX idx_url ON $APP_TABLE_NAME($APP_COLUMN_URL);")
    }

    fun getManifest(url: String): DAppManifestEntity? {
        val query = "SELECT $MANIFEST_COLUMN_OBJECT FROM $MANIFEST_TABLE_NAME WHERE $MANIFEST_COLUMN_URL = ? LIMIT 1;"
        val bytes = readableDatabase.query(query, arrayOf(url)) ?: return null
        return bytes.toParcel<DAppManifestEntity>()
    }

    fun setManifest(url: String, manifest: DAppManifestEntity) {
        val value = ContentValues()
        value.put(MANIFEST_COLUMN_URL, url)
        value.put(MANIFEST_COLUMN_OBJECT, manifest.toByteArray())
        writableDatabase.insert(MANIFEST_TABLE_NAME, null, value)
    }

    fun getApp(
        clientId: String,
        accountId: String,
        testnet: Boolean
    ): DAppEntity? {
        val query = "SELECT $APP_COLUMN_OBJECT FROM $APP_TABLE_NAME WHERE $APP_COLUMN_CLIENT_ID = ? AND $APP_COLUMN_ACCOUNT_ID = ? LIMIT 1;"
        val bytes = readableDatabase.query(query, arrayOf(clientId, accountId(accountId, testnet))) ?: return null
        return bytes.toParcel<DAppEntity>()
    }

    fun addApp(app: DAppEntity) {
        writableDatabase.delete(
            APP_TABLE_NAME,
            "$APP_COLUMN_URL = ? AND $APP_COLUMN_ACCOUNT_ID = ?",
            arrayOf(app.url, app.accountId)
        )

        val value = ContentValues()
        value.put(APP_COLUMN_CLIENT_ID, app.clientId)
        value.put(APP_COLUMN_ACCOUNT_ID, accountId(app.accountId, app.testnet))
        value.put(APP_COLUMN_OBJECT, app.toByteArray())
        value.put(APP_COLUMN_URL, app.url)
        writableDatabase.insert(APP_TABLE_NAME, null, value)
    }

    fun getApps(): List<DAppEntity> {
        val query = "SELECT $APP_COLUMN_OBJECT FROM $APP_TABLE_NAME;"
        val cursor = readableDatabase.rawQuery(query, null)
        val list = mutableListOf<DAppEntity>()
        while (cursor.moveToNext()) {
            val app = cursor.getBlob(0).toParcel<DAppEntity>() ?: continue
            list.add(app)
        }
        cursor.close()
        return uniqueApps(list)
    }

    fun getApps(accountId: String, testnet: Boolean): List<DAppEntity> {
        val query = "SELECT $APP_COLUMN_OBJECT FROM $APP_TABLE_NAME WHERE $APP_COLUMN_ACCOUNT_ID = ?;"
        val cursor = readableDatabase.rawQuery(query, arrayOf(accountId(accountId, testnet)))
        val list = mutableListOf<DAppEntity>()
        while (cursor.moveToNext()) {
            val app = cursor.getBlob(0).toParcel<DAppEntity>() ?: continue
            list.add(app)
        }
        cursor.close()
        return uniqueApps(list)
    }

    fun deleteApp(clientId: String, accountId: String, testnet: Boolean) {
        val s = writableDatabase.delete(
            APP_TABLE_NAME,
            "$APP_COLUMN_CLIENT_ID = ? AND $APP_COLUMN_ACCOUNT_ID = ?",
            arrayOf(clientId, accountId(accountId, testnet))
        )
    }

    private fun uniqueApps(list: List<DAppEntity>): List<DAppEntity> {
        return list.distinctBy { it.uniqueId }
    }
}