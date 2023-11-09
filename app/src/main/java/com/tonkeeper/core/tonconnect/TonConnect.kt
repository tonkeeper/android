package com.tonkeeper.core.tonconnect

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.crypto.tink.subtle.Hex
import com.tonkeeper.App
import com.tonkeeper.dialog.TonConnectDialog
import com.tonkeeper.core.tonconnect.models.TCApp
import com.tonkeeper.core.tonconnect.models.TCData
import com.tonkeeper.core.tonconnect.models.TCItem
import com.tonkeeper.core.tonconnect.models.TCKeyPair
import com.tonkeeper.core.tonconnect.models.TCManifest
import com.tonkeeper.core.tonconnect.models.TCProofPayload
import com.tonkeeper.core.tonconnect.models.TCRequest
import com.tonkeeper.core.tonconnect.models.reply.TCAddressItemReply
import com.tonkeeper.core.tonconnect.models.reply.TCConnectEventSuccess
import com.tonkeeper.core.tonconnect.models.reply.TCProofItemReplySuccess
import com.tonkeeper.core.tonconnect.models.reply.TCReply
import core.EncryptedKeyValue
import core.extensions.toBase64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ton.block.AddrStd
import org.ton.block.StateInit
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.contract.wallet.WalletContract
import ton.WalletInfo
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

class TonConnect(
    private val context: Context,
    private val scope: LifecycleCoroutineScope,
) {

    private val storage = EncryptedKeyValue(context, "ton-connect")
    private val manifestDao = App.db.tonConnectManifestDao()
    private val appRepository = AppRepository(storage)
    private val bridge = Bridge()
    private val proof = Proof()
    private val realtime = Realtime(context)

    private val onConnectApp: (TCData) -> Unit = { data ->
        connect(data)
    }

    private val dialog: TonConnectDialog by lazy {
        TonConnectDialog(context, onConnectApp)
    }

    fun start() {
        scope.launch(Dispatchers.IO) {
            val accountId = getAccountId()
            val clientIds = appRepository.getClientIds(accountId)
            realtime.start(clientIds)
        }
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
            val data = TCData(
                manifest = manifest,
                accountId = wallet.accountId,
                clientId = request.id,
                items = request.payload.items
            )
            dialog.setData(data)
        }
    }

    private fun connect(data: TCData) {
        scope.launch(Dispatchers.IO) {
            try {
                val accountId = getAccountId()
                val app = appRepository.createApp(accountId, data.url, data.clientId)
                val appKeyPair = TCKeyPair(
                    publicKey = app.publicKey,
                    privateKey = app.privateKey
                )

                val items = mutableListOf<TCReply>()
                for (requestItem in data.items) {
                    if (requestItem.name == TCItem.TON_ADDR) {
                        items.add(createAddressItemReply(accountId))
                    } else if (requestItem.name == TCItem.TON_PROOF) {
                        items.add(proof.createProofItemReplySuccess(
                            requestItem.payload!!,
                            app.host,
                            accountId,
                            appKeyPair
                        ))
                    }
                }

                val event = TCConnectEventSuccess(items)

                val data = Json.encodeToString(event)

                Log.d("TonConnectLog", "data: $data")

                bridge.sendEvent(data, app.clientId, appKeyPair)
            } catch (e: Throwable) {
                Log.e("TonConnectLog", "error", e)
            }


        }
    }

    private suspend fun createAddressItemReply(
        accountId: String
    ): TCAddressItemReply {
        val wallet = App.walletManager.getWalletInfo()!!

        val walletStateInit = getWalletStateInit(wallet)

        return TCAddressItemReply(
            address = accountId,
            network = "mainnet",
            walletStateInit = walletStateInit,
            publicKey = Hex.encode(wallet.publicKey.key.toByteArray())
        )
    }

    private fun getWalletStateInit(
        wallet: WalletInfo
    ): String {
        val stateInit = wallet.stateInit
        val builder = CellBuilder()
        StateInit.storeTlb(builder, stateInit)
        val cell = builder.endCell()
        return BagOfCells(cell).toByteArray().toBase64()
    }

    private suspend fun manifest(url: String): TCManifest {
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

    private suspend fun getAccountId(): String = withContext(Dispatchers.IO) {
        val wallet = App.walletManager.getWalletInfo() ?: return@withContext ""
        return@withContext wallet.accountId
    }

    private suspend fun getContract(): WalletContract<Cell> = withContext(Dispatchers.IO) {
        return@withContext App.walletManager.getWalletInfo()!!.contract
    }

    private suspend fun getApp(
        url: String
    ): TCApp = withContext(Dispatchers.IO) {
        val accountId = getAccountId()
        return@withContext appRepository.getApp(accountId, url)
    }

    fun destroy() {
        realtime.release()
    }

}