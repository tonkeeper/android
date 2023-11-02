package com.tonkeeper.fragment.receive

import android.graphics.Bitmap
import androidx.lifecycle.viewModelScope
import com.tonkeeper.App
import com.tonkeeper.helper.QRCodeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ton.WalletInfo
import uikit.mvi.UiFeature

class ReceiveScreenFeature: UiFeature<ReceiveScreenState, ReceiveScreenEffect>(ReceiveScreenState()) {

    fun requestQRCode(size: Int) {
        viewModelScope.launch {
            val walletInfo = App.walletManager.getWalletInfo() ?: return@launch
            val bitmap = createQRCode(walletInfo, size)
            updateUiState {
                it.copy(
                    qrCode = bitmap,
                    address = walletInfo.address
                )
            }
        }
    }

    private suspend fun createQRCode(walletInfo: WalletInfo, size: Int): Bitmap = withContext(Dispatchers.IO) {
        val deepLink = "ton://transfer/${walletInfo.address}"
        QRCodeHelper.createLink(deepLink, size)
    }
}