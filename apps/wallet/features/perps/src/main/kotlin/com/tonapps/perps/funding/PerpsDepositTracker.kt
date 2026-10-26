package com.tonapps.perps.funding

import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.api.models.NodeError
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.chainkit.core.op.SignedOperation
import com.tonapps.chainkit.core.op.SignedPayload
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.SentPayload
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualDepositTracker
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.PendingSwap
import com.tonapps.wallet.data.multichain.perps.PerpsDepositRoute
import com.tonapps.wallet.data.multichain.perps.PerpsDepositStage
import com.tonapps.wallet.data.multichain.perps.PerpsFundingJournal
import com.tonapps.wallet.data.multichain.perps.PerpsFundingKind
import com.tonapps.wallet.data.multichain.perps.PerpsFundingRecord
import com.tonapps.wallet.data.multichain.perps.PerpsFundingStep
import com.tonapps.wallet.data.multichain.perps.PerpsFundingStepKind
import com.tonapps.wallet.data.multichain.perps.PerpsFundingStepStatus
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonPrimitive

/**
 * Journals a deposit sent from the shared confirm screen, so recovery registers it with the perps
 * backend and the portfolio tracks it: recorded before the first broadcast, updated per attempt.
 */
class PerpsDepositTracker(
    private val journal: PerpsFundingJournal,
    private val recovery: PerpsFundingRecovery,
    private val depositService: PerpsDepositService,
    private val provider: ChainKitProvider,
) : PerpetualDepositTracker {

    override suspend fun onSigned(
        request: ConfirmRequest,
        account: Account,
        operation: SignedOperation,
        wallet: CryptoWallet,
    ) {
        val walletId = request.data.walletId
        val deposit = request.perpetualDeposit()
        depositService.pendingPayment(walletId)
            ?.takeIf { it.operationId != operation.id }
            ?.let { throw PerpsDepositException.PaymentInFlight(it.operationId) }

        val fundingAsset = PerpsFundingAssetId.parse(request.data.assetId)
            ?: throw IllegalArgumentException("perps funding: ${request.data.assetId} is not a deposit source")
        val link = depositService.linkWallet(walletId, wallet)
        val now = System.currentTimeMillis()
        val record = PerpsFundingRecord(
            operationId = operation.id,
            walletId = walletId,
            kind = PerpsFundingKind.DEPOSIT,
            stage = PerpsDepositStage.SOURCE_SIGNED,
            route = if (deposit.quote.crossChain) {
                PerpsDepositRoute.CROSSCHAIN_SWAP
            } else {
                PerpsDepositRoute.DIRECT_GATEWAY
            },
            beneficiary = deposit.quote.beneficiary,
            sourceAssetId = fundingAsset.raw,
            sourceSymbol = account.asset.symbol,
            sourceDecimals = account.asset.decimals.value,
            sourceAmount = deposit.amount.toString(),
            targetAmount = deposit.quote.depositAmount.toString(),
            quoteId = deposit.quote.id,
            executionId = deposit.quote.executionId,
            sourceTxHash = null,
            l1TxHash = null,
            steps = steps(deposit, fundingAsset, operation),
            linkSignature = link?.signature,
            linkDeadline = link?.deadline,
            registration = null,
            depositId = null,
            creditedAmount = null,
            accountIndex = null,
            failureReason = null,
            revision = 0,
            createdAt = now,
            updatedAt = now,
        )
        journal.saveIfAbsent(record)
    }

    override suspend fun track(
        request: ConfirmRequest,
        account: Account,
        operation: SignedOperation,
        send: suspend () -> List<SentPayload>,
    ): List<SentPayload> {
        val walletId = request.data.walletId
        try {
            return recovery.holding(operation.id) {
                var record = sending(walletId, operation.id)
                val sent = try {
                    send()
                } catch (e: Throwable) {
                    if (e is CancellationException) {
                        throw e
                    }
                    failed(record, e)
                    throw e
                }
                for (item in sent) {
                    record = journal.update(record.operationId, record.revision) {
                        it.sent(item.payload.payloadId, item.txHash)
                    }
                }
                sent
            }
        } finally {
            recovery.refresh(walletId)
        }
    }

    override fun pendingSwap(request: ConfirmRequest, payload: Payload): PendingSwap? {
        if (!payload.chain.isTvm) {
            return null
        }
        val deposit = request.perpetualDeposit()
        return PendingSwap(
            destinationAssetId = PerpsFundingAssetId.USDC_ETHEREUM,
            service = RELAY_SERVICE.takeIf { deposit.quote.crossChain },
            routeId = deposit.quote.id,
            serviceTxId = deposit.quote.executionId,
            sellBaseAmount = deposit.amount,
        )
    }

    private suspend fun sending(walletId: String, operationId: String): PerpsFundingRecord {
        val record = journal.list(walletId).firstOrNull { it.operationId == operationId }
            ?: throw IllegalStateException("perps funding: $operationId was not journaled before its send")
        return journal.update(record.operationId, record.revision) { current ->
            current.copy(
                stage = PerpsDepositStage.SOURCE_SENDING,
                failureReason = null,
                steps = current.steps.map { step ->
                    if (step.status == PerpsFundingStepStatus.SENT) {
                        step
                    } else {
                        step.copy(status = PerpsFundingStepStatus.SENDING)
                    }
                },
            )
        }
    }

    // The sender stops at the first payload a chain refused, so the failed step is the first one in flight.
    private suspend fun failed(record: PerpsFundingRecord, error: Throwable) {
        val current = journal.list(record.walletId).firstOrNull { it.operationId == record.operationId } ?: return
        val step = current.steps.firstOrNull { it.status == PerpsFundingStepStatus.SENDING } ?: return
        val outcome = if (error is NodeError && error.isRejection(step.chainId)) {
            PerpsStepOutcome.Failed(error.message)
        } else {
            PerpsStepOutcome.Unknown
        }
        journal.update(current.operationId, current.revision) { it.settled(step.stepId, outcome) }
    }

    private suspend fun steps(
        deposit: ConfirmType.PerpetualDeposit,
        fundingAsset: PerpsFundingAssetId,
        operation: SignedOperation,
    ): List<PerpsFundingStep> {
        val transactions = operation.payloads.filter { it.type.isTransaction }
        return transactions.mapIndexed { index, signed ->
            val payload = deposit.operation.payloads.first { it.id == signed.payloadId }
            val transfer = payload.meta.transfer()
            val data = signed.signedData()
            PerpsFundingStep(
                stepId = signed.payloadId,
                kind = if (index == transactions.lastIndex) {
                    PerpsFundingStepKind.MAIN
                } else {
                    PerpsFundingStepKind.APPROVAL
                },
                chainId = fundingAsset.chainId,
                to = transfer.to,
                value = transfer.value,
                data = transfer.data,
                txHash = provider.transactionHash(signed),
                signedData = data,
                nonce = null,
                status = PerpsFundingStepStatus.SIGNED,
            )
        }
    }

    private fun SignedPayload.signedData(): String {
        return (data as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw IllegalArgumentException("perps funding: $payloadId is not a signed transaction")
    }

    private fun PayloadMeta.transfer(): StepTransfer {
        return when (this) {
            is PayloadMeta.Evm.Main -> StepTransfer(to.display, value.toString(), data)
            is PayloadMeta.Ton.Main -> StepTransfer(to.display, value.toString(), body)
            is PayloadMeta.Tron.Main -> StepTransfer(to.display, value.toString(), data)
            is PayloadMeta.Bitcoin.Main -> StepTransfer(to.display, value.toString(), memo)
            is PayloadMeta.Tron.Prebuild -> StepTransfer(to = "", value = "0", data = data)
            is PayloadMeta.Bitcoin.Prebuild -> StepTransfer(to = "", value = "0", data = data)
            else -> throw IllegalArgumentException("perps funding: ${this::class.simpleName} is not a deposit step")
        }
    }

    private fun ConfirmRequest.perpetualDeposit(): ConfirmType.PerpetualDeposit {
        return type as? ConfirmType.PerpetualDeposit
            ?: throw IllegalArgumentException("perps funding: ${type::class.simpleName} is not a deposit")
    }

    private class StepTransfer(val to: String, val value: String, val data: String?)

    private companion object {
        const val RELAY_SERVICE = "relay"
    }
}
