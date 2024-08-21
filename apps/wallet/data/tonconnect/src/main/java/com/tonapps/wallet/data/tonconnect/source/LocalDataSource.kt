package com.tonapps.wallet.data.tonconnect.source

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.tonapps.extensions.toByteArray
import com.tonapps.extensions.toParcel
import com.tonapps.sqlite.SQLiteHelper
import com.tonapps.sqlite.query
import com.tonapps.wallet.data.tonconnect.entities.DConnectEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppManifestEntity

internal class LocalDataSource(context: Context): SQLiteHelper(context, "tonconnect", 1) {

    private companion object {
        private const val MANIFEST_TABLE_NAME = "manifest"
        private const val MANIFEST_COLUMN_URL = "url"
        private const val MANIFEST_COLUMN_SOURCE_URL = "source_url"
        private const val MANIFEST_COLUMN_OBJECT = "object"

        private const val CONNECT_TABLE_NAME = "connect"
        private const val CONNECT_COLUMN_OBJECT = "object"
        private const val CONNECT_COLUMN_URL = "url"
        private const val CONNECT_COLUMN_WALLET_ID = "walletId"

        private const val APP_TABLE_NAME = "app"
        private const val APP_COLUMN_URL = "url"
        private const val APP_COLUMN_WALLET_ID = "walletId"
    }

    override fun create(db: SQLiteDatabase) {
        createManifestTable(db)
        createConnectTable(db)
        createAppTable(db)
    }

    private fun createManifestTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $MANIFEST_TABLE_NAME (" +
                "$MANIFEST_COLUMN_URL TEXT," +
                "$MANIFEST_COLUMN_SOURCE_URL TEXT," +
                "$MANIFEST_COLUMN_OBJECT BLOB" +
                ");")
        db.execSQL("CREATE INDEX idx_url ON $MANIFEST_TABLE_NAME($MANIFEST_COLUMN_URL);")
        db.execSQL("CREATE INDEX idx_sourceUrl ON $MANIFEST_TABLE_NAME($MANIFEST_COLUMN_SOURCE_URL);")
    }

    private fun createConnectTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $CONNECT_TABLE_NAME (" +
                "$CONNECT_COLUMN_WALLET_ID TEXT," +
                "$CONNECT_COLUMN_OBJECT BLOB," +
                "$CONNECT_COLUMN_URL TEXT" +
                ");")
    }

    private fun createAppTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $APP_TABLE_NAME (" +
                "$APP_COLUMN_WALLET_ID TEXT," +
                "$APP_COLUMN_URL TEXT" +
                ");")
        db.execSQL("CREATE INDEX idx_walletId ON $APP_TABLE_NAME($APP_COLUMN_WALLET_ID);")
        db.execSQL("CREATE INDEX idx_url ON $APP_TABLE_NAME($APP_COLUMN_URL);")
    }

    fun getManifest(vararg urls: String): DAppManifestEntity? {
        val placeholders = urls.joinToString(",") { "?" }
        val query = """
            SELECT $MANIFEST_COLUMN_OBJECT 
            FROM $MANIFEST_TABLE_NAME 
            WHERE $MANIFEST_COLUMN_URL IN ($placeholders) 
               OR $MANIFEST_COLUMN_SOURCE_URL IN ($placeholders) 
            LIMIT 1;
        """
        val sanitizedUrls = urls.map { it.removeSuffix("/") }.toTypedArray()
        val bytes = readableDatabase.query(query, sanitizedUrls) ?: return null
        return bytes.toParcel<DAppManifestEntity>()
    }

    fun setManifest(sourceUrl: String, manifest: DAppManifestEntity) {
        val value = ContentValues()
        value.put(MANIFEST_COLUMN_URL, manifest.url)
        value.put(MANIFEST_COLUMN_SOURCE_URL, sourceUrl.removeSuffix("/"))
        value.put(MANIFEST_COLUMN_OBJECT, manifest.toByteArray())
        try {
            writableDatabase.insertOrThrow(MANIFEST_TABLE_NAME, null, value)
        } catch (ignored: Exception) {}
    }

    fun addConnect(connect: DConnectEntity) {
        deleteConnect(connect.url, connect.walletId)

        val value = ContentValues()
        value.put(CONNECT_COLUMN_OBJECT, connect.toByteArray())
        value.put(CONNECT_COLUMN_URL, connect.url.removeSuffix("/"))
        value.put(CONNECT_COLUMN_WALLET_ID, connect.walletId)
        writableDatabase.insertOrThrow(CONNECT_TABLE_NAME, null, value)
    }

    fun updateConnect(connect: DConnectEntity) {
        addConnect(connect)
    }

    fun getConnect(walletId: String, url: String): DConnectEntity? {
        val query = "SELECT $CONNECT_COLUMN_OBJECT FROM $CONNECT_TABLE_NAME WHERE $CONNECT_COLUMN_URL = ? AND $CONNECT_COLUMN_WALLET_ID = ? LIMIT 1;"
        val bytes = readableDatabase.query(query, arrayOf(url.removeSuffix("/"), walletId)) ?: return null
        return bytes.toParcel<DConnectEntity>()
    }

    fun getConnections(): List<DConnectEntity> {
        val query = "SELECT $CONNECT_COLUMN_OBJECT FROM $CONNECT_TABLE_NAME;"
        val cursor = readableDatabase.rawQuery(query, null)
        val list = mutableListOf<DConnectEntity>()
        while (cursor.moveToNext()) {
            val app = cursor.getBlob(0).toParcel<DConnectEntity>() ?: continue
            list.add(app)
        }
        cursor.close()
        return uniqueApps(list)
    }

    fun deleteConnect(walletId: String, url: String) {
        writableDatabase.delete(
            CONNECT_TABLE_NAME,
            "$CONNECT_COLUMN_URL = ? AND $CONNECT_COLUMN_WALLET_ID = ?",
            arrayOf(url.removeSuffix("/"), walletId)
        )
    }

    private fun uniqueApps(list: List<DConnectEntity>): List<DConnectEntity> {
        return list.distinctBy { it.uniqueId }
    }

    fun clearConnections() {
        writableDatabase.delete(CONNECT_TABLE_NAME, null, null)
    }
}