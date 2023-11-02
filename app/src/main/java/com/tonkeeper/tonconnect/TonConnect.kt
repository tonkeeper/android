package com.tonkeeper.tonconnect

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import com.tonkeeper.App
import com.tonkeeper.api.userLikeAddress
import com.tonkeeper.dialog.TonConnectDialog
import com.tonkeeper.tonconnect.db.ManifestEntity
import com.tonkeeper.tonconnect.models.TCManifest
import com.tonkeeper.tonconnect.models.TCPayload
import com.tonkeeper.tonconnect.models.TCRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.api.pk.PrivateKeyEd25519
import java.io.File
import java.net.URL
import java.security.KeyPair

class TonConnect(
    private val context: Context,
    private val scope: LifecycleCoroutineScope,
) {

    private val dao = App.db.tonConnectManifestDao()
    private val bridge = Bridge()

    private val dialog: TonConnectDialog by lazy {
        TonConnectDialog(context)
    }

    fun isSupportUri(uri: Uri): Boolean {
        if (uri.scheme == "tc") {
            return true
        }
        return uri.host == "app.tonkeeper.com" && uri.path == "/ton-connect"
    }

    fun processUri(uri: Uri) {
        val request = TCRequest(uri)

        dialog.show()
        scope.launch {
            val wallet = App.walletManager.getWalletInfo() ?: return@launch
            val manifest = manifest(request.payload.manifestUrl)
            Log.d("HandleIntentLog", "payload: ${request.payload}")
            dialog.setData(manifest.iconUrl, manifest.name, manifest.url, wallet.address.userLikeAddress)


            val sessionKey = PrivateKeyEd25519()

        }
    }

    private suspend fun manifest(url: String): TCManifest {
        return cacheManifest(url) ?: downloadManifest(url)
    }

    private suspend fun cacheManifest(url: String): TCManifest? {
        val manifest = dao.get(url)
        if (manifest != null) {
            return TCManifest(manifest.data)
        }
        return null
    }

    private suspend fun downloadManifest(
        url: String
    ): TCManifest = withContext(Dispatchers.IO) {
        val data = URL(url).readText()
        dao.insert(url, data)
        TCManifest(data)
    }

}