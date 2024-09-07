package com.tonapps.tonkeeper.ui.screen.main

import android.app.Application
import androidx.lifecycle.ViewModel
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.Wallet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import uikit.extensions.collectFlow

class MainViewModel(
    app: Application,
    private val accountRepository: AccountRepository,
    private val api: API,
) : BaseWalletVM(app) {


    private val _childBottomScrolled = MutableEffectFlow<Boolean>()
    val childBottomScrolled = _childBottomScrolled.asSharedFlow()

    val selectedWalletFlow = accountRepository.selectedWalletFlow

    fun setBottomScrolled(value: Boolean) {
        _childBottomScrolled.tryEmit(value)
    }
}