package com.tonkeeper.core.tonconnect

import com.tonkeeper.App
import com.tonkeeper.core.tonconnect.models.TCManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

class TCManifestRepository {

    private val manifestDao = App.db.tonConnectManifestDao()

    suspend fun manifest(url: String): TCManifest {
        return cacheManifest(url) ?: downloadManifest(url)
    }

    private suspend fun cacheManifest(url: String): TCManifest? {
        val manifest = manifestDao.get(url)
        if (manifest != null) {
            return TCManifest(manifest.data)
        }
        return null
    }

    private suspend fun downloadManifest(
        url: String
    ): TCManifest = withContext(Dispatchers.IO) {
        val data = URL(url).readText()
        manifestDao.insert(url, data)
        TCManifest(data)
    }


}