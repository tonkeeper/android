package com.tonapps.signer.screen.root

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.signer.deeplink.DeepLink
import com.tonapps.signer.SimpleState
import com.tonapps.signer.core.repository.KeyRepository
import com.tonapps.signer.deeplink.entities.SignRequestEntity
import com.tonapps.signer.extensions.isValidCell
import com.tonapps.signer.password.Password
import com.tonapps.signer.screen.root.action.RootAction
import com.tonapps.signer.vault.SignerVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

class RootViewModel(
    private val vault: SignerVault,
    private val keyRepository: KeyRepository
): ViewModel() {

    val hasKeys = keyRepository.stream.map {
        it.isNotEmpty()
    }.distinctUntilChanged()

    private val _action = Channel<RootAction>(Channel.BUFFERED)
    val action = _action.receiveAsFlow()

    fun checkPassword(password: CharArray) = flow {
        val valid = vault.isValidPassword(password)
        if (valid) {
            Password.setUnlock()
        }
        emit(valid)
    }.flowOn(Dispatchers.IO).take(1)

    fun signOut() {
        viewModelScope.launch(Dispatchers.IO) {
            keyRepository.clear()
            vault.clear()
        }
    }

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

        val signRequest = SignRequestEntity.safe(uri) ?: return
        if (!signRequest.body.isValidCell) {
            return
        }

        keyRepository.findIdByPublicKey(signRequest.publicKey).onEach { id ->
            sign(id, signRequest.body, qr)
        }.launchIn(viewModelScope)
    }

    private fun sign(id: Long, body: String, qr: Boolean) {
        _action.trySend(RootAction.RequestBodySign(id, body, qr))
    }

    override fun onCleared() {
        super.onCleared()
        _action.close()
    }
}