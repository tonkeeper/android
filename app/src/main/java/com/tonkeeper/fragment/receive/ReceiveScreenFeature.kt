package com.tonkeeper.fragment.receive

import android.graphics.Bitmap
import androidx.lifecycle.viewModelScope
import com.tonkeeper.App
import com.tonkeeper.helper.QRCodeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ton.wallet.Wallet
import ton.wallet.WalletInfo
import uikit.mvi.UiFeature

class ReceiveScreenFeature: UiFeature<ReceiveScreenState, ReceiveScreenEffect>(ReceiveScreenState()) {

    fun requestQRCode(size: Int) {
        viewModelScope.launch {
            val wallet = App.walletManager.getWalletInfo() ?: return@launch
            val bitmap = createQRCode(wallet, size)
            updateUiState {
                it.copy(
                    qrCode = bitmap,
                    address = wallet.address
                )
            }
        }
    }

    private suspend fun createQRCode(
        wallet: Wallet,
        size: Int
    ): Bitmap = withContext(Dispatchers.IO) {
        val deepLink = "ton://transfer/${wallet.address}"
        QRCodeHelper.createLink(deepLink, size)
    }
}