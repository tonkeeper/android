package com.tonapps.tonkeeper.core.tonconnect

import android.content.Context
import android.net.Uri
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.api.totalFees
import com.tonapps.tonkeeper.core.Coin
import com.tonapps.tonkeeper.core.currency.ton
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.core.tonconnect.models.TCApp
import com.tonapps.tonkeeper.core.tonconnect.models.TCEvent
import com.tonapps.tonkeeper.core.tonconnect.models.TCRequest
import com.tonapps.tonkeeper.core.tonconnect.models.TCTransaction
import com.tonapps.tonkeeper.core.tonconnect.models.reply.TCBase
import com.tonapps.tonkeeper.core.tonconnect.models.reply.TCResultError
import com.tonapps.tonkeeper.core.tonconnect.models.reply.TCResultSuccess
import com.tonapps.tonkeeper.event.RequestActionEvent
import com.tonapps.tonkeeper.extensions.emulate
import com.tonapps.tonkeeper.extensions.sendToBlockchain
import com.tonapps.tonkeeper.fragment.root.RootActivity
import com.tonapps.tonkeeper.fragment.tonconnect.auth.TCAuthFragment
import core.EventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.ton.contract.wallet.WalletTransfer
import ton.extensions.base64
import uikit.extensions.activity
import java.nio.charset.Charset

class TonConnect(private val context: Context) {

    companion object {

        fun from(context: Context): TonConnect? {
            val activity = context.activity as? RootActivity ?: return null
            return activity.tonConnect
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val appRepository = AppRepository()
    private val bridge = Bridge()

    private val realtime = Realtime(context) { event ->
        onEvent(event)
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

    fun restartEventHandler() {
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
        return uri.host == "app.tonkeeper.com" && uri.path == "/ton-connect" || uri.host == "ton-connect"
    }

    fun resolveScreen(uri: Uri): TCAuthFragment? {
        val request = try {
            TCRequest(uri)
        } catch (e: Throwable) {
            return null
        }

        return TCAuthFragment.newInstance(request)
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

                val params = json.getJSONArray("params")
                val id = json.getString("id")
                val transfers = TCHelper.createWalletTransfers(params)

                if (transfers.isEmpty()) {
                    return@launch
                }

                val wallet = com.tonapps.tonkeeper.App.walletManager.getWalletInfo() ?: return@launch
                val response = wallet.emulate(transfers)

                val currency = com.tonapps.tonkeeper.App.settings.currency
                val items = HistoryHelper.mapping(wallet, response.event, false, true)
                val fee = Coin.toCoins(response.totalFees)
                val feeInCurrency = wallet.ton(fee)
                    .convert(currency.code)

                val feeFormat = "≈ " + CurrencyFormatter.format("TON", fee) + " · " + CurrencyFormatter.formatFiat(currency.code, feeInCurrency)

                val transaction = TCTransaction(
                    clientId = app.clientId,
                    id = id,
                    transfers = transfers,
                    fee = feeFormat,
                    previewItems = items,
                )

                EventBus.post(RequestActionEvent(transaction))
            } catch (ignored: Throwable) { }
        }
    }

    fun cancelTransaction(id: String, clientId: String) {
        scope.launch {
            val error = TCResultError(id = id, errorCode = 300, errorMessage = "Reject Request")
            sendEvent(clientId, error)
        }
    }

    suspend fun signTransaction(
        id: String,
        clientId: String,
        transfers: List<WalletTransfer>
    ) = withContext(Dispatchers.IO) {
        val wallet = com.tonapps.tonkeeper.App.walletManager.getWalletInfo() ?: return@withContext
        val privateKey = com.tonapps.tonkeeper.App.walletManager.getPrivateKey(wallet.id)

        val boc = wallet.sendToBlockchain(privateKey, transfers)?.base64() ?: throw Exception("Error send to blockchain")

        val success = TCResultSuccess(id = id, result = boc)
        sendEvent(clientId, success)
    }

    private suspend fun getAccountId(): String = withContext(Dispatchers.IO) {
        val wallet = com.tonapps.tonkeeper.App.walletManager.getWalletInfo() ?: return@withContext ""
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