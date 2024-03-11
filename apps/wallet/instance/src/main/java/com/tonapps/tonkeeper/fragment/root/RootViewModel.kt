package com.tonapps.tonkeeper.fragment.root

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.core.deeplink.DeepLink
import com.tonapps.tonkeeper.fragment.wallet.history.HistoryScreen
import com.tonapps.tonkeeper.password.PasscodeRepository
import com.tonapps.tonkeeper.ui.screen.init.InitScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.WalletRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import uikit.base.BaseFragment

class RootViewModel(
    private val passcodeRepository: PasscodeRepository,
    private val walletRepository: WalletRepository
): ViewModel() {

    val hasWalletFlow = walletRepository.walletsFlow.map { it.isNotEmpty() }

    private val _addFragmentAction = Channel<BaseFragment>(Channel.BUFFERED)
    val addFragmentAction = _addFragmentAction.receiveAsFlow()

    private val _openTabAction = Channel<Int>(Channel.BUFFERED)
    val openTabAction = _openTabAction.receiveAsFlow()

    fun processDeepLink(uri: Uri): Boolean {
        if (DeepLink.isSupportedUri(uri)) {
            resolveDeepLink(uri)
            return true
        }
        return false
    }

    private fun resolveDeepLink(uri: Uri) {
        hasWalletFlow.onEach {
            if (uri.host == "signer") {
                resolveSignerLink(uri)
            } else {
                resolveOther(uri)
            }
        }.launchIn(viewModelScope)
    }

    private fun resolveSignerLink(uri: Uri) {
        // val fragment = InitScreen.singer(uri) ?: return
       //  _addFragmentAction.trySend(fragment)
    }

    private fun resolveOther(uri: Uri) {
        val url = uri.toString()
        if (url == HistoryScreen.DeepLink) {
            _openTabAction.trySend(R.id.activity)
            return
        }
        Log.d("RootViewModelLog", "Unknown deeplink: $uri")
    }
}