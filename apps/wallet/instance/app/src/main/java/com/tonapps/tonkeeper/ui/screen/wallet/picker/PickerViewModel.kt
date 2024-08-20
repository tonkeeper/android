package com.tonapps.tonkeeper.ui.screen.wallet.picker

import android.app.Application
import androidx.lifecycle.ViewModel
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.take

class PickerViewModel(
    app: Application,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
): BaseWalletVM(app) {

    private val _editModeFlow = MutableStateFlow(false)
    val editModeFlow = _editModeFlow.asStateFlow()

    val walletChangedFlow = accountRepository.selectedWalletFlow.drop(1).take(1)

    fun toggleEditMode() {
        _editModeFlow.value = !_editModeFlow.value
    }

    fun saveOrder(wallerIds: List<String>) {
        settingsRepository.setWalletsSort(wallerIds)
    }
}