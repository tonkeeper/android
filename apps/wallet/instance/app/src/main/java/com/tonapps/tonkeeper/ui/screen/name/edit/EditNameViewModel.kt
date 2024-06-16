package com.tonapps.tonkeeper.ui.screen.name.edit

import androidx.lifecycle.ViewModel
import com.tonapps.wallet.data.account.AccountRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

class EditNameViewModel(
    private val accountRepository: AccountRepository
): ViewModel() {

    val uiLabelFlow = accountRepository.selectedWalletFlow.map { it.label }.take(1)

    fun save(name: String, emoji: CharSequence, color: Int) {
        accountRepository.editLabel(name, emoji.toString(), color)
    }
}