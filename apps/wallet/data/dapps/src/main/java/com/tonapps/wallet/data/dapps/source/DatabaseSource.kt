package com.tonapps.wallet.data.dapps.source

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.core.content.edit
import androidx.core.database.sqlite.transaction
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.extensions.prefs
import com.tonapps.extensions.putByteArray
import com.tonapps.extensions.putLong
import com.tonapps.extensions.putParcelable
import com.tonapps.extensions.remove
import com.tonapps.extensions.toByteArray
import com.tonapps.extensions.toParcel
import com.tonapps.extensions.withoutQuery
import com.tonapps.security.CryptoBox
import com.tonapps.security.Security
import com.tonapps.sqlite.SQLiteHelper
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity
import com.tonapps.wallet.data.dapps.entities.AppPushEntity
import com.tonapps.wallet.data.dapps.entities.ConnectionEncryptedEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

internal class DatabaseSource(
    context: Context
): SQLiteHelper(context, DATABASE_NAME, DATABASE_VERSION) {

    private val coroutineContext: CoroutineContext = Dispatchers.IO.limitedParallelism(1)

    private val encryptedPrefs by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { Security.pref(context, KEY_ALIAS, DATABASE_NAME) }
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
                "$CONNECT_TABLE_NETWORK_COLUMN INTEGER," +
                "$CONNECT_TABLE_TYPE_COLUMN INTEGER," +
                "$CONNECT_TABLE_APP_URL_COLUMN TEXT," +
                "$CONNECT_TABLE_TIMESTAMP_COLUMN INTEGER" +
                ")")

        val connectIndexPrefix = "idx_$CONNECT_TABLE_NAME"
        db.execSQL("CREATE UNIQUE INDEX ${connectIndexPrefix}_client_id ON $CONNECT_TABLE_NAME ($CONNECT_TABLE_CLIENT_ID_COLUMN)")
        db.execSQL("CREATE INDEX ${connectIndexPrefix}_account_id_network ON $CONNECT_TABLE_NAME ($CONNECT_TABLE_ACCOUNT_ID_COLUMN, $CONNECT_TABLE_NETWORK_COLUMN)")
        db.execSQL("CREATE INDEX ${connectIndexPrefix}_app_url ON $CONNECT_TABLE_NAME ($CONNECT_TABLE_TYPE_COLUMN, $CONNECT_TABLE_APP_URL_COLUMN)")
    }

    private fun createNotificationsTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $NOTIFICATIONS_TABLE_NAME (" +
                "$NOTIFICATIONS_TABLE_ID_COLUMN INTEGER PRIMARY KEY AUTOINCREMENT," +
                "$NOTIFICATIONS_TABLE_APP_URL_COLUMN TEXT," +
                "$NOTIFICATIONS_TABLE_ACCOUNT_ID_COLUMN TEXT," +
                "$NOTIFICATIONS_TABLE_BODY_COLUMN TEXT" +
                ")")

        val notificationsIndexPrefix = "idx_$NOTIFICATIONS_TABLE_NAME"
        db.execSQL("CREATE INDEX ${notificationsIndexPrefix}_app_url ON $NOTIFICATIONS_TABLE_NAME ($NOTIFICATIONS_TABLE_APP_URL_COLUMN)")
        db.execSQL("CREATE INDEX ${notificationsIndexPrefix}_account_id ON $NOTIFICATIONS_TABLE_NAME ($NOTIFICATIONS_TABLE_ACCOUNT_ID_COLUMN)")
    }

    override fun create(db: SQLiteDatabase) {
        createAppTable(db)
        createConnectTable(db)
        createNotificationsTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        super.onUpgrade(db, oldVersion, newVersion)
        if (oldVersion < 2) {
            createNotificationsTable(db)
        }
        if (oldVersion < 3) {
            db.beginTransaction()
            try {
                db.execSQL("ALTER TABLE $CONNECT_TABLE_NAME ADD COLUMN $CONNECT_TABLE_NETWORK_COLUMN INTEGER DEFAULT ${TonNetwork.MAINNET.value}")
                db.execSQL(
                    "UPDATE $CONNECT_TABLE_NAME SET $CONNECT_TABLE_NETWORK_COLUMN = " +
                    "CASE WHEN $CONNECT_TABLE_TESTNET_COLUMN = 1 THEN ${TonNetwork.TESTNET.value} " +
                    "ELSE ${TonNetwork.MAINNET.value} END"
                )
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    suspend fun insertNotifications(accountId: String, list: List<AppPushEntity.Body>) = withContext(coroutineContext) {
        writableDatabase.transaction {
            writableDatabase.delete(NOTIFICATIONS_TABLE_NAME, "$NOTIFICATIONS_TABLE_ACCOUNT_ID_COLUMN = ?", arrayOf(accountId))
            for (body in list) {
                val values = ContentValues()
                values.put(NOTIFICATIONS_TABLE_APP_URL_COLUMN, body.dappUrl.withoutQuery.toString().removeSuffix("/"))
                values.put(NOTIFICATIONS_TABLE_ACCOUNT_ID_COLUMN, accountId)
                values.put(NOTIFICATIONS_TABLE_BODY_COLUMN, body.toByteArray())
                writableDatabase.insert(NOTIFICATIONS_TABLE_NAME, null, values)
            }
        }
    }

    suspend fun insertNotification(body: AppPushEntity.Body) = withContext(coroutineContext) {
        writableDatabase.transaction {
            val values = ContentValues()
            values.put(NOTIFICATIONS_TABLE_APP_URL_COLUMN, body.dappUrl.withoutQuery.toString().removeSuffix("/"))
            values.put(NOTIFICATIONS_TABLE_ACCOUNT_ID_COLUMN, body.account.toRawAddress())
            values.put(NOTIFICATIONS_TABLE_BODY_COLUMN, body.toByteArray())
            writableDatabase.insert(NOTIFICATIONS_TABLE_NAME, null, values)
        }
    }

    suspend fun getNotifications(accountId: String): List<AppPushEntity.Body> = withContext(coroutineContext) {
        val query = "SELECT $notificationsFields FROM $NOTIFICATIONS_TABLE_NAME WHERE $NOTIFICATIONS_TABLE_ACCOUNT_ID_COLUMN = ?"
        val cursor = readableDatabase.rawQuery(query, arrayOf(accountId))
        val list = mutableListOf<AppPushEntity.Body>()
        val bodyIndex = cursor.getColumnIndex(NOTIFICATIONS_TABLE_BODY_COLUMN)
        while (cursor.moveToNext()) {
            cursor.getBlob(bodyIndex).toParcel<AppPushEntity.Body>()?.let {
                list.add(it)
            }
        }
        cursor.close()
        list
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
        apps.toList()
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
            FirebaseCrashlytics.getInstance().recordException(e)
            false
        }
    }

    suspend fun insertConnection(connection: AppConnectEntity) = withContext(coroutineContext) {
        try {
            val prefix = prefixAccount(connection.accountId, connection.network)

            writableDatabase.delete(CONNECT_TABLE_NAME, "$CONNECT_TABLE_CLIENT_ID_COLUMN = ?", arrayOf(connection.clientId))
            encryptedPrefs.transaction {
                remove(prefixKeyPair(prefix, connection.clientId))
                remove(prefixProofSignature(prefix, connection.appUrl))
                remove(prefixProofPayload(prefix, connection.appUrl))
            }



            val values = ContentValues()
            values.put(CONNECT_TABLE_APP_URL_COLUMN, connection.appUrl.withoutQuery.toString().removeSuffix("/"))
            values.put(CONNECT_TABLE_ACCOUNT_ID_COLUMN, connection.accountId)
            values.put(CONNECT_TABLE_NETWORK_COLUMN, connection.network.value)
            values.put(CONNECT_TABLE_CLIENT_ID_COLUMN, connection.clientId)
            values.put(CONNECT_TABLE_TYPE_COLUMN, connection.type.value)
            values.put(CONNECT_TABLE_TIMESTAMP_COLUMN, connection.timestamp)
            writableDatabase.insertOrThrow(CONNECT_TABLE_NAME, null, values)


            encryptedPrefs.transaction {
                putByteArray(prefixKeyPair(prefix, connection.clientId), connection.keyPair.toByteArray())

                if (connection.proofSignature != null) {
                    putString(prefixProofSignature(prefix, connection.appUrl), connection.proofSignature)
                }
                if (connection.proofPayload != null) {
                    putString(prefixProofPayload(prefix, connection.appUrl), connection.proofPayload)
                }
            }
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    suspend fun deleteConnect(connection: AppConnectEntity): Boolean = withContext(coroutineContext) {
        val count = writableDatabase.delete(CONNECT_TABLE_NAME, "$CONNECT_TABLE_CLIENT_ID_COLUMN = ?", arrayOf(connection.clientId))
        encryptedPrefs.transaction {
            val prefix = prefixAccount(connection.accountId, connection.network)
            val legacyPrefix = legacyPrefixAccount(connection.accountId, connection.network)
            remove(prefixKeyPair(prefix, connection.clientId))
            remove(prefixProofSignature(prefix, connection.appUrl))
            remove(prefixProofPayload(prefix, connection.appUrl))
            remove(prefixKeyPair(legacyPrefix, connection.clientId))
            remove(prefixProofSignature(legacyPrefix, connection.appUrl))
            remove(prefixProofPayload(legacyPrefix, connection.appUrl))
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
        val networkIndex = cursor.getColumnIndex(CONNECT_TABLE_NETWORK_COLUMN)
        val clientIdIndex = cursor.getColumnIndex(CONNECT_TABLE_CLIENT_ID_COLUMN)
        val typeIndex = cursor.getColumnIndex(CONNECT_TABLE_TYPE_COLUMN)
        val timestampIndex = cursor.getColumnIndex(CONNECT_TABLE_TIMESTAMP_COLUMN)

        val connections = mutableListOf<AppConnectEntity>()
        while (cursor.moveToNext()) {
            val accountId = cursor.getString(accountIdIndex)
            val networkValue = cursor.getInt(networkIndex)
            val network = TonNetwork.entries.firstOrNull { it.value == networkValue } ?: continue
            val clientId = cursor.getString(clientIdIndex)
            val appUrl = Uri.parse(cursor.getString(appUrlIndex)).withoutQuery

            val prefix = prefixAccount(accountId, network)
            var connectionEncrypted = getConnectionEncrypted(prefix, clientId, appUrl)
            if (connectionEncrypted == null) {
                val legacyPrefix = legacyPrefixAccount(accountId, network)
                connectionEncrypted = getConnectionEncrypted(legacyPrefix, clientId, appUrl)
                if (connectionEncrypted != null) {
                    migrateEncryptedData(legacyPrefix, prefix, clientId, appUrl, connectionEncrypted)
                }
            }
            connectionEncrypted ?: continue

            connections.add(AppConnectEntity(
                appUrl = appUrl,
                accountId = accountId,
                network = network,
                clientId = clientId,
                type = AppConnectEntity.Type.entries.first { it.value == cursor.getInt(typeIndex) },
                keyPair = connectionEncrypted.keyPair,
                proofSignature = connectionEncrypted.proofSignature,
                proofPayload = connectionEncrypted.proofPayload,
                timestamp = cursor.getLong(timestampIndex),
                pushEnabled = isPushEnabled(accountId, network, appUrl)
            ))
        }
        return connections
    }

    private fun migrateEncryptedData(
        oldPrefix: String,
        newPrefix: String,
        clientId: String,
        appUrl: Uri,
        encrypted: ConnectionEncryptedEntity
    ) {
        encryptedPrefs.transaction {
            remove(prefixKeyPair(oldPrefix, clientId))
            remove(prefixProofSignature(oldPrefix, appUrl))
            remove(prefixProofPayload(oldPrefix, appUrl))
            putParcelable(prefixKeyPair(newPrefix, clientId), encrypted.keyPair)

            if (encrypted.proofSignature != null) {
                putString(prefixProofSignature(newPrefix, appUrl), encrypted.proofSignature)
            }
            if (encrypted.proofPayload != null) {
                putString(prefixProofPayload(newPrefix, appUrl), encrypted.proofPayload)
            }
        }
    }

    private fun getConnectionEncrypted(
        prefix: String,
        clientId: String,
        appUrl: Uri
    ): ConnectionEncryptedEntity? {
        try {
            val keyPair = encryptedPrefs.getByteArray(prefixKeyPair(prefix, clientId))
                ?.toParcel<CryptoBox.KeyPair>()
                ?: return null

            return ConnectionEncryptedEntity(
                keyPair = keyPair,
                proofSignature = encryptedPrefs.get(prefixProofSignature(prefix, appUrl)),
                proofPayload = encryptedPrefs.get(prefixProofPayload(prefix, appUrl)),
            )
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            return null
        }
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

    private fun prefixAccount(accountId: String, network: TonNetwork): String {
        return "account_${accountId}:${network.value}"
    }

    private fun legacyPrefixAccount(accountId: String, network: TonNetwork): String {
        return "account_${accountId}:${if (network.isTestnet) "1" else "0"}"
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

    internal fun isPushEnabled(accountId: String, network: TonNetwork, appUrl: Uri): Boolean {
        val key = prefixPush(prefixAccount(accountId, network), appUrl)
        if (prefs.contains(key)) {
            return prefs.getBoolean(key, false)
        }
        return prefs.getBoolean(prefixPush(legacyPrefixAccount(accountId, network), appUrl), false)
    }

    internal fun setPushEnabled(accountId: String, network: TonNetwork, appUrl: Uri, enabled: Boolean) {
        prefs.edit {
            putBoolean(prefixPush(prefixAccount(accountId, network), appUrl), enabled)
        }
    }

    override fun close() {
        super.close()
        coroutineContext.cancel()
    }

    suspend fun clearConnections() = withContext(coroutineContext) {
        writableDatabase.delete(CONNECT_TABLE_NAME, null, null)
    }

    private companion object {
        private const val DATABASE_NAME = "dapps"
        private const val DATABASE_VERSION = 3

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
        private const val CONNECT_TABLE_NETWORK_COLUMN = "network"
        private const val CONNECT_TABLE_CLIENT_ID_COLUMN = "client_id"
        private const val CONNECT_TABLE_TYPE_COLUMN = "type"
        private const val CONNECT_TABLE_TIMESTAMP_COLUMN = "timestamp"

        private const val NOTIFICATIONS_TABLE_NAME = "notifications"
        private const val NOTIFICATIONS_TABLE_ID_COLUMN = "id"
        private const val NOTIFICATIONS_TABLE_APP_URL_COLUMN = "app_url"
        private const val NOTIFICATIONS_TABLE_ACCOUNT_ID_COLUMN = "account_id"
        private const val NOTIFICATIONS_TABLE_BODY_COLUMN = "body"

        private val appFields = arrayOf(
            APP_TABLE_URL_COLUMN,
            APP_TABLE_NAME_COLUMN,
            APP_TABLE_ICON_URL_COLUMN
        ).joinToString(",")

        private val connectFields = arrayOf(
            CONNECT_TABLE_APP_URL_COLUMN,
            CONNECT_TABLE_ACCOUNT_ID_COLUMN,
            CONNECT_TABLE_NETWORK_COLUMN,
            CONNECT_TABLE_CLIENT_ID_COLUMN,
            CONNECT_TABLE_TYPE_COLUMN,
            CONNECT_TABLE_TIMESTAMP_COLUMN
        ).joinToString(",")

        private val notificationsFields = arrayOf(
            NOTIFICATIONS_TABLE_ID_COLUMN,
            NOTIFICATIONS_TABLE_APP_URL_COLUMN,
            NOTIFICATIONS_TABLE_ACCOUNT_ID_COLUMN,
            NOTIFICATIONS_TABLE_BODY_COLUMN
        ).joinToString(",")
    }

}
