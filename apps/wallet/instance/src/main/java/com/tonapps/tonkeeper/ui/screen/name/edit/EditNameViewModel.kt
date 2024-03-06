package com.tonapps.tonkeeper.ui.screen.name.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.wallet.data.account.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

class EditNameViewModel(
    private val walletRepository: WalletRepository
): ViewModel() {

    val uiLabelFlow = walletRepository.activeWalletFlow.map { it.label }.take(1)

    fun save(name: String, emoji: CharSequence, color: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            walletRepository.editLabel(name, emoji.toString(), color)
        }
    }
}