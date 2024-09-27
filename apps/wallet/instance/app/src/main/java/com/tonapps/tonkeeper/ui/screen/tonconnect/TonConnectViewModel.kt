package com.tonapps.tonkeeper.ui.screen.tonconnect

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.proof.TONProof
import com.tonapps.tonkeeper.manager.tonconnect.TonConnect
import com.tonapps.tonkeeper.manager.tonconnect.TonConnectManager
import com.tonapps.tonkeeper.manager.tonconnect.bridge.JsonBuilder
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.usecase.sign.SignUseCase
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

class TonConnectViewModel(
    app: Application,
    private val accountRepository: AccountRepository,
    private val signUseCase: SignUseCase
): BaseWalletVM(app) {

    private val _stateFlow = MutableStateFlow<TonConnectScreenState?>(null)
    val stateFlow = _stateFlow.asStateFlow().filterNotNull()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val wallet = accountRepository.selectedWalletFlow.firstOrNull() ?: run {
                _stateFlow.value = TonConnectScreenState.Failure
                return@launch
            }

            val wallets = accountRepository.getWallets().filter { it.isTonConnectSupported }

            if (wallets.isEmpty()) {
                _stateFlow.value = TonConnectScreenState.Failure
                return@launch
            }

            val walletsCount = wallets.size

            _stateFlow.value = TonConnectScreenState.Data(
                wallet = if (wallet.isTonConnectSupported) wallet else wallets.first(),
                hasWalletPicker = walletsCount > 1
            )
        }
    }

    suspend fun requestProof(
        wallet: WalletEntity,
        app: AppEntity,
        proofPayload: String
    ) = signUseCase(context, wallet, app.url.host!!, proofPayload)

    fun setWallet(wallet: WalletEntity) {
        val state = _stateFlow.value as? TonConnectScreenState.Data ?: return
        _stateFlow.value = state.copy(wallet = wallet)
    }
}