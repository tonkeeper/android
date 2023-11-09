package com.tonkeeper.core.tonconnect

import com.tonkeeper.App
import com.tonkeeper.core.tonconnect.db.AppEntity
import com.tonkeeper.core.tonconnect.models.TCApp
import core.EncryptedKeyValue
import core.extensions.toBase64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ton.api.pk.PrivateKeyEd25519

internal class AppRepository(
    private val storage: EncryptedKeyValue
) {

    private companion object {
        private const val APP_PRIVATE_KEY = "app_private_key"
    }

    private val appDao = App.db.tonConnectAppDao()

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
            publicKeyBase64 = app.publicKey.toBase64()
        )
        appDao.insertApp(entity)
    }

    private fun buildApp(url: String, clientId: String): TCApp {
        val keyPair = PrivateKeyEd25519()
        return TCApp(
            url = url,
            clientId = clientId,
            privateKey = keyPair.key.toByteArray(),
            publicKey = keyPair.publicKey().toByteArray()
        )
    }

    suspend fun getApp(
        accountId: String,
        url: String
    ): TCApp = withContext(Dispatchers.IO) {
        val entity = appDao.getAppEntity(accountId, url)
        val storageKey = createStorageKey(accountId, url)
        val privateKey = storage.getByteArray(storageKey)

        return@withContext TCApp(
            url = entity.url,
            clientId = entity.clientId,
            privateKey = privateKey!!,
            publicKey = entity.publicKey
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
        return@withContext appDao.getClientIds(accountId)
    }

}