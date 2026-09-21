package com.tonapps.tonkeeper.ui.screen.battery.web

import android.net.Uri
import android.webkit.JavascriptInterface
import androidx.lifecycle.lifecycleScope
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.log.L
import com.tonapps.tonkeeper.ui.component.TonConnectWebView
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppArgs
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppScreen
import com.tonapps.wallet.data.multichain.device.SecureDeviceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.koin.android.ext.android.inject
import androidx.core.net.toUri
import com.tonapps.async.Async

class BatteryWebScreen(wallet: WalletEntity) : DAppScreen(wallet) {

    override val fragmentName: String = "BatteryWebScreen"

    override val isTonConnectInjectionEnabled: Boolean = false

    private val deviceRepository: SecureDeviceRepository by inject()

    private val batteryBridge = BatteryWebBridge { expiredAccessToken -> // TODO to ViewModel
        withContext(Async.Io) {
            L.d("Loading auth data: refresh=${expiredAccessToken != null}")

            val deviceToken = if (expiredAccessToken == null) {
                deviceRepository.loadAccessToken()
            } else {
                deviceRepository.renewAccessToken(expiredAccessToken)
            }
            L.d("Device token loaded: available=${!deviceToken.isNullOrBlank()} changed=${deviceToken != null && expiredAccessToken != null && deviceToken != expiredAccessToken}")

            val currentWallet = this@BatteryWebScreen.wallet
            L.d("Loading proof token: walletType=${currentWallet.type}")

            val proofToken = deviceToken
                ?.takeIf { it.isNotBlank() }
                ?.let { deviceRepository.walletAuthToken(currentWallet.id, it) }
            L.d("Proof token loaded: available=${!proofToken.isNullOrBlank()}")

            BatteryWebBridge.Data(
                walletId = wallet.id,
                deviceToken = deviceToken.orEmpty(),
                walletToken = proofToken.orEmpty(),
            )
        }
    }

    override fun onWebViewCreated(webView: TonConnectWebView) {
        super.onWebViewCreated(webView)
        webView.setJavascriptInterface(BatteryJavascriptInterface(webView))
        L.d("JavascriptInterface attached: host=${startUri.host}")
    }

    private fun handleMessage(webView: TonConnectWebView, message: String) {
        val owner = viewLifecycleOwnerLiveData.value ?: return
        if (this.webView !== webView) {
            return
        }

        val pageUri = webView.uri ?: return
        if (!isBatteryOrigin(pageUri)) {
            L.d("Message rejected: untrusted page host=${pageUri.host}")
            return
        }

        owner.lifecycleScope.launch {
            try {
                val response = batteryBridge.handleMessage(message) ?: return@launch
                sendResponse(webView, response)
            } catch (e: CancellationException) {
                L.d("Request cancelled")
                throw e
            } catch (e: Exception) {
                L.d("Request failed: error=${e.javaClass.simpleName}")
            }
        }
    }

    private fun sendResponse(webView: TonConnectWebView, response: String) {
        val response = JSONObject(response)
        val queryId = response.getString("queryId")

        webView.postMessage(response) { result ->
            L.d("Response postMessage result: queryId=$queryId, result=$result")
        }
    }

    private fun isBatteryOrigin(origin: Uri): Boolean {
        return startUri.scheme == "https" && origin.scheme == "https" &&
            !startUri.host.isNullOrBlank() && origin.host.equals(startUri.host, ignoreCase = true) &&
            (origin.port.takeIf { it != -1 } ?: 443) == (startUri.port.takeIf { it != -1 } ?: 443)
    }

    override fun onDestroyView() {
        L.d("Bridge detached")
        super.onDestroyView()
    }

    private inner class BatteryJavascriptInterface(
        private val webView: TonConnectWebView,
    ) {
        @JavascriptInterface
        fun postMessage(message: String) {
            webView.post {
                handleMessage(webView, message)
            }
        }
    }

    companion object {
        fun newInstance(wallet: WalletEntity, url: Uri): BatteryWebScreen {
            require(wallet.isMultichain) { "BatteryWebScreen requires a multichain wallet" }
            return BatteryWebScreen(wallet).apply {
                setArgs(DAppArgs(
                    title = "Battery",
                    url = url,
                    source = "battery_refund",
                    iconUrl = "",
                    forceConnect = false,
                ))
            }
        }
    }
}
