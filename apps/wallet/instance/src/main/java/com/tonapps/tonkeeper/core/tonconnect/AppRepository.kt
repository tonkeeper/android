package com.tonapps.tonkeeper.core.tonconnect

import android.content.Context
import com.tonapps.tonkeeper.core.tonconnect.db.AppEntity
import com.tonapps.tonkeeper.core.tonconnect.models.TCApp
import core.keyvalue.EncryptedKeyValue
import io.ktor.util.hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.libsodium.jni.Sodium

internal class AppRepository(
    context: Context = com.tonapps.tonkeeper.App.instance
) {

    private companion object {
        private const val APP_PRIVATE_KEY = "app_private_key"
    }

    private val storage = EncryptedKeyValue(context, "ton-connect")
    private val appDao = com.tonapps.tonkeeper.App.db.tonConnectAppDao()

    suspend fun createApp(
        accountId: String,
        url: String,
        clientId: String
    ): TCApp = withContext(Dispatchers.IO) {
        val app = buildApp(url, clientId)
        addApp(accountId, app)
        return@withContext app
    }

    private suspend fun addApp(
        accountId: String,
        app: TCApp
    ) = withContext(Dispatchers.IO) {
        val storageKey = createStorageKey(accountId, app.url)
        storage.putByteArray(storageKey, app.privateKey)

        val entity = AppEntity(
            id = "${accountId}_${app.url}",
            url = app.url,
            accountId = accountId,
            clientId = app.clientId,
            publicKeyHex = hex(app.publicKey),
        )
        appDao.insertApp(entity)
    }

    private fun buildApp(url: String, clientId: String): TCApp {
        val publicKey = ByteArray(32)
        val privateKey = ByteArray(32)
        Sodium.crypto_box_keypair(publicKey, privateKey)

        return TCApp(
            url = url,
            clientId = clientId,
            publicKey = publicKey,
            privateKey = privateKey,
        )
    }

    suspend fun getApp(
        accountId: String,
        clientId: String
    ): TCApp? = withContext(Dispatchers.IO) {
        val entity = appDao.getAppEntity(accountId, clientId) ?: return@withContext null
        val storageKey = createStorageKey(accountId, entity.url)
        val privateKey = storage.getByteArray(storageKey)

        return@withContext TCApp(
            url = entity.url,
            clientId = entity.clientId,
            publicKey = hex(entity.publicKeyHex),
            privateKey = privateKey!!,
        )
    }

    private fun createStorageKey(
        accountId: String,
        url: String
    ): String {
        return "$APP_PRIVATE_KEY-$accountId-$url"
    }

    suspend fun getClientIds(
        accountId: String
    ): List<String> = withContext(Dispatchers.IO) {
        val apps = appDao.getApps(accountId)
        return@withContext apps.map { it.publicKeyHex }
    }

}