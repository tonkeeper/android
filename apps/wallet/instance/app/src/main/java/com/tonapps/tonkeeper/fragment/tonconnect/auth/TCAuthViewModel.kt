package com.tonapps.tonkeeper.fragment.tonconnect.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.core.tonconnect.models.TCData
import com.tonapps.tonkeeper.password.PasscodeRepository
import com.tonapps.wallet.data.push.GooglePushService
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.tonconnect.TonConnectRepository
import com.tonapps.wallet.data.tonconnect.entities.DAppRequestEntity
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppEventSuccessEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import uikit.extensions.collectFlow

class TCAuthViewModel(
    private val request: DAppRequestEntity,
    private val accountRepository: AccountRepository,
    private val passcodeRepository: PasscodeRepository,
    private val tonConnectRepository: TonConnectRepository,
): ViewModel() {

    private val _dataState = MutableStateFlow<TCData?>(null)
    val dataState = _dataState.asStateFlow().filterNotNull()

    init {
        collectFlow(accountRepository.selectedWalletFlow, ::requestData)
    }

    private fun requestData(wallet: WalletEntity) {
        viewModelScope.launch {
            val manifest = tonConnectRepository.getManifest(request.payload.manifestUrl) ?: return@launch

            val data = TCData(
                manifest = manifest,
                accountId = wallet.accountId,
                clientId = request.id,
                items = request.payload.items,
                testnet = wallet.testnet,
            )

            _dataState.tryEmit(data)
        }
    }

    private fun passcode(context: Context) = passcodeRepository.confirmationFlow(context)

    private fun wallet(context: Context) = passcode(context).combine(accountRepository.selectedWalletFlow) { _, wallet ->
        wallet
    }.take(1)

    fun connect(
        context: Context,
        allowPush: Boolean
    ): Flow<DAppEventSuccessEntity> = wallet(context).combine(dataState) { wallet, data ->
        val privateKey = accountRepository.getPrivateKey(wallet.id)
        val firebaseToken = if (allowPush) {
            GooglePushService.requestToken()
        } else {
            null
        }
        tonConnectRepository.connect(wallet, privateKey, data.manifest, data.clientId, data.items, firebaseToken)
    }.take(1).flowOn(Dispatchers.IO)


}