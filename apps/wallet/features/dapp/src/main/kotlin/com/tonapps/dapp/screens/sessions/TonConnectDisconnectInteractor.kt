package com.tonapps.dapp.screens.sessions

import android.net.Uri
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity

interface TonConnectDisconnectInteractor {

    suspend fun disconnectApp(wallet: WalletEntity, appUrl: Uri, type: AppConnectEntity.Type)
}
