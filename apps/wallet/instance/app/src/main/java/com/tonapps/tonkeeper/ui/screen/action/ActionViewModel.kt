package com.tonapps.tonkeeper.ui.screen.action

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.tonkeeper.password.PasscodeRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.repository.BaseWalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

class ActionViewModel(
    private val args: ActionArgs,
    private val walletRepository: BaseWalletRepository,
    private val passcodeRepository: PasscodeRepository
): ViewModel() {

    private val _walletFlow = MutableStateFlow<WalletEntity?>(null)
    val walletFlow = _walletFlow.asStateFlow().filterNotNull()

    init {
        viewModelScope.launch {
            _walletFlow.value = walletRepository.getWalletById(args.walletId)
        }
    }

    fun sign(context: Context) = passcodeRepository.confirmationFlow(context).combine(walletFlow) { _, wallet ->
        val request = args.request
        val secretKey = walletRepository.getPrivateKey(wallet.id)
        val seqno = walletRepository.getSeqno(wallet)
        val message = walletRepository.createSignedMessage(wallet, seqno, secretKey, request.validUntil, request.transfers)
        message.base64()
    }.flowOn(Dispatchers.IO).take(1)

}