package com.tonapps.wallet.data.dapps.source

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import com.tonapps.extensions.getParcelable
import com.tonapps.extensions.prefs
import com.tonapps.extensions.putLong
import com.tonapps.extensions.putParcelable
import com.tonapps.extensions.putString
import com.tonapps.extensions.remove
import com.tonapps.extensions.withoutQuery
import com.tonapps.security.CryptoBox
import com.tonapps.security.Security
import com.tonapps.sqlite.SQLiteHelper
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

internal class DatabaseSource(
    context: Context
): SQLiteHelper(context, DATABASE_NAME, DATABASE_VERSION) {

    private companion object {
        private const val DATABASE_NAME = "dapps"
        private const val DATABASE_VERSION = 1

        private const val KEY_ALIAS = "_com_tonapps_dapps_master_key_"

        private const val LAST_EVENT_ID_KEY = "last_event_id"
        private const val LAST_APP_REQUEST_ID_PREFIX = "last_app_request_id_"

        private const val APP_TABLE_NAME = "app"
        private const val APP_TABLE_URL_COLUMN = "url"
        private const val APP_TABLE_NAME_COLUMN = "name"
        private const val APP_TABLE_ICON_URL_COLUMN = "icon_url"

        private const val CONNECT_TABLE_NAME = "connect"
        private const val CONNECT_TABLE_APP_URL_COLUMN = "app_url"
        private const val CONNECT_TABLE_ACCOUNT_ID_COLUMN = "account_id"
        private const val CONNECT_TABLE_TESTNET_COLUMN = "testnet"
        private const val CONNECT_TABLE_CLIENT_ID_COLUMN = "client_id"
        private const val CONNECT_TABLE_TYPE_COLUMN = "type"
        private const val CONNECT_TABLE_TIMESTAMP_COLUMN = "timestamp"

        private val appFields = arrayOf(
            APP_TABLE_URL_COLUMN,
            APP_TABLE_NAME_COLUMN,
            APP_TABLE_ICON_URL_COLUMN
        ).joinToString(",")

        private val connectFields = arrayOf(
            CONNECT_TABLE_APP_URL_COLUMN,
            CONNECT_TABLE_ACCOUNT_ID_COLUMN,
            CONNECT_TABLE_TESTNET_COLUMN,
            CONNECT_TABLE_CLIENT_ID_COLUMN,
            CONNECT_TABLE_TYPE_COLUMN,
            CONNECT_TABLE_TIMESTAMP_COLUMN
        ).joinToString(",")
    }

    private val coroutineContext: CoroutineContext = Dispatchers.IO.limitedParallelism(1)

    private val encryptedPrefs = Security.pref(context, KEY_ALIAS, DATABASE_NAME)
    private val prefs = context.prefs("tonconnect")

    private fun createAppTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $APP_TABLE_NAME (" +
                "$APP_TABLE_URL_COLUMN TEXT PRIMARY KEY," +
                "$APP_TABLE_NAME_COLUMN TEXT," +
                "$APP_TABLE_ICON_URL_COLUMN TEXT" +
                ")")

        val appIndexPrefix = "idx_$APP_TABLE_NAME"
        db.execSQL("CREATE UNIQUE INDEX ${appIndexPrefix}_url ON $APP_TABLE_NAME ($APP_TABLE_URL_COLUMN)")
    }

    private fun createConnectTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $CONNECT_TABLE_NAME (" +
                "$CONNECT_TABLE_CLIENT_ID_COLUMN TEXT PRIMARY KEY," +
                "$CONNECT_TABLE_ACCOUNT_ID_COLUMN TEXT," +
                "$CONNECT_TABLE_TESTNET_COLUMN INTEGER," +
                "$CONNECT_TABLE_TYPE_COLUMN INTEGER," +
                "$CONNECT_TABLE_APP_URL_COLUMN TEXT," +
                "$CONNECT_TABLE_TIMESTAMP_COLUMN INTEGER" +
                ")")

        val connectIndexPrefix = "idx_$CONNECT_TABLE_NAME"
        db.execSQL("CREATE UNIQUE INDEX ${connectIndexPrefix}_client_id ON $CONNECT_TABLE_NAME ($CONNECT_TABLE_CLIENT_ID_COLUMN)")
        db.execSQL("CREATE INDEX ${connectIndexPrefix}_account_id_testnet ON $CONNECT_TABLE_NAME ($CONNECT_TABLE_ACCOUNT_ID_COLUMN, $CONNECT_TABLE_TESTNET_COLUMN)")
        db.execSQL("CREATE INDEX ${connectIndexPrefix}_app_url ON $CONNECT_TABLE_NAME ($CONNECT_TABLE_TYPE_COLUMN, $CONNECT_TABLE_APP_URL_COLUMN)")
    }

    override fun create(db: SQLiteDatabase) {
        createAppTable(db)
        createConnectTable(db)
    }

    suspend fun getApps(urls: List<Uri>): List<AppEntity> = withContext(coroutineContext) {
        val placeholders = urls.joinToString(",") { "?" }
        val query = "SELECT $appFields FROM $APP_TABLE_NAME WHERE $APP_TABLE_URL_COLUMN IN ($placeholders)"
        val cursor = readableDatabase.rawQuery(query, urls.map { it.withoutQuery.toString() }.toTypedArray())
        val urlIndex = cursor.getColumnIndex(APP_TABLE_URL_COLUMN)
        val nameIndex = cursor.getColumnIndex(APP_TABLE_NAME_COLUMN)
        val iconUrlIndex = cursor.getColumnIndex(APP_TABLE_ICON_URL_COLUMN)
        val apps = mutableListOf<AppEntity>()
        while (cursor.moveToNext()) {
            apps.add(AppEntity(
                url = Uri.parse(cursor.getString(urlIndex).removeSuffix("/")),
                name = cursor.getString(nameIndex),
                iconUrl = cursor.getString(iconUrlIndex),
                empty = false
            ))
        }
        cursor.close()
        val notFoundApps = urls.filter { url -> apps.none { it.url == url } }
        if (notFoundApps.isNotEmpty()) {
            for (url in notFoundApps) {
                val domain = url.host ?: "unknown"
                apps.add(AppEntity(
                    url = url,
                    name = domain.split(".").firstOrNull() ?: domain,
                    iconUrl = "https://$domain/favicon.ico",
                    empty = true
                ))
            }
        }
        apps.toList()
    }

    suspend fun getApp(url: Uri): AppEntity? = withContext(coroutineContext) {
        getApps(listOf(url)).firstOrNull()
    }

    suspend fun insertApp(appEntity: AppEntity): Boolean = withContext(coroutineContext) {
        try {
            val values = ContentValues()
            values.put(APP_TABLE_URL_COLUMN, appEntity.url.withoutQuery.toString().removeSuffix("/"))
            values.put(APP_TABLE_NAME_COLUMN, appEntity.name)
            values.put(APP_TABLE_ICON_URL_COLUMN, appEntity.iconUrl)
            writableDatabase.insertWithOnConflict(APP_TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            prefs.remove(LAST_EVENT_ID_KEY)
            true
        } catch (e: Throwable) {
            false
        }
    }

    suspend fun insertConnection(connection: AppConnectEntity) = withContext(coroutineContext) {
        val values = ContentValues()
        values.put(CONNECT_TABLE_APP_URL_COLUMN, connection.appUrl.withoutQuery.toString().removeSuffix("/"))
        values.put(CONNECT_TABLE_ACCOUNT_ID_COLUMN, connection.accountId)
        values.put(CONNECT_TABLE_TESTNET_COLUMN, if (connection.testnet) 1 else 0)
        values.put(CONNECT_TABLE_CLIENT_ID_COLUMN, connection.clientId)
        values.put(CONNECT_TABLE_TYPE_COLUMN, connection.type.value)
        values.put(CONNECT_TABLE_TIMESTAMP_COLUMN, connection.timestamp)
        writableDatabase.insertOrThrow(CONNECT_TABLE_NAME, null, values)

        val prefix = prefixAccount(connection.accountId, connection.testnet)
        encryptedPrefs.putParcelable(prefixKeyPair(prefix, connection.clientId), connection.keyPair)
        if (connection.proofSignature != null) {
            encryptedPrefs.putString(prefixProofSignature(prefix, connection.appUrl), connection.proofSignature)
        }
        if (connection.proofPayload != null) {
            encryptedPrefs.putString(prefixProofPayload(prefix, connection.appUrl), connection.proofPayload)
        }
    }

    suspend fun deleteConnect(connection: AppConnectEntity): Boolean = withContext(coroutineContext) {
        val count = writableDatabase.delete(CONNECT_TABLE_NAME, "$CONNECT_TABLE_CLIENT_ID_COLUMN = ?", arrayOf(connection.clientId))
        encryptedPrefs.edit {
            val prefix = prefixAccount(connection.accountId, connection.testnet)
            remove(prefixKeyPair(prefix, connection.clientId))
            remove(prefixProofSignature(prefix, connection.appUrl))
            remove(prefixProofPayload(prefix, connection.appUrl))
        }
        count > 0
    }

    suspend fun getConnections(): List<AppConnectEntity> = withContext(coroutineContext) {
        val query = "SELECT $connectFields FROM $CONNECT_TABLE_NAME"
        val cursor = readableDatabase.rawQuery(query, null)
        val list = readConnections(cursor)
        cursor.close()
        list
    }

    private fun readConnections(cursor: Cursor): List<AppConnectEntity> {
        val appUrlIndex = cursor.getColumnIndex(CONNECT_TABLE_APP_URL_COLUMN)
        val accountIdIndex = cursor.getColumnIndex(CONNECT_TABLE_ACCOUNT_ID_COLUMN)
        val testnetIndex = cursor.getColumnIndex(CONNECT_TABLE_TESTNET_COLUMN)
        val clientIdIndex = cursor.getColumnIndex(CONNECT_TABLE_CLIENT_ID_COLUMN)
        val typeIndex = cursor.getColumnIndex(CONNECT_TABLE_TYPE_COLUMN)
        val timestampIndex = cursor.getColumnIndex(CONNECT_TABLE_TIMESTAMP_COLUMN)

        val connections = mutableListOf<AppConnectEntity>()
        while (cursor.moveToNext()) {
            val accountId = cursor.getString(accountIdIndex)
            val testnet = cursor.getInt(testnetIndex) == 1
            val clientId = cursor.getString(clientIdIndex)
            val appUrl = Uri.parse(cursor.getString(appUrlIndex)).withoutQuery
            val prefix = prefixAccount(accountId, testnet)
            val keyPair = encryptedPrefs.getParcelable<CryptoBox.KeyPair>(prefixKeyPair(prefix, clientId)) ?: continue

            connections.add(AppConnectEntity(
                appUrl = appUrl,
                accountId = accountId,
                testnet = testnet,
                clientId = cursor.getString(clientIdIndex),
                type = AppConnectEntity.Type.entries.first { it.value == cursor.getInt(typeIndex) },
                keyPair = keyPair,
                proofSignature = encryptedPrefs.getString(prefixProofSignature(prefix, appUrl), null),
                proofPayload = encryptedPrefs.getString(prefixProofPayload(prefix, appUrl), null),
                timestamp = cursor.getLong(timestampIndex),
                pushEnabled = isPushEnabled(accountId, testnet, appUrl)
            ))
        }
        return connections
    }

    private fun prefixKeyPair(
        prefix: String,
        clientId: String
    ): String {
        return "key_pair_${prefix}_${clientId}"
    }

    private fun prefixProofSignature(
        prefix: String,
        appUrl: Uri
    ): String {
        return "proof_signature_${prefix}_${appUrl}"
    }

    private fun prefixProofPayload(
        prefix: String,
        appUrl: Uri
    ): String {
        return "proof_payload_${prefix}_${appUrl}"
    }

    private fun prefixPush(
        prefix: String,
        appUrl: Uri
    ): String {
        return "push_${prefix}_${appUrl}"
    }

    private fun prefixAccount(
        accountId: String,
        testnet: Boolean
    ): String {
        return "account_${accountId}:${if (testnet) "1" else "0"}"
    }

    internal fun getLastEventId(): Long {
        return prefs.getLong(LAST_EVENT_ID_KEY, 0)
    }

    internal fun setLastEventId(id: Long) {
        if (id > getLastEventId()) {
            prefs.putLong(LAST_EVENT_ID_KEY, id)
        }
    }

    internal fun getLastAppRequestId(clientId: String): Long {
        return prefs.getLong(LAST_APP_REQUEST_ID_PREFIX + clientId, -1)
    }

    internal fun setLastAppRequestId(clientId: String, id: Long) {
        if (id > getLastAppRequestId(clientId)) {
            prefs.putLong(LAST_APP_REQUEST_ID_PREFIX + clientId, id)
        }
    }

    internal fun isPushEnabled(accountId: String, testnet: Boolean, appUrl: Uri): Boolean {
        return prefs.getBoolean(prefixPush(prefixAccount(accountId, testnet), appUrl), false)
    }

    internal fun setPushEnabled(accountId: String, testnet: Boolean, appUrl: Uri, enabled: Boolean) {
        prefs.edit {
            putBoolean(prefixPush(prefixAccount(accountId, testnet), appUrl), enabled)
        }
    }

    override fun close() {
        super.close()
        coroutineContext.cancel()
    }

    suspend fun clearConnections() = withContext(coroutineContext) {
        writableDatabase.delete(CONNECT_TABLE_NAME, null, null)
    }

}