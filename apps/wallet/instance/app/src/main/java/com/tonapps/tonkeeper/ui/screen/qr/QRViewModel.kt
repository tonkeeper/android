package com.tonapps.tonkeeper.ui.screen.qr

import android.app.Application
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.settings.SettingsRepository

class QRViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val settingsRepository: SettingsRepository
): BaseWalletVM(app) {


    val installId: String
        get() = settingsRepository.installId

}