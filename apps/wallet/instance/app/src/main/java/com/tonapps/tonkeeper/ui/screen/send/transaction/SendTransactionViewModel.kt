package com.tonapps.tonkeeper.ui.screen.send.transaction

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.ledger.ton.Transaction
import com.tonapps.security.base64
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.extensions.getTransfers
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.usecase.emulation.EmulationUseCase
import com.tonapps.tonkeeper.usecase.sign.SignUseCase
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.SendBlockchainState
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.MessageBodyEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import com.tonapps.wallet.data.settings.BatteryTransaction
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class SendTransactionViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val request: SignRequestEntity,
    private val batteryTransactionType: BatteryTransaction,
    private val forceRelayer: Boolean,
    private val accountRepository: AccountRepository,
    private val tokenRepository: TokenRepository,
    private val settingsRepository: SettingsRepository,
    private val signUseCase: SignUseCase,
    private val api: API,
    private val historyHelper: HistoryHelper,
    private val emulationUseCase: EmulationUseCase,
) : BaseWalletVM(app) {

    private val currency = settingsRepository.currency

    private val useBattery: Boolean by lazy {
        settingsRepository.batteryIsEnabledTx(wallet.accountId, batteryTransactionType)
    }

    private val _stateFlow = MutableStateFlow<SendTransactionState>(SendTransactionState.Loading)
    val stateFlow = _stateFlow.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val message = messageBody()
                val emulated = emulationUseCase(message, useBattery, forceRelayer)
                val details = historyHelper.create(wallet, emulated)

                val totalFormatBuilder = StringBuilder(getString(Localization.total, emulated.totalFormat))
                if (emulated.nftCount > 0) {
                    totalFormatBuilder.append(" + ").append(emulated.nftCount).append(" NFT")
                }

                _stateFlow.value = SendTransactionState.Details(
                    emulated = details,
                    totalFormat = totalFormatBuilder.toString(),
                    isDangerous = emulated.total.isDangerous,
                    nftCount = emulated.nftCount
                )
            } catch (e: Throwable) {
                _stateFlow.value = SendTransactionState.Failed
            }
        }
    }

    private fun getLedgerTransaction(
        message: MessageBodyEntity
    ): Transaction? {
        if (!message.wallet.isLedger) {
            return null
        }
        if (message.transfers.size > 1) {
            throw IllegalStateException("Ledger does not support multiple messages")
        }
        val transfer = message.transfers.firstOrNull() ?: return null

        return Transaction.fromWalletTransfer(
            walletTransfer = transfer,
            seqno = message.seqNo,
            timeout = message.validUntil
        )
    }

    fun send() = flow {
        val message = messageBody()
        val unsignedBody = message.createUnsignedBody(useBattery || forceRelayer)
        val ledgerTransaction = getLedgerTransaction(message)

        val boc = signUseCase(
            context = context,
            wallet = wallet,
            unsignedBody = unsignedBody,
            seqNo = message.seqNo,
            ledgerTransaction = ledgerTransaction
        ).base64()
        val status = api.sendToBlockchain(boc, wallet.testnet)
        if (status == SendBlockchainState.SUCCESS) {
            emit(boc)
        } else {
            throw IllegalStateException("Failed to send transaction to blockchain: $status")
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun messageBody(): MessageBodyEntity {
        val compressedTokens = getCompressedTokens()
        val transfers = request.getTransfers(wallet, compressedTokens, api = api)
        return accountRepository.messageBody(wallet, request.validUntil, transfers)
    }

    private suspend fun getCompressedTokens(): List<AccountTokenEntity> {
        return tokenRepository.get(currency, wallet.accountId, wallet.testnet)?.filter { it.isCompressed } ?: emptyList()
    }

}