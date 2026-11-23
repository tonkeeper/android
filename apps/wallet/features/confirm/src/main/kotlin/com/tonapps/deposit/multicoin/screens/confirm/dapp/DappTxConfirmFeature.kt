package com.tonapps.deposit.multicoin.screens.confirm.dapp

import com.tonapps.blockchain.model.ConfirmContext
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.core.op.Intent
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.core.flags.WalletFeature
import com.tonapps.deposit.multicoin.screens.confirm.ConfirmAnalytics
import com.tonapps.deposit.multicoin.screens.confirm.ConfirmResultListener
import com.tonapps.deposit.multicoin.screens.confirm.base.SendingState
import com.tonapps.deposit.multicoin.screens.confirm.base.SignatureGate
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.BaseOperationConfirmFeature
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationIntent
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationSpending
import com.tonapps.log.L
import com.tonapps.time.TimeProvider
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.tx.PostTransactionRefreshSchedule
import com.tonapps.wallet.data.passcode.WalletUnlocker
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Whether [request] confirms on [DappTxConfirmScreen]: a dapp's EVM transaction (eth_sendTransaction,
 * eth_signTransaction), while the flag is on.
 */
fun ConfirmRequest.confirmsAsDappOperation(): Boolean {
    val type = type as? ConfirmType.Operational ?: return false
    if (type !is ConfirmType.Transfer && type !is ConfirmType.Call) {
        return false
    }
    return context is ConfirmContext.Dapp &&
        type.operation.payloads.all { it.meta is PayloadMeta.Evm.Main } &&
        WalletFeature.OperationDapp.isEnabled
}

/**
 * Confirm feature for a dapp's EVM transaction (eth_sendTransaction, eth_signTransaction): signs the
 * operation as the dapp sent it and answers the dapp with the hash, or with the signed transaction.
 */
class DappTxConfirmFeature(
    request: ConfirmRequest,
    provider: ChainKitProvider,
    accountRepo: McAccountRepository,
    walletUnlocker: WalletUnlocker,
    signatureGate: SignatureGate,
    postTransactionRefreshSchedule: PostTransactionRefreshSchedule,
    timeProvider: TimeProvider,
    confirmResults: ConfirmResultListener,
) : BaseOperationConfirmFeature(
    request = request,
    provider = provider,
    accountRepo = accountRepo,
    walletUnlocker = walletUnlocker,
    signatureGate = signatureGate,
    postTransactionRefreshSchedule = postTransactionRefreshSchedule,
    intent = DappIntent,
    timeProvider = timeProvider,
    spending = OperationSpending { screen, account -> screen.transaction(account)?.amount },
    confirmResults = confirmResults,
) {

    val dapp: ConfirmContext.Dapp = request.context as? ConfirmContext.Dapp
        ?: throw IllegalArgumentException("Only a dapp's transaction is confirmed here")

    val isCall: Boolean = request.type is ConfirmType.Call

    private val analytics = ConfirmAnalytics(request)

    /** What the operation does for the account it leaves: a transfer, or a contract call. */
    val transaction: StateFlow<Transaction?> = account
        .map { position -> position?.let { request.transaction(it.value) } }
        .cacheState(initialValue = null)

    init {
        // Only a broadcast transaction is reported as sent; a signature the dapp sends itself is not.
        if (!request.action.isSignOnly) {
            bgScope.launch {
                sendingState.first { it == SendingState.Done }
                transaction.value?.let { analytics.transactionSent(it, fee = null, quote = null, isHardware = false, ledgerModel = null) }
            }
        }
    }
}

// A dapp's request is listed no amounts: what the screen shows is read off the request itself, which
// holds it to nothing. Nothing it moves is then held to a limit, and every other check stands.
// TODO: list what the request emulates to, once the screen shows the emulation.
private val DappIntent = OperationIntent { _, _ ->
    Intent(spends = null, to = null, source = Intent.Source.DApp)
}

// A token transfer is signed as the token contract's call: the transfer is what that call carries.
private fun ConfirmRequest.transaction(account: Account): Transaction? {
    val type = type as ConfirmType.Operational
    val tokenTransfer = (type as? ConfirmType.Transfer)?.tokenTransfer
    if (tokenTransfer != null) {
        return Transaction.Transfer(
            account = account,
            amount = tokenTransfer.amount,
            isMax = false,
            to = Address.force(tokenTransfer.to, account.chain),
        )
    }

    val bound = type.operation.transaction(account)
    bound.error?.let { L.e(it) }
    return bound.getOrNull()
}
