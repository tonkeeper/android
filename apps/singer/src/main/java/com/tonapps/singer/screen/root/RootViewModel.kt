package com.tonapps.singer.screen.root

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.tonapps.singer.core.DeepLink
import com.tonapps.singer.core.account.AccountRepository
import com.tonapps.singer.screen.root.action.RootAction
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow

class RootViewModel(
    private val accountRepository: AccountRepository
): ViewModel() {

    val initialized: Boolean
        get() = accountRepository.keysEntityFlow.value != null

    val hasKeys: Flow<Boolean>
        get() = accountRepository.keysEntityFlow.filterNotNull().map {
            it.isNotEmpty()
        }.distinctUntilChanged()

    private val _modeFlow = MutableStateFlow<RootMode>(RootMode.Default)
    val modeFlow = _modeFlow.asStateFlow()

    private val _action = Channel<RootAction>(Channel.BUFFERED)
    val action = _action.receiveAsFlow()

    fun openKey(id: Long) {
        val mode = modeFlow.value
        if (mode is RootMode.Default) {
            _action.trySend(RootAction.KeyDetails(id))
        } else if (mode is RootMode.Select) {
            _action.trySend(RootAction.RequestBodySign(id, mode.body, mode.qr))
        }
    }

    fun setMode(mode: RootMode) {
        _modeFlow.value = mode
    }

    fun responseSignedBoc(boc: String) {
        _action.trySend(RootAction.ResponseBoc(boc))

        setMode(RootMode.Default)
    }

    fun processUri(uri: Uri, qr: Boolean) {
        if (!DeepLink.isSupported(uri)) {
            return
        }

        val body = uri.getQueryParameter("body") ?: return
        _modeFlow.value = RootMode.Select(body, qr)
    }

}