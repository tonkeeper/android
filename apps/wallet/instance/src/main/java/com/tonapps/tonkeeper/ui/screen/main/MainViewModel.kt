package com.tonapps.tonkeeper.ui.screen.main

import androidx.lifecycle.ViewModel
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.wallet.data.account.WalletRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map

class MainViewModel(
    private val walletRepository: WalletRepository
): ViewModel() {

    val walletTypeFlow = walletRepository.activeWalletFlow.map {
        it.type
    }

    private val _childBottomScrolled = MutableEffectFlow<Boolean>()
    val childBottomScrolled = _childBottomScrolled.asSharedFlow()

    fun setBottomScrolled(value: Boolean) {
        _childBottomScrolled.tryEmit(value)
    }
}