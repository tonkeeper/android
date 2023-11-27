package com.tonkeeper.core.tonconnect

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tonkeeper.App
import com.tonkeeper.api.TonNetwork
import com.tonkeeper.dialog.tc.TonConnectDialog
import com.tonkeeper.core.tonconnect.models.TCApp
import com.tonkeeper.core.tonconnect.models.TCData
import com.tonkeeper.core.tonconnect.models.TCEvent
import com.tonkeeper.core.tonconnect.models.TCItem
import com.tonkeeper.core.tonconnect.models.TCKeyPair
import com.tonkeeper.core.tonconnect.models.TCManifest
import com.tonkeeper.core.tonconnect.models.TCRequest
import com.tonkeeper.core.tonconnect.models.reply.TCAddressItemReply
import com.tonkeeper.core.tonconnect.models.reply.TCConnectEventSuccess
import com.tonkeeper.core.tonconnect.models.reply.TCReply
import core.keyvalue.EncryptedKeyValue
import io.ktor.util.Identity.decode
import io.ktor.util.hex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.ton.block.StateInit
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.contract.wallet.WalletContract
import org.ton.crypto.base64
import java.net.URL
import java.nio.charset.Charset

class TonConnect(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val storage = EncryptedKeyValue(context, "ton-connect")
    private val manifestDao = App.db.tonConnectManifestDao()
    private val appRepository = AppRepository(storage)
    private val bridge = Bridge()
    private val proof = Proof()
    private val realtime = Realtime(context) { event ->
        onEvent(event)
    }

    private val onConnectApp: (TCData) -> Unit = { data ->
        connect(data)
    }

    private val dialog: TonConnectDialog by lazy {
        TonConnectDialog(context, onConnectApp)
    }

    fun onCreate() {
        scope.launch { startEventHandler() }
    }

    fun onDestroy() {
        stopEventHandler()
        scope.cancel()
    }

    private fun restartEventHandler() {
        stopEventHandler()
        scope.launch { startEventHandler() }
    }

    private suspend fun startEventHandler() {
        val accountId = getAccountId()
        val clientIds = appRepository.getClientIds(accountId)
        realtime.start(clientIds)
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

    private fun onEvent(event: TCEvent) {
        scope.launch {
            val app = getApp(event.from) ?: return@launch
            try {
                val msg = app.decrypt(event.body).toString(Charset.defaultCharset())
                val json = JSONObject(msg)
                val method = json.getString("method")

                Log.d("TonConnectLog", "method: $method")
            } catch (ignored: Throwable) {
                Log.e("TonConnectLog", "error", ignored)
            }
        }
    }

    private fun connect(data: TCData) {
        scope.launch(Dispatchers.IO) {
            try {
                val accountId = getAccountId()
                val app = appRepository.createApp(accountId, data.url, data.clientId)

                val appKeyPair = TCKeyPair(
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
                bridge.sendEvent(event.toJSON(), app)

                restartEventHandler()

                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                }
            } catch (e: Throwable) {
                Log.e("TonConnectLog", "error", e)
            }
        }
    }

    private suspend fun createAddressItemReply(
        accountId: String
    ): TCAddressItemReply {
        val wallet = App.walletManager.getWalletInfo()!!

        val walletStateInit = getWalletStateInit(wallet.stateInit)

        return TCAddressItemReply(
            address = accountId,
            network = TonNetwork.MAINNET.value.toString(),
            walletStateInit = walletStateInit,
            publicKey = hex(wallet.publicKey.key.toByteArray())
        )
    }

    private fun getWalletStateInit(
        stateInit: StateInit
    ): String {
        val builder = CellBuilder()
        StateInit.storeTlb(builder, stateInit)
        val cell = builder.endCell()
        return base64(BagOfCells(cell).toByteArray())
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
        clientId: String
    ): TCApp? = withContext(Dispatchers.IO) {
        val accountId = getAccountId()
        return@withContext appRepository.getApp(accountId, clientId)
    }

    private fun stopEventHandler() {
        realtime.release()
    }

}