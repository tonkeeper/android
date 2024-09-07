package com.tonapps.tonkeeper.ui.screen.purchase.web

import android.app.Application
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.purchase.PurchaseRepository
import com.tonapps.wallet.data.settings.SettingsRepository

class PurchaseWebViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val settingsRepository: SettingsRepository,
    private val purchaseRepository: PurchaseRepository
): BaseWalletVM(app) {

    fun replaceUrl(url: String): String {
        return purchaseRepository.replaceUrl(
            url = url,
            address = wallet.address,
            currency = settingsRepository.country
        )
    }

}