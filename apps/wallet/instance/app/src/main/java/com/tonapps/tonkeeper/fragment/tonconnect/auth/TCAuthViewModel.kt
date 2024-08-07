package com.tonapps.tonkeeper.fragment.tonconnect.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.tonkeeper.core.tonconnect.models.TCData
import com.tonapps.tonkeeper.extensions.signLedgerProof
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.ProofDomainEntity
import com.tonapps.wallet.data.account.entities.ProofEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.push.GooglePushService
import com.tonapps.wallet.data.tonconnect.TonConnectRepository
import com.tonapps.wallet.data.tonconnect.entities.DAppItemEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppRequestEntity
import com.tonapps.wallet.data.tonconnect.entities.DConnectEntity
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppEventSuccessEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import org.ton.crypto.base64
import uikit.extensions.collectFlow

class TCAuthViewModel(
    private val request: DAppRequestEntity,
    private val accountRepository: AccountRepository,
    private val passcodeManager: PasscodeManager,
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

    private fun passcode(context: Context) = passcodeManager.confirmationFlow(context, context.getString(Localization.app_name))

    @OptIn(ExperimentalCoroutinesApi::class)
    fun connect(
        context: Context,
        allowPush: Boolean,
        type: DConnectEntity.Type,
    ): Flow<DAppEventSuccessEntity> = accountRepository.selectedWalletFlow.flatMapLatest { wallet ->
        if (wallet.isLedger) {
            flowOf(wallet)
        } else {
            passcode(context).map { wallet }
        }
    }.combine(dataState) { wallet, data ->
        val firebaseToken = if (allowPush) {
            GooglePushService.requestToken()
        } else {
            null
        }

        if (wallet.isLedger) {
            val proofItem = data.items.find { it.name == DAppItemEntity.TON_PROOF }

            var proof: ProofEntity? = null

            if (proofItem != null) {
                val domain = ProofDomainEntity(data.host!!)
                val timestamp = System.currentTimeMillis() / 1000L
                val signature = context.signLedgerProof(domain.value, timestamp.toBigInteger(), proofItem.payload ?: "", wallet.id)

                if (signature == null) throw Exception("Failed to sign proof")

                proof = ProofEntity(
                    timestamp = timestamp,
                    domain = domain,
                    payload = proofItem.payload ?: "",
                    signature = base64(signature),
                    stateInit = wallet.contract.getStateCell().base64(),
                )
            }

            tonConnectRepository.connectLedger(wallet, data.manifest, data.clientId, data.items, firebaseToken, type, proof)
        } else {
            val privateKey = accountRepository.getPrivateKey(wallet.id)
            tonConnectRepository.connect(wallet, privateKey, data.manifest, data.clientId, data.items, firebaseToken, type)
        }
    }.take(1).flowOn(Dispatchers.IO)


}