package com.tonapps.tonkeeper.ui.screen.name.edit

import android.app.Application
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.worker.WidgetUpdaterWorker
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity

class EditNameViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val accountRepository: AccountRepository
): BaseWalletVM(app) {

    fun save(name: String, emoji: CharSequence, color: Int) {
        accountRepository.editLabel(
            walletId = wallet.id,
            name = name,
            emoji = emoji,
            color = color
        )

        WidgetUpdaterWorker.update(context)
    }
}