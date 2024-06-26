package com.tonapps.signer.screen.root

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.signer.Key
import com.tonapps.signer.core.repository.KeyRepository
import com.tonapps.signer.deeplink.DeeplinkSource
import com.tonapps.signer.deeplink.entities.ReturnResultEntity
import com.tonapps.signer.deeplink.entities.SignRequestEntity
import com.tonapps.signer.password.Password
import com.tonapps.signer.screen.root.action.RootAction
import com.tonapps.signer.vault.SignerVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import org.ton.cell.Cell

class RootViewModel(
    private val vault: SignerVault,
    private val keyRepository: KeyRepository
): ViewModel() {

    val hasKeys = keyRepository.stream.map {
        it.isNotEmpty()
    }.distinctUntilChanged()

    private val _action = MutableSharedFlow<RootAction>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val action = _action.asSharedFlow()

    fun checkPassword(password: CharArray) = flow {
        val valid = vault.isValidPassword(password)
        if (valid) {
            Password.setUnlock()
        }
        emit(valid)
    }.flowOn(Dispatchers.IO).take(1)

    fun signOut() {
        viewModelScope.launch(Dispatchers.IO) {
            keyRepository.deleteAll()
            vault.deleteAll()
        }
    }

    fun responseSignature(signature: ByteArray) {
        _action.tryEmit(RootAction.ResponseSignature(signature))
    }

    fun processDeepLink(uri: Uri, source: DeeplinkSource): Boolean {
        if (uri.scheme != Key.SCHEME) {
            return false
        }
        if (uri.authority != "v1") { // if no authority, then it's a just open app
            _action.tryEmit(RootAction.UpdateApp)
            return false
        }
        if (uri.query.isNullOrEmpty()) { // if no query, then it's a just open app
            return true
        }

        val returnResult = ReturnResultEntity(source, uri.getQueryParameter("return"))

        val signRequest = SignRequestEntity.safe(uri, returnResult) ?: return false

        signRequest(signRequest)
        return true
    }

    private fun signRequest(signRequest: SignRequestEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = keyRepository.findIdByPublicKey(signRequest.publicKey) ?: return@launch notFoundKey()

            sign(
                id = id,
                body = signRequest.body,
                v = signRequest.v,
                returnResult = signRequest.returnResult,
                seqno = signRequest.seqno,
                network = signRequest.network,
            )
        }
    }

    private fun sign(
        id: Long,
        body: Cell,
        v: String,
        returnResult: ReturnResultEntity,
        seqno: Int,
        network: TonNetwork,
    ) {
        _action.tryEmit(RootAction.RequestBodySign(id, body, v, returnResult, seqno, network))
    }

    private fun notFoundKey() {
        _action.tryEmit(RootAction.NotFoundKey)
    }

    override fun onCleared() {
        super.onCleared()
        keyRepository.closeDatabase()
    }
}