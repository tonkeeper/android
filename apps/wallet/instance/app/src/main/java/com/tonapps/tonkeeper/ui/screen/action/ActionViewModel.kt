package com.tonapps.tonkeeper.ui.screen.action

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.ledger.ton.Transaction
import com.tonapps.tonkeeper.core.SendBlockchainException
import com.tonapps.tonkeeper.extensions.getTransfers
import com.tonapps.tonkeeper.extensions.getWalletTransfer
import com.tonapps.tonkeeper.extensions.signLedgerTransaction
import com.tonapps.tonkeeper.manager.tx.TransactionManager
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.send.main.SendException
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.SendBlockchainState
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
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

class ActionViewModel(
    app: Application,
    private val args: ActionArgs,
    private val accountRepository: AccountRepository,
    private val passcodeManager: PasscodeManager,
    private val api: API,
    private val batteryRepository: BatteryRepository,
    private val transactionManager: TransactionManager,
) : BaseWalletVM(app) {

    private val _walletFlow = MutableStateFlow<WalletEntity?>(null)
    val walletFlow = _walletFlow.asStateFlow().filterNotNull()

    init {
        viewModelScope.launch {
            _walletFlow.value = accountRepository.getWalletById(args.walletId)
        }
    }

    private suspend fun byLedger(
        wallet: WalletEntity,
        seqno: Int,
    ): String {
        val transactions = args.request.getTransfers().map { transfer ->
            Transaction.fromWalletTransfer(
                transfer,
                seqno = seqno,
                timeout = args.validUntil
            )
        }

        val contract = wallet.contract
        var boc: String? = null
        for ((index, transaction) in transactions.withIndex()) {
            val isLast = index == transactions.size - 1
            val signedBody = context.signLedgerTransaction(transaction, wallet.id) ?: throw SendException.Cancelled()
            val message = contract.createTransferMessageCell(contract.address, seqno, signedBody)
            boc = message.base64()
            if (!isLast) {
                val state = transactionManager.send(wallet, message, false)
                if (state != SendBlockchainState.SUCCESS) {
                    throw SendBlockchainException.fromState(state)
                }
            }
        }

        return boc ?: throw SendException.UnableSendTransaction()
    }

    private suspend fun byDefault(
        wallet: WalletEntity,
        seqno: Int,
    ): String {
        val secretKey = accountRepository.getPrivateKey(wallet.id)
        val excessesAddress = if (args.isBattery) {
            batteryRepository.getConfig(wallet.testnet).excessesAddress
        } else null

        val transfers = args.messages.map { it.getWalletTransfer(excessesAddress) }

        val message = accountRepository.createSignedMessage(
            wallet,
            seqno,
            secretKey,
            args.validUntil,
            transfers,
            internalMessage = args.isBattery
        )
        return message.base64()
    }

    fun sign(context: Context) = walletFlow.take(1).map { wallet ->
        if (!wallet.isLedger && !passcodeManager.confirmation(context, context.getString(Localization.app_name))) {
            throw SendException.Cancelled()
        }
        val seqno = accountRepository.getSeqno(wallet)

        if (wallet.isLedger) {
            byLedger(wallet, seqno)
        } else {
            byDefault(wallet, seqno)
        }
    }.flowOn(Dispatchers.IO)

}