package com.tonapps.deposit.multicoin.screens.confirm.transfer

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.ConfirmContext
import com.tonapps.blockchain.model.ConfirmOperations
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.chainkit.core.chain.model.account.PubKey
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.core.common.getOrElse
import com.tonapps.chainkit.core.op.Intent
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.chainkit.transaction.GasReservePolicy
import com.tonapps.core.flags.InAppReviewManager
import com.tonapps.core.flags.WalletFeature
import com.tonapps.deposit.multicoin.screens.confirm.ConfirmAnalytics
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationErrorCause
import com.tonapps.deposit.multicoin.screens.confirm.base.SendingState
import com.tonapps.deposit.multicoin.screens.confirm.base.SignatureGate
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.BaseOperationConfirmFeature
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationAuthorizer
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationIntent
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationQuote
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationSpending
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.SendData
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.coinValue
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFee
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeBuilder
import com.tonapps.log.L
import com.tonapps.time.TimeProvider
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.SigningWallet
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.tx.PostTransactionRefreshSchedule
import com.tonapps.wallet.data.passcode.WalletUnlocker
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Whether [request] confirms on [TransferConfirmScreen]: the wallet's own send, while the flag is on. */
fun ConfirmRequest.confirmsAsTransferOperation(): Boolean {
    return type is ConfirmType.Transfer &&
        context !is ConfirmContext.Dapp &&
        WalletFeature.OperationTransfer.isEnabled
}

/**
 * Confirm feature for the wallet's own [ConfirmType.Transfer]: the send the screen holds — recipient,
 * amount, comment — built into the `main` its chain signs, held to what the user asked for and signed
 * through the operation API.
 */
class TransferConfirmFeature(
    request: ConfirmRequest,
    provider: ChainKitProvider,
    accountRepo: McAccountRepository,
    walletUnlocker: WalletUnlocker,
    signatureGate: SignatureGate,
    postTransactionRefreshSchedule: PostTransactionRefreshSchedule,
    timeProvider: TimeProvider,
    feeBuilder: TxFeeBuilder,
    settingsRepository: SettingsRepository,
) : BaseOperationConfirmFeature(
    request = request,
    provider = provider,
    accountRepo = accountRepo,
    walletUnlocker = walletUnlocker,
    signatureGate = signatureGate,
    postTransactionRefreshSchedule = postTransactionRefreshSchedule,
    intent = OperationIntent { _, account -> request.askedIntent(account) },
    timeProvider = timeProvider,
    authorizer = TransferAuthorizer(request, provider, accountRepo),
    relayedFeeMethods = TransferFeeMethods(request, feeBuilder, settingsRepository),
    quote = TransferQuote(request, provider, accountRepo),
    spending = TransferSpending(request),
) {

    val transfer = request.type as ConfirmType.Transfer

    private val analytics = ConfirmAnalytics(request)

    /** The send as the user asked for it; null until the sender's position is read. */
    val asked: StateFlow<Transaction.Transfer?> = account
        .map { position -> position?.let { request.asked(it.value) } }
        .cacheState(initialValue = null)

    /** What the recipient gets with the fee method picked; what was asked while no send is prepared. */
    val amount: StateFlow<BigInteger?> = combine(sendData, feeMethod, asked) { data, method, asked ->
        data?.let { spending?.sent(it, method) } ?: asked?.amount
    }
        .cacheState(initialValue = null)

    init {
        bgScope.launch {
            sendingState.first { it == SendingState.Done }
            reportSent(feeMethod.value)
        }
    }

    // An encrypted comment is priced as a stand-in of its size: what a relayer sends is the comment the
    // wallet encrypts once unlocked, priced again. Not reached while [TransferFeeMethods] keeps a TON send,
    // the one send that carries such a comment, from the relayer: its test is ignored until TON is relayed again.
    override fun relaysAuthorized(request: ConfirmRequest): Boolean {
        return super.relaysAuthorized(request) || (transfer.commentEncryptionKey != null && asked.value?.memo != null)
    }

    private fun reportSent(method: TxFee?) {
        try {
            val position = account.value ?: return
            val sent = request.asked(position.value).copy(
                amount = amount.value ?: return,
                isMax = transfer.isMax == true,
            )
            analytics.transactionSent(sent, method, quote = null, isHardware = false, ledgerModel = null)
            InAppReviewManager.onTransactionSent()
        } catch (e: Exception) {
            L.e(e)
        }
    }
}

/** What the send takes of the sender's asset: what the screen asked, or what is left of a whole coin balance. */
internal class TransferSpending(private val screen: ConfirmRequest) : OperationSpending {

    override fun spent(request: ConfirmRequest, account: Account): BigInteger = request.asked(account).amount

    // A whole coin balance the wallet pays the fee from is what is left past it. A relayer pays the
    // fee apart and sends the whole.
    override fun sent(data: SendData, method: TxFee?): BigInteger {
        val asked = screen.asked(data.position.value)
        val drained = (screen.type as ConfirmType.Transfer).isMax == true && data.position.asset.value is Asset.Coin
        if (!drained || method?.viaRelayer == true) {
            return asked.amount
        }

        // The payload states what goes, the balance less the fee once it is known; one that states nothing sends that too.
        val signed = data.operation.payloads.fold(BigInteger.ZERO) { sum, payload -> sum + payload.meta.coinValue() }
        if (signed.isPositive) {
            return signed
        }
        return (data.position.unitBalance.value - data.fee.amount).coerceAtLeast(BigInteger.ZERO)
    }
}

/**
 * The send [screen] holds, built into the `main` its chain signs: a coin payment or the token
 * contract's transfer. A whole coin balance is what is left past the fee, built again once it is known.
 * An encrypted comment is priced and validated as a stand-in of its size; [TransferAuthorizer]
 * encrypts it once the wallet is unlocked.
 */
internal class TransferQuote(
    private val screen: ConfirmRequest,
    private val provider: ChainKitProvider,
    private val accountRepo: McAccountRepository,
) : OperationQuote {

    override suspend fun build(request: ConfirmRequest): ConfirmRequest {
        val asked = request.asked(position(request).value)
        return request.sending(asked, asked.amount)
    }

    override suspend fun fit(built: ConfirmRequest, fee: Fee, relayable: Boolean): ConfirmRequest {
        val position = position(screen)
        val asked = screen.asked(position.value)
        val isMax = (screen.type as ConfirmType.Transfer).isMax == true

        val reserved = provider.calculateGasReserve(
            account = position.value,
            energy = position.data.chainAccount,
            amount = asked.amount,
            isMax = isMax,
            fee = fee,
            policy = GasReservePolicy.DrainOrError,
        ).getOrElse { throw it }

        // A relayer may pay the fee instead: the wallet's coin need not cover it.
        if (reserved.error != null) {
            if (relayable) {
                return built
            }
            throw TransferShortfall(position.shortfall())
        }

        if (!reserved.isAmountAdjusted) {
            return built
        }

        // A payload built again as it was is the one already priced.
        val fitted = screen.sending(asked, reserved.amount)
        return if (fitted.meta == built.meta) {
            built
        } else {
            fitted
        }
    }

    // A send is its asset's own: an SPL transfer names its mint, and chain-kit signs it as the token's account.
    override fun signer(position: AccountWithDetails): Account = position.value

    private suspend fun position(request: ConfirmRequest): AccountWithDetails {
        return accountRepo.findAccount(request.data.walletId, request.data.assetId)
            ?: throw IllegalStateException("Can't find account")
    }

    // [asked] for [amount], as its chain signs it.
    private suspend fun ConfirmRequest.sending(asked: Transaction.Transfer, amount: BigInteger): ConfirmRequest {
        val transfer = type as ConfirmType.Transfer
        val memo = asked.memo
        val key = transfer.commentEncryptionKey?.takeIf { memo != null }
        val meta = provider.transferPayload(
            account = asked.account,
            to = asked.to,
            amount = amount,
            memo = memo.takeIf { key == null },
            isMax = transfer.isMax == true,
        ).getOrElse { throw it }
        if (key == null || memo == null) {
            return carrying(meta)
        }

        // Only a TON payment carries an encrypted comment, as its body: a jetton's body is its transfer.
        val payment = (meta as? PayloadMeta.Ton.Main)?.takeIf { asked.account.asset is Asset.Coin }
            ?: throw IllegalStateException("${asked.account.asset.id} carries no encrypted comment")
        val standIn = provider.tonEmulationPayload(memo.encodeToByteArray().size, asked.account, recipientKey(key))
            .getOrElse { throw it }
        return carrying(payment.copy(body = standIn))
    }

    // A coin send is short on its own balance; a token send on the coin that pays its fee.
    private fun AccountWithDetails.shortfall(): ConfirmationError = when (asset.value) {
        is Asset.Coin -> ConfirmationError.InsufficientBalance
        is Asset.Token, is Asset.Battery -> ConfirmationError.InsufficientNetworkFee(data.chainAccount.asset.symbol)
    }
}

/**
 * Encrypts [screen]'s comment, once the wallet is unlocked, into the body the stand-in held its place
 * in; a send with no comment to encrypt is signed as it is.
 */
internal class TransferAuthorizer(
    private val screen: ConfirmRequest,
    private val provider: ChainKitProvider,
    private val accountRepo: McAccountRepository,
) : OperationAuthorizer {

    override suspend fun authorize(request: ConfirmRequest, wallet: MnemonicWallet): ConfirmRequest {
        val key = (screen.type as ConfirmType.Transfer).commentEncryptionKey ?: return request
        val meta = request.meta as? PayloadMeta.Ton.Main ?: return request
        val position = accountRepo.findAccount(screen.data.walletId, screen.data.assetId)
            ?: throw IllegalStateException("Can't find account")
        if (position.asset.value !is Asset.Coin) {
            return request
        }
        val memo = screen.asked(position.value).memo ?: return request

        val encrypted = provider.encryptTonMemo(memo, position.value, recipientKey(key), SigningWallet.Hd(wallet))
            .getOrElse { throw it }
        return request.carrying(meta.copy(body = encrypted))
    }
}

private val ConfirmRequest.meta
    get() = (type as ConfirmType.Transfer).operation.payloads.single().meta

// This transfer as one payload of [meta].
private fun ConfirmRequest.carrying(meta: PayloadMeta): ConfirmRequest {
    val transfer = type as ConfirmType.Transfer
    val payload = ConfirmOperations.payload(0, transfer.operation.payloads.single().chain, meta)
    return ConfirmRequest(
        data = data,
        type = transfer.copy(operation = ConfirmOperations.build(listOf(payload))),
        action = action,
        context = context,
        initiator = initiator,
        utm = utm,
    )
}

private fun recipientKey(hex: String) = PubKey(defaultHex = hex, segWit = "")

private class TransferShortfall(
    override val confirmationError: ConfirmationError,
) : Exception(confirmationError.toString()), ConfirmationErrorCause

/** The send the user asked for: what the screen's operation transfers, read as [account]'s own asset. */
internal fun ConfirmRequest.asked(account: Account): Transaction.Transfer {
    val transfer = type as? ConfirmType.Transfer
        ?: throw IllegalArgumentException("${type::class.simpleName} is no transfer")
    return transfer.operation.transaction(account).getOrElse { throw it } as? Transaction.Transfer
        ?: throw IllegalStateException("Transfer operation does not bind to a transfer")
}

// What the user agreed to: the amount on the screen of [account]'s asset, to the recipient on it.
private fun ConfirmRequest.askedIntent(account: Account): Intent {
    val asked = asked(account)
    return Intent(listOf(Intent.Spend(account.asset, asked.amount)), asked.to, Intent.Source.Wallet)
}
