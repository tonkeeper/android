package com.tonapps.singer.screen.root

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.singer.core.DeepLink
import com.tonapps.singer.core.Network
import com.tonapps.singer.core.account.AccountRepository
import com.tonapps.singer.core.deeplink.SignAction
import com.tonapps.singer.screen.root.action.RootAction
import core.extensions.getQuery
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import org.ton.api.pub.PublicKeyEd25519
import org.ton.crypto.base64

class RootViewModel(
    private val accountRepository: AccountRepository
): ViewModel() {

    val hasKeys: Flow<Boolean>
        get() = accountRepository.keysEntityFlow.map {
            it.isNotEmpty()
        }.distinctUntilChanged()

    private val _action = Channel<RootAction>(Channel.BUFFERED)
    val action = _action.receiveAsFlow()

    fun responseSignedBoc(boc: String) {
        _action.trySend(RootAction.ResponseBoc(boc))
    }

    fun processUri(url: String, qr: Boolean) {
        processUri(Uri.parse(url), qr)
    }

    fun processUri(uri: Uri, qr: Boolean) {
        if (!DeepLink.isSupported(uri)) {
            return
        }

        val signAction = SignAction.safe(uri) ?: return

        accountRepository.findIdByPublicKey(signAction.publicKey).onEach { id ->
            sign(id, signAction.body, qr)
        }.launchIn(viewModelScope)
    }

    private fun sign(id: Long, body: String, qr: Boolean) {
        _action.trySend(RootAction.RequestBodySign(id, body, qr))
    }
}