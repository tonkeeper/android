package com.tonkeeper.fragment.receive

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.tonkeeper.App
import com.tonkeeper.helper.QRBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ton.wallet.Wallet
import uikit.mvi.UiFeature

class ReceiveScreenFeature: UiFeature<ReceiveScreenState, ReceiveScreenEffect>(ReceiveScreenState()) {

    fun copy() {
        viewModelScope.launch {
            val wallet = App.walletManager.getWalletInfo() ?: return@launch
            sendEffect(ReceiveScreenEffect.Copy(wallet.address))
        }
    }

    fun share() {
        viewModelScope.launch {
            val wallet = App.walletManager.getWalletInfo() ?: return@launch
            sendEffect(ReceiveScreenEffect.Share(wallet.address))
        }
    }

    fun requestQRCode(size: Int) {
        viewModelScope.launch {
            val wallet = App.walletManager.getWalletInfo() ?: return@launch
            val bitmap = createBitmap(wallet, size)
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
        size: Int
    ): Bitmap = withContext(Dispatchers.IO) {
        val content = "ton://transfer/${wallet.address}"
        val bitmap = QRBuilder(content, size, size)
            .setWithCutout(true)
            .build()
        bitmap
    }
}