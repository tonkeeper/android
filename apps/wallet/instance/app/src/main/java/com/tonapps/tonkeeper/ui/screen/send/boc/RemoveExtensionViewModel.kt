package com.tonapps.tonkeeper.ui.screen.send.boc

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.icu.Coins
import com.tonapps.blockchain.model.legacy.Amount
import com.tonapps.blockchain.model.legacy.TransferEntity
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.wallet.data.tx.TransactionManager
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.blockchain.model.legacy.errors.InsufficientBalanceType
import com.tonapps.deposit.screens.send.state.SendFee
import com.tonapps.tonkeeper.ui.screen.send.transaction.SendTransactionState
import com.tonapps.deposit.usecase.emulation.Emulated
import com.tonapps.deposit.usecase.emulation.Emulated.Companion.buildFee
import com.tonapps.deposit.usecase.emulation.EmulationUseCase
import com.tonapps.deposit.usecase.emulation.InsufficientBalanceError
import com.tonapps.deposit.usecase.sign.SignUseCase
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.getDebugMessage
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import org.ton.block.AddrStd
import org.ton.cell.CellBuilder
import org.ton.contract.wallet.MessageData
import org.ton.contract.wallet.WalletTransferBuilder
import java.util.concurrent.atomic.AtomicLong

class RemoveExtensionViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val pluginAddress: String,
    private val accountRepository: AccountRepository,
    private val tokenRepository: TokenRepository,
    private val settingsRepository: SettingsRepository,
    private val signUseCase: SignUseCase,
    private val api: API,
    private val historyHelper: HistoryHelper,
    private val emulationUseCase: EmulationUseCase,
    private val transactionManager: TransactionManager,
    private val batteryRepository: BatteryRepository,
    private val ratesRepository: RatesRepository,
) : BaseWalletVM(app) {

    private val _stateFlow = MutableStateFlow<SendTransactionState>(SendTransactionState.Loading)
    val stateFlow = _stateFlow.asStateFlow()

    private val emulationReadyDate = AtomicLong(0)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val seqNo = accountRepository.getSeqno(wallet)
                val validUntil = accountRepository.getValidUntil(wallet.network)
                val queryId = TransferEntity.newWalletQueryId()

                val address = AddrStd(pluginAddress)
                val forwardAmount = org.ton.block.Coins.of(0.05)

                val unsignedBody = wallet.contract.removePlugin(
                    seqNo = seqNo,
                    validUntil = validUntil,
                    queryId = queryId,
                    forwardAmount = forwardAmount,
                    pluginAddress = address
                )

                val actionInternal = WalletTransferBuilder().apply {
                    destination = address
                    bounceable = true
                    coins = forwardAmount
                    messageData = MessageData.raw(CellBuilder().build(), null)
                }.build()

                val emulated = emulationUseCase(
                    wallet = wallet,
                    seqNo = seqNo,
                    unsignedBody = unsignedBody,
                    outMsgs = wallet.contract.getOutMsgs(gifts = arrayOf(actionInternal)),
                    forwardAmount = Coins.of(forwardAmount.toString()),
                )

                emulationReadyDate.set(System.currentTimeMillis())

                val error = emulated.error
                if (emulated.failed && error is InsufficientBalanceError) {
                    _stateFlow.value = SendTransactionState.InsufficientBalance(
                        wallet = wallet,
                        balance = Amount(error.accountBalance),
                        required = Amount(error.totalAmount),
                        withRechargeBattery = false,
                        singleWallet = isSingleWallet(),
                        type = InsufficientBalanceType.InsufficientTONBalance
                    )

                    return@launch
                }

                _stateFlow.value = createDetails(emulated)
            } catch (e: Throwable) {
                val tonBalance = getTONBalance()
                if (tonBalance == Coins.ZERO) {
                    _stateFlow.value = SendTransactionState.InsufficientBalance(
                        wallet = wallet,
                        balance = Amount(tonBalance),
                        required = Amount(Coins.of(0.1)),
                        withRechargeBattery = false,
                        singleWallet = isSingleWallet(),
                        type = InsufficientBalanceType.InsufficientTONBalance
                    )
                } else {
                    toast(e.getDebugMessage() ?: getString(Localization.unknown_error))
                    _stateFlow.value = SendTransactionState.FailedEmulation
                }
            }
        }
    }

    private suspend fun createDetails(emulated: Emulated): SendTransactionState.Details {
        val fee: SendFee =
            emulated.buildFee(wallet, api, accountRepository, batteryRepository, ratesRepository)

        val details = historyHelper.create(wallet, emulated, fee)
        val totalFormatBuilder = StringBuilder(getString(Localization.total, emulated.totalFormat))
        if (emulated.nftCount > 0) {
            totalFormatBuilder.append(" + ").append(emulated.nftCount).append(" NFT")
        }

        return SendTransactionState.Details(
            emulated = details,
            totalFormat = if (emulated.failed) getString(Localization.unknown) else totalFormatBuilder.toString(),
            isDangerous = emulated.total.isDangerous,
            nftCount = emulated.nftCount,
            failed = emulated.failed,
            fee = fee,
        )
    }

    private suspend fun isSingleWallet(): Boolean {
        return 1 >= accountRepository.getWallets().size
    }

    private suspend fun getTONBalance(): Coins {
        val balance = tokenRepository.getTON(
            settingsRepository.currency,
            wallet.accountId,
            wallet.network
        )?.balance?.value
        return balance ?: Coins.ZERO
    }

    fun send() = flow {
        val seqNo = accountRepository.getSeqno(wallet)
        val validUntil = accountRepository.getValidUntil(wallet.network)
        val queryId = TransferEntity.newWalletQueryId()

        val unsignedBody = wallet.contract.removePlugin(
            seqNo = seqNo,
            validUntil = validUntil,
            queryId = queryId,
            forwardAmount = org.ton.block.Coins.of(0.05),
            pluginAddress = AddrStd(pluginAddress)
        )

        val cell = signUseCase(
            context = context,
            wallet = wallet,
            unsignedBody = unsignedBody,
            ledgerTransaction = null,
            seqNo = seqNo
        )

        val confirmationTimeSeconds = getConfirmationTimeMillis() / 1000.0

        transactionManager.send(
            wallet = wallet,
            boc = cell,
            withBattery = false,
            source = "local",
            confirmationTime = confirmationTimeSeconds
        )
        emit(cell.base64())
    }.flowOn(Dispatchers.IO)

    private fun getConfirmationTimeMillis(): Long {
        return emulationReadyDate.get() - System.currentTimeMillis()
    }

}


