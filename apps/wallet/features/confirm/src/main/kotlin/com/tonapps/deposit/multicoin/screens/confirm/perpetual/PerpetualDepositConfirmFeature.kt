package com.tonapps.deposit.multicoin.screens.confirm.perpetual

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.chainkit.core.chain.model.num.FiatRate
import com.tonapps.chainkit.core.chain.model.num.toDisplayUnit
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.SignatureGate
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.BaseOperationConfirmFeature
import com.tonapps.chainkit.core.op.Intent
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationAuthorizer
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationIntent
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationRequote
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationTracker
import com.tonapps.log.L
import com.tonapps.time.TimeProvider
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.tonCoinAssetId
import com.tonapps.wallet.data.multichain.tx.PostTransactionRefreshSchedule
import com.tonapps.wallet.data.passcode.WalletUnlocker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The perps side of a deposit's send: the funding journal and the deposit's registration. */
interface PerpetualDepositTracker : OperationTracker {

    fun onError(error: ConfirmationError)

    fun onQuoteChanged()
}

/** The perps side of a deposit [com.tonapps.blockchain.model.CommonTransactionData.preview]: the deposit, once the wallet is unlocked. */
interface PerpetualDepositAuthorizer {

    /** Signs the preview [request] carries and returns the deposit quoted with those signatures. */
    suspend fun authorize(request: ConfirmRequest, wallet: MnemonicWallet): ConfirmRequest

    suspend fun requote(request: ConfirmRequest): ConfirmRequest
}

/**
 * Confirm feature for [ConfirmType.PerpetualDeposit]: signs the operation the deposit was quoted
 * with, as it is, until the quote expires. A preview is priced as its own operation — signatures,
 * nothing to broadcast — and on the swipe they make the deposit that is signed and sent instead.
 */
class PerpetualDepositConfirmFeature(
    request: ConfirmRequest,
    provider: ChainKitProvider,
    accountRepo: McAccountRepository,
    walletUnlocker: WalletUnlocker,
    signatureGate: SignatureGate,
    postTransactionRefreshSchedule: PostTransactionRefreshSchedule,
    timeProvider: TimeProvider,
    tracker: PerpetualDepositTracker?,
    authorizer: PerpetualDepositAuthorizer,
    private val depositFeeMethods: PerpetualDepositFeeMethods,
) : BaseOperationConfirmFeature(
    request = request,
    provider = provider,
    accountRepo = accountRepo,
    walletUnlocker = walletUnlocker,
    signatureGate = signatureGate,
    postTransactionRefreshSchedule = postTransactionRefreshSchedule,
    intent = DepositIntent,
    timeProvider = timeProvider,
    tracker = tracker,
    // Only a preview is signed into another deposit; a deposit quoted as it is is signed as it is.
    authorizer = OperationAuthorizer { quoted, wallet ->
        if (quoted.data.preview) {
            authorizer.authorize(quoted, wallet)
        } else {
            quoted
        }
    },
    relayedFeeMethods = depositFeeMethods,
    requote = OperationRequote { quoted -> authorizer.requote(quoted) },
) {

    val deposit: StateFlow<ConfirmType.PerpetualDeposit> = quoted
        .map { it.type as ConfirmType.PerpetualDeposit }
        .cacheState(initialValue = request.type as ConfirmType.PerpetualDeposit)

    val delegatesAddress: Boolean = request.data.preview

    val sourceAccount: StateFlow<AccountWithDetails?> = account
        .map { priced -> priced ?: findSourceAccount() }
        .cacheState(initialValue = null)

    val pricing: StateFlow<PerpetualDepositPricing?> = combine(fee, deposit) { priced, quotedDeposit ->
        priced?.let { price(it, quotedDeposit) }
    }
        .cacheState(initialValue = null)

    init {
        if (tracker != null) {
            stateScope.launch {
                confirmationError.filterNotNull().collect { tracker.onError(it) }
            }
            stateScope.launch {
                quoteChanged.filter { it }.collect { tracker.onQuoteChanged() }
            }
        }
    }

    private suspend fun price(fee: Fee, deposit: ConfirmType.PerpetualDeposit): PerpetualDepositPricing {
        val networkFeeUsd = feeUsdRate(fee.asset)?.let { rate ->
            fee.asset.decimals.toDisplayUnit(fee.amount).value * rate
        }
        val spentUsd = findUsdAccount(request.data.assetId)?.let { source ->
            source.usdRate()?.let { rate -> source.asset.value.toDisplayUnit(deposit.amount).value * rate.value }
        }
        return perpetualDepositPricing(
            feeSymbol = fee.asset.symbol,
            networkFeeUsd = networkFeeUsd,
            providerFees = deposit.providerFees,
            spentUsd = spentUsd,
        )
    }

    private suspend fun findUsdRate(assetId: String): FiatRate? = findUsdAccount(assetId)?.usdRate()

    private suspend fun feeUsdRate(asset: Asset): BigDecimal? {
        if (asset !is Asset.Battery) {
            return findUsdRate(asset.id)?.value
        }
        val tonUsd = findUsdRate(tonCoinAssetId(testnet = false)) ?: return null
        val chargeCost = depositFeeMethods.chargeCostInTon() ?: return null
        return tonUsd.value * chargeCost
    }

    private suspend fun findUsdAccount(assetId: String): AccountWithDetails? {
        return try {
            accountRepo.findAccount(request.data.walletId, assetId, currencyOverride = USD_CURRENCY)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e(e)
            null
        }
    }

    private fun AccountWithDetails.usdRate(): FiatRate? {
        return rate?.takeIf { it.currencyCode.equals(USD_CURRENCY, ignoreCase = true) }?.value
    }

    private suspend fun findSourceAccount(): AccountWithDetails? {
        return try {
            accountRepo.findAccount(request.data.walletId, request.data.assetId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e(e)
            null
        }
    }
}

private const val USD_CURRENCY = "USD"

// What the user chose on the deposit screen: the source and the amount entered. Coin the operation carries
// beside a token is not listed: TON's gas is the validator's own allowance, and elsewhere it is a spend.
private val DepositIntent = OperationIntent { request, account ->
    val deposit = request.type as ConfirmType.PerpetualDeposit
    Intent(spends = listOf(Intent.Spend(account.asset, deposit.amount)), to = null, source = Intent.Source.Wallet)
}
