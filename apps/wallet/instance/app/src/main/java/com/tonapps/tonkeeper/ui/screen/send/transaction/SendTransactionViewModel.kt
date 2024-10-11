package com.tonapps.tonkeeper.ui.screen.send.transaction

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.icu.Coins
import com.tonapps.ledger.ton.Transaction
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.extensions.getTransfers
import com.tonapps.tonkeeper.manager.tx.TransactionManager
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.usecase.emulation.EmulationUseCase
import com.tonapps.tonkeeper.usecase.sign.SignUseCase
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.SendBlockchainState
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.MessageBodyEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.battery.BatteryRepository
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
import org.ton.contract.wallet.WalletTransfer
import java.util.concurrent.atomic.AtomicBoolean

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
    private val transactionManager: TransactionManager,
    private val batteryRepository: BatteryRepository
) : BaseWalletVM(app) {

    private val currency = settingsRepository.currency
    private val isBattery = AtomicBoolean(false)

    private val _stateFlow = MutableStateFlow<SendTransactionState>(SendTransactionState.Loading)
    val stateFlow = _stateFlow.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tokens = getTokens()
                val transfers = transfers(tokens.filter { it.isCompressed }, true)
                val message = accountRepository.messageBody(wallet, request.validUntil, transfers)
                val emulated = emulationUseCase(
                    message = message,
                    useBattery = settingsRepository.batteryIsEnabledTx(wallet.accountId, batteryTransactionType),
                    forceRelayer = forceRelayer,
                    params = true
                )
                isBattery.set(emulated.withBattery)

                val details = historyHelper.create(wallet, emulated)

                val totalFormatBuilder = StringBuilder(getString(Localization.total, emulated.totalFormat))
                if (emulated.nftCount > 0) {
                    totalFormatBuilder.append(" + ").append(emulated.nftCount).append(" NFT")
                }

                val jettonsAddress = emulated.consequences.risk.jettons.map { it.jetton.address }
                val sendTokens = tokens.filter { it.balance.token.address in jettonsAddress }
                val hasCompressedJetton = sendTokens.any { it.isCompressed }

                val tonBalance = getTONBalance()
                val transferAmount = EmulationUseCase.calculateTransferAmount(transfers)
                var transferFee = (if (!emulated.extra.isRefund) {
                    Coins.ZERO
                } else {
                    emulated.extra.value
                }) + Coins.of(0.05)

                if (hasCompressedJetton) {
                    transferFee += Coins.of(0.1)
                } else if (sendTokens.size > 1) {
                    transferFee += Coins.of(0.05)
                }

                val transferTotal = transferAmount + transferFee

                if (!emulated.withBattery && transferTotal > tonBalance) {
                    _stateFlow.value = SendTransactionState.InsufficientBalance(
                        wallet = wallet,
                        balance = tonBalance,
                        required = transferTotal,
                        withRechargeBattery = false,
                        singleWallet = isSingleWallet()
                    )
                } else {
                    _stateFlow.value = SendTransactionState.Details(
                        emulated = details,
                        totalFormat = totalFormatBuilder.toString(),
                        isDangerous = emulated.total.isDangerous,
                        nftCount = emulated.nftCount
                    )
                }
            } catch (e: Throwable) {
                val tonBalance = getTONBalance()
                if (tonBalance == Coins.ZERO) {
                    _stateFlow.value = SendTransactionState.InsufficientBalance(
                        wallet = wallet,
                        balance = tonBalance,
                        required = Coins.of(0.1),
                        withRechargeBattery = false,
                        singleWallet = isSingleWallet()
                    )
                } else {
                    toast(Localization.unknown_error)
                    _stateFlow.value = SendTransactionState.FailedEmulation
                }
            }
        }
    }

    private suspend fun isSingleWallet(): Boolean {
        return 1 >= accountRepository.getWallets().size
    }

    private suspend fun getTONBalance(): Coins {
        val balance = tokenRepository.getTON(settingsRepository.currency, wallet.accountId, wallet.testnet)?.balance?.value
        return balance ?: Coins.ZERO
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
        val isBattery = isBattery.get()
        val compressedTokens = getTokens().filter { it.isCompressed }
        val transfers = transfers(compressedTokens, false)
        val message = accountRepository.messageBody(wallet, request.validUntil, transfers)
        val unsignedBody = message.createUnsignedBody(isBattery)
        val ledgerTransaction = getLedgerTransaction(message)

        val boc = signUseCase(
            context = context,
            wallet = wallet,
            unsignedBody = unsignedBody,
            seqNo = message.seqNo,
            ledgerTransaction = ledgerTransaction
        ).base64()

        val status = transactionManager.send(wallet, boc, isBattery)
        if (status == SendBlockchainState.SUCCESS) {
            emit(boc)
        } else {
            throw IllegalStateException("Failed to send transaction to blockchain: $status")
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun transfers(compressedTokens: List<AccountTokenEntity>, forEmulation: Boolean): List<WalletTransfer> {
        val excessesAddress = if (!forEmulation && isBattery.get()) {
            batteryRepository.getConfig(wallet.testnet).excessesAddress
        } else null

        return request.getTransfers(
            wallet = wallet,
            compressedTokens = compressedTokens,
            excessesAddress = excessesAddress,
            api = api
        )
    }

    private suspend fun getTokens(): List<AccountTokenEntity> {
        return tokenRepository.get(currency, wallet.accountId, wallet.testnet, true) ?: emptyList()
    }

}