package com.tonapps.tonkeeper.ui.screen.name.edit

import android.app.Application
import androidx.lifecycle.ViewModel
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.data.account.AccountRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

class EditNameViewModel(
    app: Application,
    private val accountRepository: AccountRepository
): BaseWalletVM(app) {

    val uiLabelFlow = accountRepository.selectedWalletFlow.map { it.label }.take(1)

    fun save(name: String, emoji: CharSequence, color: Int) {
        accountRepository.editLabel(name, emoji.toString(), color)
    }
}