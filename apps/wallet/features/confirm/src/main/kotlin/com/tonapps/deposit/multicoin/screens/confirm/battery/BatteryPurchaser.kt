package com.tonapps.deposit.multicoin.screens.confirm.battery

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.op.Intent
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.SignedOperation
import com.tonapps.chainkit.core.op.ValidationError
import com.tonapps.core.flags.BootFlags
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.FindingSeverity
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.severity
import com.tonapps.deposit.multicoin.screens.confirm.fee.GaslessRelayer
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerOutcomeUnknownException
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerQuoteExpiredException
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerRejectedException
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.api.AuthorizationProvider
import com.tonapps.wallet.api.entity.Authorization
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.battery.entity.BatteryPurchaseQuote
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import io.batteryapi.models.GaslessV2SendResponse
import io.infrastructure.ClientException
import kotlinx.coroutines.CancellationException

/** A battery purchase as the battery quoted it: what the confirm screen shows and what the swipe signs. */
class BatteryPurchaseReview(
    val quote: BatteryPurchaseQuote,
    val operation: Operation,
    /** Epoch millis on the battery's clock. */
    val expiresAt: Long,
) {

    fun isExpired(serverNowMs: Long): Boolean = expiresAt - EXPIRY_LEAD_MS <= serverNowMs

    private companion object {
        // Signing and the request to /gasless/send have to land before the battery's deadline.
        const val EXPIRY_LEAD_MS = 5_000L
    }
}

/**
 * Buys the battery with an asset on a chain other than TON: quotes the purchase with
 * `/purchase-battery/emulate`, holds the operation it builds to the purchase, signs it as any gasless
 * operation and sends it to `/gasless/send`.
 */
class BatteryPurchaser(
    private val batteryRepository: BatteryRepository,
    private val provider: ChainKitProvider,
    private val gaslessRelayer: GaslessRelayer,
    private val authorizationProvider: AuthorizationProvider,
    private val isValidationEnabled: () -> Boolean = { BootFlags.isTxValidationEnabled },
) {

    /** The purchase [request] asks for, quoted with the operation to sign. */
    suspend fun quote(request: ConfirmRequest): BatteryPurchaseReview {
        val purchase = request.type as ConfirmType.BatteryPurchase
        val quote = batteryRepository.emulatePurchase(
            walletId = request.data.walletId,
            chainId = purchase.chainId,
            assetId = purchase.batteryAssetId,
            senderAddress = purchase.senderAddress,
            amount = java.math.BigInteger(purchase.amount.toString()),
            promo = purchase.promo,
        )

        // What the screen shows is what was asked for: a quote of another asset, amount, chain or sender is not signed.
        val sameAmount = quote.amount.toString() == purchase.amount.toString()
        val sameAsset = sameId(quote.assetId, purchase.batteryAssetId)
        val sameSender = sameId(quote.senderAddress, purchase.senderAddress)
        if (!sameAsset || !sameAmount || quote.chainId != purchase.chainId || !sameSender) {
            throw IllegalStateException(
                "Quoted ${quote.amount} of ${quote.assetId} on ${quote.chainId} from ${quote.senderAddress}, " +
                    "asked for ${purchase.amount} of ${purchase.batteryAssetId} on ${purchase.chainId} from ${purchase.senderAddress}",
            )
        }

        val json = quote.operation ?: throw IllegalStateException("Purchase quote has no operation")
        val expiresAt = quote.expiresAt ?: throw IllegalStateException("Purchase quote has no deadline")
        return BatteryPurchaseReview(quote, gaslessRelayer.parseOperation(json), expiresAt)
    }

    /** Signs [review]'s operation as [account] and sends it; the transaction hash when the battery gave one. */
    suspend fun send(
        walletId: String,
        review: BatteryPurchaseReview,
        account: AccountWithDetails,
        wallet: MnemonicWallet,
    ): String? {
        val auth = requireAuth(walletId)

        requireSafe(review, account)

        // The battery pays the network fee: neither the fee nor the nonce is read for its operations.
        val chainAccount = account.data.chainAccount
        val payloads = review.operation.payloads.map { payload ->
            val signed = provider.signPayload(payload, chainAccount, Fee.Value(chainAccount.asset, BigInteger.ZERO), BigInteger.ZERO, wallet)
            signed.getOrNull()
                ?: throw IllegalStateException("Payload ${payload.id} was not signed", signed.error)
        }
        val operation = SignedOperation(review.operation.id, payloads)

        val response = try {
            gaslessRelayer.send(auth, operation)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ClientException) {
            val rejection = GaslessRelayer.rejection(e)
            throw when {
                rejection.isExpired -> RelayerQuoteExpiredException(e)
                rejection.isAlreadySent -> RelayerOutcomeUnknownException(e)
                else -> RelayerRejectedException(
                    reason = rejection.message,
                    nothingSent = rejection.isNothingSent,
                    cause = e,
                )
            }
        } catch (e: Throwable) {
            // The request may have reached the battery: a retry could pay twice.
            throw RelayerOutcomeUnknownException(e)
        }

        return when (response.status) {
            GaslessV2SendResponse.Status.ok -> response.txHash?.takeIf { it.isNotBlank() }
            GaslessV2SendResponse.Status.failed -> throw RelayerRejectedException(reason = null)
            GaslessV2SendResponse.Status.unknown -> throw RelayerOutcomeUnknownException(
                IllegalStateException("Battery answered `${response.status.value}` to ${operation.id}"),
            )
        }
    }

    /**
     * The operation is held to the purchase before it is signed: [BatteryPurchaseQuote.amount] of the asset
     * to the battery's funds receiver, and nothing else. What chainkit finds dangerous refuses it unsigned,
     * as does a check that read nothing.
     */
    private suspend fun requireSafe(review: BatteryPurchaseReview, account: AccountWithDetails) {
        if (!isValidationEnabled()) {
            return
        }

        val intent = intent(review, account)
        val refused = provider.validateOperation(review.operation, account.value, intent)
            .findings
            .firstOrNull { it.severity == FindingSeverity.Danger || it is ValidationError.Unknown || it is ValidationError.UnsupportedChain }
        if (refused != null) {
            throw RelayerRejectedException(refused.message, nothingSent = true)
        }
    }

    private suspend fun requireAuth(walletId: String): Authorization {
        val auth = authorizationProvider.getAuthBy(walletId)
        if (auth.isEmpty) {
            throw IllegalStateException("Battery authorization is missing")
        }
        return auth
    }

    companion object {

        /** What the user agreed to on the confirm screen: the amount, to the battery's funds receiver. */
        fun intent(review: BatteryPurchaseReview, account: AccountWithDetails): Intent {
            val asset = account.asset.value
            return Intent(
                spends = listOf(Intent.Spend(asset, BigInteger.parseString(review.quote.amount.toString()))),
                to = Address.force(review.quote.fundsReceiver, asset.chain),
                source = Intent.Source.Wallet,
            )
        }
    }
}

/**
 * Whether two asset ids or addresses name the same thing. Only EVM hex (`0x…`) is case-insensitive: base58
 * Solana mints and TRON contracts tell assets apart by case.
 */
internal fun sameId(a: String, b: String): Boolean {
    if (a == b) {
        return true
    }
    return isEvmHex(a) && isEvmHex(b) && a.equals(b, ignoreCase = true)
}

private fun isEvmHex(id: String): Boolean {
    return id.substringAfterLast('/').startsWith("0x", ignoreCase = true)
}
