package com.tonapps.tonkeeper.fragment.root

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.core.deeplink.DeepLink
import com.tonapps.tonkeeper.fragment.wallet.history.HistoryScreen
import com.tonapps.tonkeeper.ui.screen.init.InitFragment
import com.tonapps.tonkeeperx.R
import core.extensions.getQuery
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ton.wallet.WalletManager
import uikit.base.BaseFragment

class RootViewModel(
    private val walletManager: WalletManager
): ViewModel() {

    private val _hasWalletFlow = MutableStateFlow<Boolean?>(null)
    val hasWalletFlow = _hasWalletFlow.asStateFlow().filterNotNull()

    private val _changeWalletAction = MutableSharedFlow<Unit>(extraBufferCapacity = Int.MAX_VALUE)
    val changeWallet = _changeWalletAction.asSharedFlow()

    private val _addFragmentAction = Channel<BaseFragment>(Channel.BUFFERED)
    val addFragmentAction = _addFragmentAction.receiveAsFlow()

    private val _openTabAction = Channel<Int>(Channel.BUFFERED)
    val openTabAction = _openTabAction.receiveAsFlow()

    init {
        viewModelScope.launch {
            _hasWalletFlow.value = walletManager.getWalletInfo() != null
        }
    }

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
        val fragment = InitFragment.singer(uri) ?: return
        _addFragmentAction.trySend(fragment)
    }

    private fun resolveOther(uri: Uri) {
        val url = uri.toString()
        if (url == HistoryScreen.DeepLink) {
            _openTabAction.trySend(R.id.activity)
            return
        }
        Log.d("RootViewModelLog", "Unknown deeplink: $uri")
    }

    fun notifyChangeWallet() {
        viewModelScope.launch {
            _changeWalletAction.tryEmit(Unit)
        }
    }
}