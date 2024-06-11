package com.tonapps.tonkeeper.ui.screen.name.edit

import androidx.lifecycle.ViewModel
import com.tonapps.wallet.data.account.repository.BaseWalletRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

class EditNameViewModel(
    private val walletRepository: BaseWalletRepository
): ViewModel() {

    val uiLabelFlow = walletRepository.activeWalletFlow.map { it.label }.take(1)

    fun save(name: String, emoji: CharSequence, color: Int) {
        walletRepository.saveLabel(name, emoji.toString(), color)
    }
}