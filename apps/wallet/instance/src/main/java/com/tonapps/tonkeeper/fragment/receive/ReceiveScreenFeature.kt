package com.tonapps.tonkeeper.fragment.receive

import android.graphics.Bitmap
import androidx.lifecycle.viewModelScope
import com.tonapps.qr.QR
import com.tonapps.tonkeeper.api.getAddress
import io.tonapi.models.JettonBalance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ton.wallet.Wallet
import uikit.mvi.UiFeature

class ReceiveScreenFeature: UiFeature<ReceiveScreenState, ReceiveScreenEffect>(ReceiveScreenState()) {

    fun copy() {
        viewModelScope.launch {
            val wallet = com.tonapps.tonkeeper.App.walletManager.getWalletInfo() ?: return@launch
            sendEffect(ReceiveScreenEffect.Copy(wallet.address))
        }
    }

    fun share() {
        viewModelScope.launch {
            val wallet = com.tonapps.tonkeeper.App.walletManager.getWalletInfo() ?: return@launch
            sendEffect(ReceiveScreenEffect.Share(wallet.address))
        }
    }

    fun requestQRCode(size: Int, jetton: JettonBalance?) {
        viewModelScope.launch {
            val wallet = com.tonapps.tonkeeper.App.walletManager.getWalletInfo() ?: return@launch
            val bitmap = createBitmap(wallet, size, jetton)
            updateUiState {
                it.copy(
                    qrCode = bitmap,
                    address = wallet.address
                )
            }
        }
    }

    private suspend fun createBitmap(
        wallet: Wallet,
        size: Int,
        jetton: JettonBalance?
    ): Bitmap = withContext(Dispatchers.IO) {
        var content = "ton://transfer/${wallet.address}"
        if (jetton != null) {
            content += "?jetton=${jetton.getAddress(wallet.testnet)}"
        }
        val bitmap = QR.Builder(content)
            .setSize(size)
            .setWithCutout(true)
            .build()
        bitmap
    }
}