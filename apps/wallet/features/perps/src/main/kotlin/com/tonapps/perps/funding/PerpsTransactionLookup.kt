package com.tonapps.perps.funding

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.CryptoKitClient
import com.tonapps.chainkit.chain.ethereum.EthereumMediator
import com.tonapps.chainkit.core.chain.api.models.TxError
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.transaction.TransactionInfo
import com.tonapps.chainkit.core.common.NetworkError
import com.tonapps.log.L
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import kotlinx.coroutines.CancellationException

internal const val TX_REVERTED = "reverted"

private const val HTTP_BAD_REQUEST = 400

enum class PerpsTransactionPresence {
    KNOWN,
    REVERTED,
    UNKNOWN,
    UNAVAILABLE,
}

sealed interface PerpsStepOutcome {
    class Sent(val hash: String) : PerpsStepOutcome

    class Failed(val reason: String?) : PerpsStepOutcome

    data object Unknown : PerpsStepOutcome
}

class PerpsTransactionLookup(
    private val client: CryptoKitClient,
    private val provider: ChainKitProvider,
    private val mcAccountRepository: McAccountRepository,
) {

    fun canLookUp(chain: Chain): Boolean = chain.isEvm || chain is Chain.Tron || chain is Chain.Bitcoin

    suspend fun presence(walletId: String, assetId: String, chain: Chain, txHash: String): PerpsTransactionPresence {
        return try {
            if (chain.isEvm) {
                evmPresence(chain, txHash)
            } else {
                chainPresence(walletId, assetId, chain, txHash)
            }
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e, "perps funding: could not look up $txHash on ${chain.network.type}")
            PerpsTransactionPresence.UNAVAILABLE
        }
    }

    private suspend fun evmPresence(chain: Chain, txHash: String): PerpsTransactionPresence {
        val rpc = (client.blockchain.getMediator(chain.network.type).mediator as? EthereumMediator)?.ethereumRpcClient
            ?: return PerpsTransactionPresence.UNAVAILABLE
        val receipt = rpc.getTransactionReceipt(chain, txHash)
        return when {
            receipt?.status == BigInteger.ONE -> PerpsTransactionPresence.KNOWN
            receipt?.status == BigInteger.ZERO -> PerpsTransactionPresence.REVERTED
            rpc.getTransactionByHash(chain, txHash) != null -> PerpsTransactionPresence.KNOWN
            else -> PerpsTransactionPresence.UNKNOWN
        }
    }

    private suspend fun chainPresence(walletId: String, assetId: String, chain: Chain, txHash: String): PerpsTransactionPresence {
        val account = mcAccountRepository.findAccount(walletId, assetId)?.data?.chainAccount
            ?: return PerpsTransactionPresence.UNAVAILABLE
        val found = provider.findTransaction(account, txHash)
        val error = found.error
        return when {
            error is TxError.NotFound -> PerpsTransactionPresence.UNKNOWN
            chain is Chain.Bitcoin && (error?.cause as? NetworkError)?.isBlockbookTxNotFound(txHash) == true ->
                PerpsTransactionPresence.UNKNOWN
            error != null -> PerpsTransactionPresence.UNAVAILABLE
            else -> when (found.unwrap().status) {
                TransactionInfo.Status.Confirmed,
                TransactionInfo.Status.Pending -> PerpsTransactionPresence.KNOWN
                is TransactionInfo.Status.Failed -> PerpsTransactionPresence.REVERTED
            }
        }
    }
}

// Blockbook answers an unknown txid with HTTP 400 {"error":"Transaction '<txid>' not found"}; the
// "not found (<cause>)" form is a backend failure, not a missing transaction.
// https://github.com/trezor/blockbook/blob/44242e0d24d1b9a78ce77df05c9221f6789ba463/api/worker.go#L291-L294
// https://github.com/trezor/blockbook/blob/44242e0d24d1b9a78ce77df05c9221f6789ba463/server/html_templates.go#L126-L127
private fun NetworkError.isBlockbookTxNotFound(txHash: String): Boolean {
    val notFound = "Transaction '$txHash' not found"
    return code == HTTP_BAD_REQUEST && message.contains(notFound) && !message.contains("$notFound (")
}
