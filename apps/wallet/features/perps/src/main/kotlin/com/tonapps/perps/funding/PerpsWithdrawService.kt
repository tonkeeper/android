package com.tonapps.perps.funding

import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.chainkit.lighter.LighterConstants
import com.tonapps.chainkit.lighter.model.LighterTransferTxReq
import com.tonapps.extensions.fromHex
import com.tonapps.log.L
import com.tonapps.mvi.graph.KResult
import com.tonapps.perps.data.PerpsAccount
import com.tonapps.perps.data.PerpsBalance
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsLoadException
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.perps.data.PerpsSendError
import com.tonapps.perps.data.PerpsSendReason
import com.tonapps.perps.data.USD_CURRENCY
import com.tonapps.perps.data.withdrawableBalance
import com.tonapps.perps.trade.PerpsAccountProofs
import com.tonapps.perps.trade.PerpsAccountService
import com.tonapps.perps.trade.PerpsActivation
import com.tonapps.perps.trade.isRoTokenUnavailable
import com.tonapps.perps.trade.reissuingRoToken
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.lighter.LighterSigningCredentials
import com.tonapps.wallet.data.multichain.lighter.PerpetualRepository
import com.tonapps.wallet.data.multichain.perps.PerpsDepositRoute
import com.tonapps.wallet.data.multichain.perps.PerpsDepositStage
import com.tonapps.wallet.data.multichain.perps.PerpsFundingJournal
import com.tonapps.wallet.data.multichain.perps.PerpsFundingKind
import com.tonapps.wallet.data.multichain.perps.PerpsFundingRecord
import com.tonapps.wallet.data.multichain.perps.PerpsFundingStep
import com.tonapps.wallet.data.multichain.perps.PerpsFundingStepKind
import com.tonapps.wallet.data.multichain.perps.PerpsFundingStepStatus
import io.perpsapi.models.WithdrawQuoteRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.UUID

class PerpsWithdrawService(
    private val repository: PerpsRepository,
    private val journal: PerpsFundingJournal,
    private val mcAccountRepository: McAccountRepository,
    private val accountService: PerpsAccountService,
    private val perpetualRepository: PerpetualRepository,
    private val lighterApi: LighterPublicApi,
    private val tracker: PerpsWithdrawTracker,
    private val recovery: PerpsFundingRecovery,
) {

    suspend fun available(walletId: String): BigDecimal {
        val read: suspend () -> KResult<PerpsBalance?, PerpsError> = { repository.getPortfolioBalance(walletId) }
        var result = accountService.reissuingRoToken(walletId, read)
        if (result.isRoTokenUnavailable() && accountService.activateWithKeptKey(walletId)) {
            result = accountService.reissuingRoToken(walletId, read)
        }
        return when (result) {
            is KResult.Ok -> result.value?.withdrawableBalance() ?: BigDecimal.ZERO
            is KResult.Err -> throw PerpsLoadException(result.value)
        }
    }

    suspend fun availableUnits(walletId: String): BigInteger {
        return available(walletId)
            .setScale(PerpsFundingAmount.USDC_DECIMALS, RoundingMode.DOWN)
            .unscaledValue()
            .coerceAtLeast(BigInteger.ZERO)
    }

    suspend fun destinations(walletId: String): List<PerpsWithdrawDestination> = coroutineScope {
        val account = async { repository.account(walletId).getOrNull() }
        val usdc = async { sourceOf(walletId, PerpsFundingAssetId.USDC_ETHEREUM) }
        val transferFee = async { transferFeeOrNull(account.await()?.accountIndex) }
        val relayed = RELAY_DESTINATION_ASSET_IDS.map { assetId -> async { relayDestinationOrNull(walletId, assetId) } }
        val lighter = lighterDestination(account.await(), usdc.await())
        val fee = transferFee.await()
        listOf(lighter) + relayed.awaitAll()
            .filterNotNull()
            .map { it.copy(transferFeeUnits = fee) }
    }

    suspend fun limits(): PerpsWithdrawLimits = coroutineScope {
        val minimums = async { lighterRead { lighterApi.assetMinimums(USDC_ASSET_INDEX) } }
        val delay = async { lighterRead { lighterApi.withdrawalDelaySeconds() } }
        val loaded = minimums.await()
        PerpsWithdrawLimits(
            minWithdrawalUnits = loaded?.withdrawal?.toUsdcUnitsRoundedUp() ?: DEFAULT_MIN_UNITS,
            minTransferUnits = loaded?.transfer?.toUsdcUnitsRoundedUp() ?: DEFAULT_MIN_TRANSFER_UNITS,
            delaySeconds = delay.await(),
        )
    }

    suspend fun pendingWithdraw(walletId: String): PerpsFundingRecord? {
        return journal.list(walletId).firstOrNull { it.kind == PerpsFundingKind.WITHDRAW && it.stage.isSubmitting }
    }

    suspend fun prepare(walletId: String, amountUnits: BigInteger, destinationAssetId: String): PerpsWithdrawPlan = coroutineScope {
        val limits = async { limits() }
        val available = async { availableUnits(walletId) }
        val account = repository.account(walletId).getOrNull()
        val destination = destination(walletId, destinationAssetId, account)
            ?: throw PerpsWithdrawException.DestinationUnavailable(destinationAssetId)
        val minimum = limits.await().minimumFor(destination)
        if (amountUnits < minimum) {
            throw PerpsWithdrawException.BelowMinimum(minimum)
        }
        if (amountUnits > available.await()) {
            throw PerpsWithdrawException.InsufficientBalance()
        }
        val quote = if (destination.isLighterWithdraw) {
            null
        } else {
            quote(walletId, account, destination, amountUnits)
        }
        PerpsWithdrawPlan(
            walletId = walletId,
            amountUnits = amountUnits,
            destination = destination,
            limits = limits.await(),
            quote = quote,
            cost = quote?.let { costOf(destination, it) },
        )
    }

    suspend fun submit(plan: PerpsWithdrawPlan, wallet: CryptoWallet): PerpsFundingRecord {
        val walletId = plan.walletId
        pendingWithdraw(walletId)?.let { throw PerpsWithdrawException.WithdrawInFlight(it.operationId) }
        val quote = plan.quote
        if (quote != null && !quote.isAlive(System.currentTimeMillis())) {
            throw PerpsWithdrawException.QuoteExpired()
        }
        if (plan.amountUnits > availableUnits(walletId)) {
            throw PerpsWithdrawException.InsufficientBalance()
        }
        val credentials = when (val activation = accountService.ensureActivated(walletId) { wallet }) {
            is PerpsActivation.Ready -> activation.credentials
            PerpsActivation.NoAccount -> throw PerpsWithdrawException.AccountUnavailable()
            PerpsActivation.Failed -> throw PerpsWithdrawException.ActivationFailed()
        }
        if (quote != null) {
            checkTransferFee(credentials.accountIndex, quote)
        }
        val sign: suspend () -> PerpsFundingStep = if (quote == null) {
            { signStep(walletId, plan, credentials) }
        } else {
            { signTransferStep(walletId, quote, credentials, wallet) }
        }
        val step = sign()
        val now = System.currentTimeMillis()
        var record = PerpsFundingRecord(
            operationId = UUID.randomUUID().toString(),
            walletId = walletId,
            kind = PerpsFundingKind.WITHDRAW,
            stage = PerpsDepositStage.SOURCE_SIGNED,
            route = if (quote == null) {
                PerpsDepositRoute.DIRECT_GATEWAY
            } else {
                PerpsDepositRoute.CROSSCHAIN_SWAP
            },
            beneficiary = plan.destination.recipient,
            sourceAssetId = LIGHTER_USDC_ASSET_ID,
            sourceSymbol = USDC_SYMBOL,
            sourceDecimals = PerpsFundingAmount.USDC_DECIMALS,
            sourceAmount = plan.amountUnits.toString(),
            targetAmount = quote?.outputUnits?.toString() ?: plan.amountUnits.toString(),
            quoteId = quote?.quoteId,
            executionId = quote?.executionId,
            sourceTxHash = null,
            l1TxHash = null,
            steps = listOf(step),
            linkSignature = null,
            linkDeadline = null,
            registration = null,
            depositId = null,
            creditedAmount = null,
            accountIndex = credentials.accountIndex,
            failureReason = null,
            revision = 0,
            createdAt = now,
            updatedAt = now,
        )
        check(journal.saveIfAbsent(record)) { "perps withdraw: ${record.operationId} already exists" }

        val outcome = try {
            recovery.holding(record.operationId) {
                val first = tracker.send(record, firstAttempt = true)
                val refusal = first.error as? PerpsSendError.Refused
                if (refusal != null && refusal.reason == PerpsSendReason.RESIGN_REQUIRED) {
                    val resigned = try {
                        sign()
                    } catch (e: Throwable) {
                        if (e is CancellationException) {
                            throw e
                        }
                        tracker.refused(first.record, refusal)
                        throw e
                    }
                    val retry = journal.update(first.record.operationId, first.record.revision) {
                        it.copy(stage = PerpsDepositStage.SOURCE_SIGNED, steps = listOf(resigned))
                    }
                    tracker.send(retry, firstAttempt = false)
                } else {
                    first
                }
            }
        } finally {
            recovery.refresh(walletId)
        }
        record = outcome.record
        when (record.stage) {
            PerpsDepositStage.FAILED -> throw when (val error = outcome.error) {
                is PerpsSendError.Refused -> PerpsWithdrawException.Refused(error.reason, error.code)
                is PerpsSendError.Failed -> PerpsWithdrawException.SendFailed(error.error)
                PerpsSendError.Unknown, null -> PerpsWithdrawException.SendUnknown()
            }

            PerpsDepositStage.SOURCE_UNKNOWN,
            PerpsDepositStage.SOURCE_SENDING -> throw PerpsWithdrawException.SendUnknown()

            else -> return record
        }
    }

    private suspend fun quote(
        walletId: String,
        account: PerpsAccount?,
        destination: PerpsWithdrawDestination,
        amountUnits: BigInteger,
    ): PerpsWithdrawQuote {
        val accountIndex = account?.accountIndex ?: throw PerpsWithdrawException.AccountUnavailable()
        val request = WithdrawQuoteRequest(
            destinationAsset = destination.assetId,
            amount = amountUnits.toString(),
            recipientAddress = destination.recipient,
        )
        return PerpsWithdrawQuoteReader.read(
            quote = repository.quoteWithdraw(walletId, request),
            request = request,
            accountIndex = accountIndex,
            nowMs = System.currentTimeMillis(),
        )
    }

    private suspend fun destination(
        walletId: String,
        assetId: String,
        account: PerpsAccount?,
    ): PerpsWithdrawDestination? = coroutineScope {
        when {
            assetId.equals(PerpsFundingAssetId.USDC_ETHEREUM, ignoreCase = true) -> {
                lighterDestination(account, sourceOf(walletId, PerpsFundingAssetId.USDC_ETHEREUM))
            }

            RELAY_DESTINATION_ASSET_IDS.any { it.equals(assetId, ignoreCase = true) } -> {
                val relayed = async { relayDestination(walletId, assetId) }
                val transferFee = async { transferFeeOrNull(account?.accountIndex) }
                relayed.await()?.copy(transferFeeUnits = transferFee.await())
            }

            else -> null
        }
    }

    private fun lighterDestination(account: PerpsAccount?, usdc: PerpsDepositSource?): PerpsWithdrawDestination {
        val l1Address = account?.l1Address?.let(PerpsAccountProofs::normalizeL1AddressOrNull)
            ?: throw PerpsWithdrawException.AccountUnavailable()
        return PerpsWithdrawDestination(
            assetId = PerpsFundingAssetId.USDC_ETHEREUM,
            recipient = l1Address,
            source = usdc,
            transferFeeUnits = null,
        )
    }

    private suspend fun relayDestination(walletId: String, assetId: String): PerpsWithdrawDestination? {
        val account = mcAccountRepository.findAccount(walletId, assetId, currencyOverride = USD_CURRENCY) ?: return null
        val asset = PerpsFundingAssetId.parseDestination(account.asset.id) ?: return null
        val address = account.data.displayAddress
        return PerpsWithdrawDestination(
            assetId = asset.raw,
            recipient = if (asset.isEvm) {
                address.lowercase()
            } else {
                address
            },
            source = PerpsDepositSource(account, asset),
            transferFeeUnits = null,
        )
    }

    private fun costOf(destination: PerpsWithdrawDestination, quote: PerpsWithdrawQuote): PerpsWithdrawCost {
        val transfer = quote.transfer
        val lighterFee = PerpsFundingAmount.toDecimal(transfer.usdcFeeUnits, PerpsFundingAmount.USDC_DECIMALS)
        val source = destination.source
        val usdPrice = source?.account?.rate
            ?.takeIf { it.currencyCode.equals(USD_CURRENCY, ignoreCase = true) }
            ?.price
            ?.toBigDecimalOrNull()
        if (source == null || usdPrice == null) {
            return PerpsWithdrawCost(usd = lighterFee, isEstimate = false)
        }
        val spent = PerpsFundingAmount.toDecimal(transfer.amountUnits + transfer.usdcFeeUnits, PerpsFundingAmount.USDC_DECIMALS)
        val received = PerpsFundingAmount.toDecimal(quote.outputUnits, source.decimals).multiply(usdPrice)
        return PerpsWithdrawCost(usd = spent.subtract(received).max(lighterFee), isEstimate = true)
    }

    private suspend fun checkTransferFee(accountIndex: Long, quote: PerpsWithdrawQuote) {
        val current = lighterRead { lighterApi.transferFeeUnits(accountIndex, quote.transfer.toAccountIndex) } ?: return
        if (current != quote.transfer.usdcFeeUnits) {
            throw PerpsWithdrawException.FeeChanged(quoted = quote.transfer.usdcFeeUnits, current = current)
        }
    }

    private suspend fun transferFeeOrNull(accountIndex: Long?): BigInteger? {
        accountIndex ?: return null
        return lighterRead { lighterApi.transferFeeUnits(accountIndex) }
    }

    private fun BigDecimal.toUsdcUnitsRoundedUp(): BigInteger {
        return setScale(PerpsFundingAmount.USDC_DECIMALS, RoundingMode.UP).unscaledValue()
    }

    private suspend fun relayDestinationOrNull(walletId: String, assetId: String): PerpsWithdrawDestination? {
        return try {
            relayDestination(walletId, assetId)
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e, "perps withdraw: $assetId is not available as a destination")
            null
        }
    }

    private suspend fun sourceOf(walletId: String, assetId: String): PerpsDepositSource? {
        return mcAccountRepository.findAccount(walletId, assetId)?.let { details ->
            PerpsFundingAssetId.parse(details.asset.id)?.let { PerpsDepositSource(details, it) }
        }
    }

    private suspend fun signStep(
        walletId: String,
        plan: PerpsWithdrawPlan,
        credentials: LighterSigningCredentials,
    ): PerpsFundingStep {
        val nonce = nextNonce(walletId, credentials)
        val expiredAtMs = expiredAtMs()
        val signed = perpetualRepository.signWithdraw(
            walletId = walletId,
            assetIndex = USDC_ASSET_INDEX.toShort(),
            routeType = LighterConstants.AssetRouteTypePerps,
            amountUnits = plan.amountUnits.longValueExact(),
            nonce = nonce,
            expiredAtMs = expiredAtMs,
        ) ?: throw PerpsWithdrawException.SigningFailed("no Lighter credentials")
        return PerpsFundingStep(
            stepId = signed.txHash,
            kind = PerpsFundingStepKind.MAIN,
            chainId = "$LIGHTER_CHAIN_LABEL/${credentials.chainId}",
            to = plan.destination.recipient,
            value = plan.amountUnits.toString(),
            data = null,
            txHash = signed.txHash,
            signedData = signed.txInfo,
            nonce = signed.nonce.toString(),
            status = PerpsFundingStepStatus.SIGNED,
        )
    }

    private suspend fun signTransferStep(
        walletId: String,
        quote: PerpsWithdrawQuote,
        credentials: LighterSigningCredentials,
        wallet: CryptoWallet,
    ): PerpsFundingStep {
        val nonce = nextNonce(walletId, credentials)
        val transfer = quote.transfer
        val signed = perpetualRepository.signTransfer(
            walletId = walletId,
            request = LighterTransferTxReq(
                toAccountIndex = transfer.toAccountIndex,
                assetIndex = USDC_ASSET_INDEX.toShort(),
                fromRouteType = LighterConstants.AssetRouteTypePerps,
                toRouteType = LighterConstants.AssetRouteTypePerps,
                amount = transfer.amountUnits.longValueExact(),
                usdcFee = transfer.usdcFeeUnits.longValueExact(),
                memo = transfer.memoHex.fromHex(),
            ),
            nonce = nonce,
            expiredAtMs = expiredAtMs(),
            signOwnerMessage = { message -> accountService.signOwnerMessage(wallet, message) },
        ) ?: throw PerpsWithdrawException.SigningFailed("no Lighter credentials")
        return PerpsFundingStep(
            stepId = signed.txHash,
            kind = PerpsFundingStepKind.MAIN,
            chainId = "$LIGHTER_CHAIN_LABEL/${credentials.chainId}",
            to = transfer.toAccountIndex.toString(),
            value = transfer.amountUnits.toString(),
            data = transfer.memoHex,
            txHash = signed.txHash,
            signedData = signed.txInfo,
            nonce = signed.nonce.toString(),
            status = PerpsFundingStepStatus.SIGNED,
        )
    }

    private suspend fun nextNonce(walletId: String, credentials: LighterSigningCredentials): Long {
        val nonce = when (val result = repository.nextNonce(walletId)) {
            is KResult.Ok -> result.value
            is KResult.Err -> throw PerpsWithdrawException.SendFailed(result.value)
        }
        if (nonce.accountIndex != credentials.accountIndex) {
            throw PerpsWithdrawException.SigningFailed("nonce is for account ${nonce.accountIndex}")
        }
        return nonce.nonce
    }

    private fun expiredAtMs(): Long {
        return minOf(
            System.currentTimeMillis() + LighterConstants.DefaultExpireTimeMillis,
            LighterConstants.MaxTimestamp,
        )
    }

    private suspend fun <T> lighterRead(block: suspend () -> T): T? {
        return try {
            block()
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e, "perps withdraw: Lighter read failed")
            null
        }
    }

    companion object {
        const val USDC_ASSET_INDEX = 3
        const val USDC_SYMBOL = "USDC"
        const val LIGHTER_USDC_ASSET_ID = "lighter/mainnet/usdc"
        private const val TON_COIN_ASSET_ID = "ton/mainnet/coin"
        private const val LIGHTER_CHAIN_LABEL = "lighter"
        val DEFAULT_MIN_UNITS: BigInteger = PerpsFundingAmount.GATEWAY_MINIMUM_USDC_UNITS
        private val DEFAULT_MIN_TRANSFER_UNITS: BigInteger = BigInteger.valueOf(1_000_000L)

        private val RELAY_DESTINATION_ASSET_IDS = listOf(
            TON_COIN_ASSET_ID,
            "tron/mainnet/trc20/TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t",
            "bsc/mainnet/bep20/0x55d398326f99059ff775485246999027b3197955",
            "eth/mainnet/erc20/0xdac17f958d2ee523a2206206994597c13d831ec7",
            "arb/mainnet/erc20/0xfd086bc7cd5c481dcc9c85ebe478a1c0b69fcbb9",
            "base/mainnet/erc20/0x833589fcd6edb6e08f4c7c32d4f71b54bda02913",
            "base/mainnet/erc20/0xfde4c96c8593536e31f229ea8f37b2ada2699bb2",
            "arb/mainnet/erc20/0xaf88d065e77c8cc2239327c5edb3a432268e5831",
            "bsc/mainnet/bep20/0x8ac76a51cc950d9822d68b83fe1ad97b32cd580d",
            "eth/mainnet/coin",
            "base/mainnet/coin",
            "arb/mainnet/coin",
            "bsc/mainnet/coin",
            "tron/mainnet/coin",
            "btc/mainnet/coin",
        )
    }
}
