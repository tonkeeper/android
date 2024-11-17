package com.tonapps.tonkeeper.ui.screen.qr

import android.app.Application
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.data.account.entities.WalletEntity

class QRViewModel(
    app: Application,
    private val wallet: WalletEntity
): BaseWalletVM(app) {

}