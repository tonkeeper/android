package com.tonapps.tonkeeper.ui.screen.wallet.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.core.widget.Widget
import com.tonapps.tonkeeper.ui.screen.wallet.picker.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.WalletRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.delayFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PickerViewModel(
    private val walletRepository: WalletRepository,
    private val settingsRepository: SettingsRepository,
): ViewModel() {

    private val _editModeFlow = MutableStateFlow(false)
    val editModeFlow = _editModeFlow.asStateFlow()

    val walletChangedFlow = walletRepository.activeWalletFlow.drop(1).take(1)

    fun toggleEditMode() {
        _editModeFlow.value = !_editModeFlow.value
    }

    fun saveOrder(wallerIds: List<Long>) {
        settingsRepository.setWalletsSort(wallerIds)
    }
}