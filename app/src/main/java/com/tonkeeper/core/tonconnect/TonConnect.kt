package com.tonkeeper.core.tonconnect

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tonkeeper.App
import com.tonkeeper.api.TonNetwork
import com.tonkeeper.api.Tonapi
import com.tonkeeper.core.Coin
import com.tonkeeper.core.currency.from
import com.tonkeeper.core.formatter.CurrencyFormatter
import com.tonkeeper.core.history.HistoryHelper
import com.tonkeeper.dialog.tc.TonConnectDialog
import com.tonkeeper.core.tonconnect.models.TCApp
import com.tonkeeper.core.tonconnect.models.TCData
import com.tonkeeper.core.tonconnect.models.TCEvent
import com.tonkeeper.core.tonconnect.models.TCItem
import com.tonkeeper.core.tonconnect.models.TCKeyPair
import com.tonkeeper.core.tonconnect.models.TCManifest
import com.tonkeeper.core.tonconnect.models.TCRequest
import com.tonkeeper.core.tonconnect.models.TCTransaction
import com.tonkeeper.core.tonconnect.models.event.TransactionParam
import com.tonkeeper.core.tonconnect.models.reply.TCAddressItemReply
import com.tonkeeper.core.tonconnect.models.reply.TCBase
import com.tonkeeper.core.tonconnect.models.reply.TCConnectEventSuccess
import com.tonkeeper.core.tonconnect.models.reply.TCReply
import com.tonkeeper.core.tonconnect.models.reply.TCResultError
import com.tonkeeper.core.tonconnect.models.reply.TCResultSuccess
import com.tonkeeper.core.transaction.TransactionHelper
import com.tonkeeper.event.RequestActionEvent
import com.tonkeeper.fragment.root.RootActivity
import core.EventBus
import core.keyvalue.EncryptedKeyValue
import io.ktor.util.hex
import io.tonapi.models.EmulateMessageToWalletRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import org.json.JSONArray
import org.json.JSONObject
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.Message
import org.ton.block.StateInit
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.buildCell
import org.ton.contract.wallet.WalletContract
import org.ton.contract.wallet.WalletTransfer
import org.ton.contract.wallet.WalletTransferBuilder
import org.ton.crypto.base64
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.storeTlb
import ton.SendMode
import ton.SupportedTokens
import ton.contract.WalletV4R2Contract
import uikit.extensions.activity
import java.net.URL
import java.nio.charset.Charset
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.seconds

class TonConnect(private val context: Context) {

    companion object {

        fun from(context: Context): TonConnect? {
            val activity = context.activity as? RootActivity ?: return null
            return activity.tonConnect
        }
    }

    val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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
        scope.launch {
            stopEventHandler()
            startEventHandler()
        }
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
                accountId = wallet.accountId.lowercase(),
                clientId = request.id,
                items = request.payload.items
            )
            dialog.setData(data)
        }
    }

    private fun onEvent(event: TCEvent) {
        scope.launch(Dispatchers.IO) {
            val app = getApp(event.from) ?: return@launch
            try {
                val msg = app.decrypt(event.body).toString(Charset.defaultCharset())
                val json = JSONObject(msg)
                if (json.getString("method") != "sendTransaction") {
                    return@launch
                }

                val wallet = App.walletManager.getWalletInfo() ?: return@launch
                val accountId = wallet.accountId
                val seqno = TransactionHelper.getSeqno(accountId)
                val walletId = WalletContract.DEFAULT_WALLET_ID
                val privateKey = PrivateKeyEd25519()

                val params = json.getJSONArray("params")
                val id = json.getString("id")
                val transfers = TCHelper.createWalletTransfers(params)

                if (transfers.isEmpty()) {
                    return@launch
                }

                val message = TCHelper.signMessage(wallet, seqno, privateKey, *transfers.toTypedArray())

                val cell = buildCell {
                    storeTlb(Message.tlbCodec(AnyTlbConstructor), message)
                }

                val boc = base64(BagOfCells(cell).toByteArray())
                val request = EmulateMessageToWalletRequest(boc)
                val response = Tonapi.emulation.emulateMessageToWallet(request)

                val currency = App.settings.currency
                val items = HistoryHelper.mapping(wallet, response.event, false, true)
                val fee = Coin.toCoins(response.trace.transaction.totalFees)
                val feeInCurrency = from(SupportedTokens.TON, wallet.accountId)
                    .value(fee)
                    .to(currency)

                val feeFormat = "≈ " + CurrencyFormatter.format(SupportedTokens.TON.code, fee) + " · " + CurrencyFormatter.formatFiat(feeInCurrency)

                val transaction = TCTransaction(
                    clientId = app.clientId,
                    id = id,
                    transfers = transfers,
                    fee = feeFormat,
                    previewItems = items,
                )

                EventBus.post(RequestActionEvent(transaction))
            } catch (e: Throwable) {
                Log.e("TonConnectLog", "failed parse event", e)
            }
        }
    }

    fun cancelTransaction(id: String, clientId: String) {
        scope.launch {
            val error = TCResultError(id = id, errorCode = 300, errorMessage = "Reject Request")
            sendEvent(clientId, error)
        }
    }

    suspend fun signTransaction(id: String, clientId: String, transfers: List<WalletTransfer>) = withContext(Dispatchers.IO) {
        val wallet = App.walletManager.getWalletInfo() ?: return@withContext
        val accountId = wallet.accountId
        val seqno = TransactionHelper.getSeqno(accountId)
        val privateKey = App.walletManager.getPrivateKey(wallet.id)

        val message = TCHelper.signMessage(wallet, seqno, privateKey, *transfers.toTypedArray())

        val cell = buildCell {
            storeTlb(Message.tlbCodec(AnyTlbConstructor), message)
        }

        val boc = base64(BagOfCells(cell).toByteArray())

        val success = TCResultSuccess(id = id, result = boc)
        sendEvent(clientId, success)
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
                    dialog.setSuccess()
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    dialog.setFailure()
                }
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
            publicKey = hex(wallet.publicKey.toByteArray())
        )
    }

    private fun getWalletStateInit(
        stateInit: StateInit
    ): String {
        val cell = CellBuilder()
            .storeTlb(StateInit.tlbCodec(), stateInit)
            .endCell()
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

    suspend fun sendEvent(clientId: String, event: TCBase) {
        val app = getApp(clientId) ?: return
        bridge.sendEvent(event.toJSON(), app)
    }

    suspend fun sendEvent(clientId: String, event: String) {
        val app = getApp(clientId) ?: return
        bridge.sendEvent(event, app)
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