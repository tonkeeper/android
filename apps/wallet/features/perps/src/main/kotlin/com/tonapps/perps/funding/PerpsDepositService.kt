package com.tonapps.perps.funding

import com.ionspin.kotlin.bignum.integer.BigInteger as ChainInteger
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.blockchain.utils.add0x
import com.tonapps.chainkit.core.chain.api.models.ChainError
import com.tonapps.chainkit.core.chain.api.models.NodeError
import com.tonapps.chainkit.core.chain.model.MessageType
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.core.common.NetworkError
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.transaction.GasReservePolicy
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.coinValue
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualDepositAuthorizer
import com.tonapps.log.L
import com.tonapps.mvi.graph.KResult
import com.tonapps.perps.data.LIGHTER_API_KEY_INDEX
import com.tonapps.perps.data.LIGHTER_NIL_ACCOUNT_INDEX
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.perps.data.USD_CURRENCY
import com.tonapps.perps.trade.PerpsAccountProofs
import com.tonapps.perps.trade.PerpsAccountService
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.data.multichain.account.AccountEntity
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.AssetCapability
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.lighter.PerpetualDepositQuote
import com.tonapps.wallet.data.multichain.lighter.PerpetualRepository
import com.tonapps.wallet.data.multichain.lighter.PerpsQuoteError
import com.tonapps.wallet.data.multichain.perps.PerpsDepositRoute
import com.tonapps.wallet.data.multichain.perps.PerpsDepositStage
import com.tonapps.wallet.data.multichain.perps.PerpsFundingJournal
import com.tonapps.wallet.data.multichain.perps.PerpsFundingKind
import com.tonapps.wallet.data.multichain.perps.PerpsFundingRecord
import com.tonapps.wallet.data.multichain.perps.PerpsFundingStep
import com.tonapps.wallet.data.multichain.perps.PerpsFundingStepKind
import com.tonapps.wallet.data.multichain.perps.PerpsFundingStepStatus
import com.tonapps.wallet.data.settings.SettingsRepository
import io.perpsapi.models.ChangePubKey
import io.perpsapi.models.DepositQuote
import io.perpsapi.models.DepositQuoteRequest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

private const val EMPTY_CALLDATA = "0x"
private const val CHANGE_PUB_KEY_PREVIEW = "change_pub_key preview"
private const val TRON_BROADCAST_REFUSED_CODE = 0

class PerpsDepositService(
    private val repository: PerpsRepository,
    private val journal: PerpsFundingJournal,
    private val mcAccountRepository: McAccountRepository,
    private val settingsRepository: SettingsRepository,
    private val accountService: PerpsAccountService,
    private val provider: ChainKitProvider,
    private val recovery: PerpsFundingRecovery,
    private val perpetualRepository: PerpetualRepository,
) : PerpetualDepositAuthorizer {

    suspend fun sources(walletId: String): List<PerpsDepositSource> {
        val currency = settingsRepository.currency.code
        val accounts = try {
            fetchAllAccounts(walletId, currency)
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e)
            mcAccountRepository.getCachedAccounts(
                walletId = walletId,
                currency = currency,
                availableOnly = true,
                showHidden = true,
                capabilities = DEPOSIT_SOURCE_CAPABILITIES,
                hideDust = true,
            )?.accounts ?: throw e
        }
        val sources = accounts.mapNotNull { account ->
            PerpsFundingAssetId.parse(account.asset.id)?.let { PerpsDepositSource(account, it) }
        }
        val withBestValue = if (sources.any { it.isBestValue }) {
            sources
        } else {
            sources + listOfNotNull(fetchBestValueSource(walletId, currency))
        }
        return withBestValue.sortedWith(compareByDescending<PerpsDepositSource> { it.isBestValue }.thenByDescending { it.fiatValue })
    }

    private suspend fun fetchBestValueSource(walletId: String, currency: String): PerpsDepositSource? {
        val account = try {
            mcAccountRepository.findAccount(
                walletId = walletId,
                assetId = PerpsFundingAssetId.USDC_ETHEREUM,
                currencyOverride = currency,
                forceRefresh = true,
            )
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e)
            null
        } ?: return null
        return PerpsFundingAssetId.parse(account.asset.id)?.let { PerpsDepositSource(account, it) }
    }

    private suspend fun fetchAllAccounts(walletId: String, currency: String): List<AccountWithDetails> {
        val accounts = ArrayList<AccountWithDetails>()
        var cursor: String? = null
        repeat(MAX_SOURCE_PAGES) {
            val page = mcAccountRepository.fetchAccounts(
                walletId = walletId,
                currency = currency,
                availableOnly = true,
                showHidden = true,
                cursor = cursor,
                capabilities = DEPOSIT_SOURCE_CAPABILITIES,
                hideDust = true,
            )
            accounts += page.accounts
            cursor = page.nextCursor ?: return accounts
        }
        return accounts
    }

    suspend fun beneficiary(walletId: String): String {
        return PerpsAccountProofs.normalizeL1Address(coinAccount(walletId, Chain.Ethereum.Mainnet).displayAddress)
    }

    suspend fun pendingPayment(walletId: String): PerpsFundingRecord? {
        return journal.list(walletId).firstOrNull { it.kind == PerpsFundingKind.DEPOSIT && it.stage.holdsUnregisteredPayment }
    }

    suspend fun dismissOutcomes(walletId: String) {
        val outcomes = journal.list(walletId)
            .filter { it.stage == PerpsDepositStage.FAILED || it.stage == PerpsDepositStage.REFUNDED }
            .map { it.operationId }
        if (outcomes.isNotEmpty()) {
            journal.delete(outcomes)
        }
    }

    suspend fun maxAmount(walletId: String, source: PerpsDepositSource): BigInteger {
        val balance = source.balanceUnits
        if (!source.fundingAsset.isCoin || balance.signum() <= 0) {
            return balance
        }
        if (source.account.value.chain !is Chain.Tvm) {
            quotedMaxAmount(walletId, source)?.let { return it }
        }
        return probedMaxAmount(source)
    }

    private suspend fun quotedMaxAmount(walletId: String, source: PerpsDepositSource): BigInteger? {
        val balance = source.balanceUnits
        val beneficiary = beneficiary(walletId)
        for (percent in MAX_QUOTE_PERCENTS) {
            val amount = balance * percent.toBigInteger() / HUNDRED
            val quote = when (val probe = probeQuote(walletId, source, amount, beneficiary)) {
                is MaxProbe.Priced -> probe.quote
                MaxProbe.BelowMinimum -> return balance
                MaxProbe.Refused -> continue
            }
            if (quote.preview) {
                return null
            }
            val fee = networkFee(source, quote.operation) ?: return null
            val reserve = fee.amount.toJava() * MAX_QUOTED_FEE_MARGIN_PERCENT.toBigInteger() / HUNDRED
            val candidate = (balance - reserve).coerceAtLeast(BigInteger.ZERO)
            if (source.account.value.chain is Chain.Bitcoin && candidate > amount) {
                return largestQuotable(walletId, source, beneficiary, quoted = amount, candidate = candidate)
            }
            return candidate
        }
        return null
    }

    private suspend fun largestQuotable(
        walletId: String,
        source: PerpsDepositSource,
        beneficiary: String,
        quoted: BigInteger,
        candidate: BigInteger,
    ): BigInteger {
        if (probeQuote(walletId, source, candidate, beneficiary) is MaxProbe.Priced) {
            return candidate
        }
        var priced = quoted
        var refused = candidate
        repeat(MAX_BISECTIONS) {
            val middle = (priced + refused) / BigInteger.TWO
            if (middle <= priced) {
                return priced
            }
            if (probeQuote(walletId, source, middle, beneficiary) is MaxProbe.Priced) {
                priced = middle
            } else {
                refused = middle
            }
        }
        return priced
    }

    private suspend fun probeQuote(
        walletId: String,
        source: PerpsDepositSource,
        amountUnits: BigInteger,
        beneficiary: String,
    ): MaxProbe {
        return try {
            MaxProbe.Priced(depositQuote(repository.quoteDeposit(walletId, quoteRequest(walletId, source, amountUnits, beneficiary))))
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            if ((e.toPerpsFundingError() as? PerpsFundingError.Refused)?.reason == REASON_AMOUNT_TOO_LOW) {
                MaxProbe.BelowMinimum
            } else {
                L.e(e, "perps funding: no quote for MAX of $amountUnits ${source.assetId}")
                MaxProbe.Refused
            }
        }
    }

    private suspend fun probedMaxAmount(source: PerpsDepositSource): BigInteger {
        val balance = source.balanceUnits
        val account = source.account.value
        val probe = if (account.chain is Chain.Tvm) {
            Transaction.Swap(
                account = account,
                amount = balance.toChain(),
                destination = account,
                to = account.address,
                isMax = true,
            )
        } else {
            Transaction.Call(
                account = account,
                amount = balance.toChain(),
                contract = account.address,
                data = EMPTY_CALLDATA,
            )
        }
        val fee = provider.estimateFee(probe).getOrNull() ?: return balance
        val reserve = provider.calculateGasReserve(
            account = account,
            energy = account,
            amount = balance.toChain(),
            isMax = true,
            fee = Fee.Value(fee.asset, fee.amount * MAX_FEE_MARGIN),
            policy = GasReservePolicy.ShrinkToFit,
        ).getOrNull() ?: return balance
        return reserve.amount.toJava().coerceAtLeast(BigInteger.ZERO)
    }

    /**
     * A quote for [amountUnits] of [source], as the confirm request it opens. Every quote names the account's
     * key and the backend tells whether the venue still lacks it: a deposit that registers it opens as its
     * preview, which the confirm screen [authorize]s on the swipe.
     */
    suspend fun confirmRequest(
        walletId: String,
        source: PerpsDepositSource,
        amountUnits: BigInteger,
    ): ConfirmRequest {
        val beneficiary = beneficiary(walletId)
        val request = quoteRequest(walletId, source, amountUnits, beneficiary)
        val quote = depositQuote(repository.quoteDeposit(walletId, request))
        checkBalance(source, quote)
        val fee = if (quote.preview) {
            null
        } else {
            networkFee(source, quote.operation)
        }
        fee?.let { checkNetworkFee(walletId, quote, it) }
        return PerpsConfirmRequests.deposit(
            walletId = walletId,
            sourceAssetId = source.assetId,
            beneficiary = beneficiary,
            quote = quote,
            preview = quote.preview,
            feeShareBps = feeShareBps(walletId, source, quote, fee),
        )
    }

    suspend fun minimum(walletId: String, source: PerpsDepositSource): PerpsDepositMinimum? {
        if (source.isBestValue) {
            return PerpsDepositMinimum(
                usd = PerpsFundingAmount.toDecimal(PerpsFundingAmount.GATEWAY_MINIMUM_USDC_UNITS, PerpsFundingAmount.USDC_DECIMALS),
                units = PerpsFundingAmount.GATEWAY_MINIMUM_USDC_UNITS,
            )
        }
        val usdPrice = usdPrice(walletId, source.assetId) ?: return null
        val probeUnits = PerpsFundingAmount.toUnits(
            amount = MINIMUM_PROBE_USD.divide(usdPrice, source.decimals, RoundingMode.DOWN),
            decimals = source.decimals,
            rounding = RoundingMode.DOWN,
        ).max(BigInteger.ONE)
        val minUsd = try {
            repository.quoteDeposit(walletId, quoteRequest(walletId, source, probeUnits, beneficiary(walletId)))
            return null
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            (e.toPerpsFundingError() as? PerpsFundingError.Refused)
                ?.takeIf { it.reason == REASON_AMOUNT_TOO_LOW }
                ?.minUsdAmount
                ?: return null
        }
        val units = PerpsFundingAmount.toUnits(
            amount = minUsd.divide(usdPrice, source.decimals, RoundingMode.UP),
            decimals = source.decimals,
            rounding = RoundingMode.UP,
        )
        return PerpsDepositMinimum(usd = minUsd, units = units)
    }

    private suspend fun networkFee(source: PerpsDepositSource, operation: Operation): Fee? {
        val account = source.account.data.chainAccount
        val fees = operation.payloads.map { payload ->
            safeCall { provider.estimateFee(payload, account).getOrNull() } ?: return null
        }
        val asset = fees.firstOrNull()?.asset ?: return null
        if (fees.any { it.asset.id != asset.id }) {
            return null
        }
        return Fee.Value(asset, fees.fold(ChainInteger.ZERO) { sum, fee -> sum + fee.amount })
    }

    private suspend fun checkNetworkFee(walletId: String, quote: PerpetualDepositQuote, fee: Fee) {
        val coin = safeCall { mcAccountRepository.findAccount(walletId, fee.asset.id, forceRefresh = true) } ?: return
        val spent = quote.operation.payloads.fold(fee.amount) { sum, payload -> sum + payload.meta.coinValue() }
        if (spent > coin.unitBalance.value) {
            throw PerpsDepositException.InsufficientNetworkFee(coin.asset.symbol)
        }
    }

    private suspend fun feeShareBps(
        walletId: String,
        source: PerpsDepositSource,
        quote: PerpetualDepositQuote,
        fee: Fee?,
    ): Int? {
        val sourcePrice = usdPrice(walletId, source.assetId) ?: return null
        val spentUsd = PerpsFundingAmount.toDecimal(quote.sourceAmount.toJava(), source.decimals).multiply(sourcePrice)
        if (spentUsd.signum() <= 0) {
            return null
        }
        val creditedUsd = PerpsFundingAmount.toDecimal(quote.depositAmount.toJava(), PerpsFundingAmount.USDC_DECIMALS)
        val feeUsd = fee?.let {
            val feePrice = usdPrice(walletId, it.asset.id) ?: return@let BigDecimal.ZERO
            PerpsFundingAmount.toDecimal(it.amount.toJava(), it.asset.decimals.value).multiply(feePrice)
        } ?: BigDecimal.ZERO
        return (spentUsd - creditedUsd + feeUsd)
            .multiply(BPS_DECIMAL)
            .divide(spentUsd, 0, RoundingMode.DOWN)
            .max(BigDecimal.ZERO)
            .toInt()
    }

    private suspend fun usdPrice(walletId: String, assetId: String): BigDecimal? {
        return safeCall { mcAccountRepository.findAccount(walletId, assetId, currencyOverride = USD_CURRENCY) }
            ?.rate
            ?.takeIf { it.currencyCode.equals(USD_CURRENCY, ignoreCase = true) }
            ?.price
            ?.toBigDecimalOrNull()
            ?.takeIf { it.signum() > 0 }
    }

    private suspend fun <T> safeCall(block: suspend () -> T?): T? {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e(e)
            null
        }
    }

    override suspend fun authorize(request: ConfirmRequest, wallet: CryptoWallet): ConfirmRequest {
        val deposit = request.type as ConfirmType.PerpetualDeposit
        val walletId = request.data.walletId
        val source = source(request)
        val beneficiary = deposit.quote.beneficiary
        val quoteRequest = quoteRequest(walletId, source, deposit.amount.toJava(), beneficiary)
        val operation = signPreview(walletId, deposit.operation, wallet)

        val quote = depositQuote(repository.quoteDeposit(walletId, quoteRequest.copy(operation = operation)))
        if (quote.preview) {
            throw PerpsDepositException.QuoteRejected(PerpsQuoteError.UnexpectedOperation(CHANGE_PUB_KEY_PREVIEW))
        }

        checkBalance(source, quote)
        checkConfirmed(deposit, quote)
        return PerpsConfirmRequests.deposit(walletId, source.assetId, beneficiary, quote, preview = false)
    }

    private suspend fun quoteRequest(
        walletId: String,
        source: PerpsDepositSource,
        amountUnits: BigInteger,
        beneficiary: String,
    ): DepositQuoteRequest = DepositQuoteRequest(
        sourceAsset = source.fundingAsset.raw,
        sourceAmount = amountUnits.toString(),
        senderAddress = source.account.data.displayAddress,
        destinationAddress = beneficiary,
        changePubKey = changePubKey(walletId),
    )

    private suspend fun source(request: ConfirmRequest): PerpsDepositSource {
        val assetId = request.data.assetId
        val account = mcAccountRepository.findAccount(request.data.walletId, assetId)
            ?: throw PerpsDepositException.WalletUnavailable()
        val fundingAsset = PerpsFundingAssetId.parse(assetId)
            ?: throw IllegalArgumentException("perps funding: $assetId is not a deposit source")
        return PerpsDepositSource(account, fundingAsset)
    }

    private fun checkBalance(source: PerpsDepositSource, quote: PerpetualDepositQuote) {
        if (!source.fundingAsset.isCoin && quote.sourceAmount.toJava() > source.balanceUnits) {
            throw PerpsDepositException.InsufficientBalance()
        }
    }

    // The preview is what the user confirmed: the deposit quoted on the swipe spends the same, takes the
    // same route, and credits at most MAX_CREDIT_DRIFT_BPS less, the price having moved in between.
    private fun checkConfirmed(preview: ConfirmType.PerpetualDeposit, quote: PerpetualDepositQuote) {
        val changed = when {
            quote.sourceAmount != preview.amount -> "input.amount"
            (quote.route == PerpsDepositRoute.CROSSCHAIN_SWAP) != preview.quote.crossChain -> "provider"
            quote.depositAmount * BPS < preview.quote.depositAmount * (BPS - MAX_CREDIT_DRIFT_BPS) -> "output.min_amount"
            else -> return
        }
        throw PerpsDepositException.QuoteRejected(PerpsQuoteError.Changed(changed))
    }

    private fun depositQuote(quote: DepositQuote): PerpetualDepositQuote {
        return when (val result = perpetualRepository.depositQuote(quote)) {
            is KResult.Ok -> result.value
            is KResult.Err -> throw PerpsDepositException.QuoteRejected(result.value)
        }
    }

    // The preview's payloads are signatures only, made with the key behind destination_address. They go
    // back as the shared Response Signed Payloads chainkit writes, each with its payload's version.
    private suspend fun signPreview(walletId: String, preview: Operation, wallet: CryptoWallet): JsonObject {
        preview.payloads.firstOrNull { it.type !in PREVIEW_PAYLOAD_TYPES }?.let { payload ->
            throw PerpsDepositException.QuoteRejected(PerpsQuoteError.UnexpectedOperation(payload.type.id))
        }

        if (preview.payloads.map { it.type }.sorted() != PREVIEW_PAYLOAD_TYPES) {
            throw PerpsDepositException.QuoteRejected(PerpsQuoteError.UnexpectedOperation(CHANGE_PUB_KEY_PREVIEW))
        }

        val owner = coinAccount(walletId, Chain.Ethereum.Mainnet).chainAccount
        val payloads = preview.payloads.map { payload ->
            val signed = provider.signPayload(
                payload = payload,
                account = owner,
                fee = Fee.Value(owner.asset, ChainInteger.ZERO),
                nonce = ChainInteger.ZERO,
                wallet = wallet,
            ).getOrNull() ?: throw PerpsDepositException.SigningFailed(payload.type.id)

            val data = when (payload.type) {
                Payload.Type.Eip7821HermesDelegate -> JsonPrimitive(signed.data.jsonPrimitive.content.add0x())
                else -> signed.data
            }

            JsonObject(signed.copy(data = data).toJson() + ("version" to JsonPrimitive(payload.version)))
        }

        return buildJsonObject {
            put("id", preview.id)
            put("payloads", JsonArray(payloads))
        }
    }

    // Named on every quote, the backend registering it only where the venue lacks it.
    private suspend fun changePubKey(walletId: String): ChangePubKey {
        // Kept when the wallet is created or imported and on every passcode unlock: never derived here.
        val pubKey = checkNotNull(accountService.keptTradingPublicKey(walletId)) {
            "perps: no trading key kept for $walletId"
        }

        return ChangePubKey(
            accountIndex = LIGHTER_NIL_ACCOUNT_INDEX,
            apiKeyIndex = LIGHTER_API_KEY_INDEX,
            pubKey = pubKey,
        )
    }

    suspend fun relinkWallet(walletId: String, wallet: CryptoWallet): Int {
        val link = signLinkWallet(walletId, wallet)
            ?: throw PerpsDepositException.SigningFailed("LinkWallet")
        val reauthorized = reauthorize(walletId, link)
        recovery.refresh(walletId)
        return reauthorized
    }

    /** Signs LinkWallet with [wallet] and hands it to the deposits stuck waiting for one; null when it can't be signed. */
    internal suspend fun linkWallet(walletId: String, wallet: CryptoWallet): LinkWalletSignature? {
        val link = signLinkWallet(walletId, wallet) ?: return null
        try {
            reauthorize(walletId, link)
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e, "perps funding: re-signing stuck deposits failed for $walletId")
        }
        return link
    }

    private suspend fun reauthorize(walletId: String, link: LinkWalletSignature): Int {
        val stuck = journal.list(walletId).filter { it.stage == PerpsDepositStage.NEEDS_AUTHORIZATION }
        stuck.forEach { record ->
            journal.update(record.operationId, record.revision) { current ->
                current.copy(
                    stage = PerpsDepositStage.REGISTRATION_PENDING,
                    linkSignature = link.signature,
                    linkDeadline = link.deadline,
                    registration = null,
                    failureReason = null,
                )
            }
        }
        return stuck.size
    }

    private suspend fun signLinkWallet(walletId: String, wallet: CryptoWallet): LinkWalletSignature? {
        return try {
            accountService.ensureBound(walletId, wallet)
            val deadline = System.currentTimeMillis() / MILLIS_IN_SECOND + LINK_DEADLINE_SECONDS
            val ethereum = coinAccount(walletId, Chain.Ethereum.Mainnet).chainAccount
            val signed = provider.signMessage(
                data = PerpsAccountProofs.linkWalletTypedData(walletId, deadline),
                type = MessageType.TypedMessage,
                account = ethereum,
                wallet = wallet,
            )?.getOrNull() ?: return null
            LinkWalletSignature(PerpsAccountProofs.normalizeHex(signed, withPrefix = true), deadline)
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e, "perps funding: LinkWallet signature skipped for $walletId")
            null
        }
    }

    private suspend fun coinAccount(walletId: String, chain: Chain): AccountEntity {
        return mcAccountRepository.getCoinAccounts(walletId).firstOrNull { it.chain == chain }
            ?: throw PerpsDepositException.WalletUnavailable()
    }

    internal class LinkWalletSignature(val signature: String, val deadline: Long)

    private sealed interface MaxProbe {
        class Priced(val quote: PerpetualDepositQuote) : MaxProbe
        data object BelowMinimum : MaxProbe
        data object Refused : MaxProbe
    }

    private companion object {
        const val LINK_DEADLINE_SECONDS = 7L * 24L * 60L * 60L
        const val MILLIS_IN_SECOND = 1_000L
        const val MAX_SOURCE_PAGES = 10
        val DEPOSIT_SOURCE_CAPABILITIES = listOf(AssetCapability.PerpDeposit)
        val MAX_FEE_MARGIN: ChainInteger = ChainInteger.TWO
        val BPS: ChainInteger = ChainInteger.fromInt(10_000)
        val MAX_CREDIT_DRIFT_BPS: ChainInteger = ChainInteger.fromInt(100)
        val BPS_DECIMAL: BigDecimal = BigDecimal.valueOf(10_000L)
        val HUNDRED: BigInteger = BigInteger.valueOf(100L)
        val MAX_QUOTE_PERCENTS = listOf(100, 90, 50)
        const val MAX_QUOTED_FEE_MARGIN_PERCENT = 125
        const val MAX_BISECTIONS = 3
        val MINIMUM_PROBE_USD: BigDecimal = BigDecimal("0.5")

        // A change_pub_key preview signs one of each, in whichever order the quote lists them.
        val PREVIEW_PAYLOAD_TYPES = listOf(Payload.Type.Eip7702Authorization, Payload.Type.Eip7821HermesDelegate).sorted()
    }
}

internal fun PerpsFundingRecord.withStep(stepId: String, transform: (PerpsFundingStep) -> PerpsFundingStep): PerpsFundingRecord {
    return copy(
        steps = steps.map {
            if (it.stepId == stepId) {
                transform(it)
            } else {
                it
            }
        },
    )
}

internal fun PerpsFundingRecord.sent(stepId: String, hash: String): PerpsFundingRecord {
    val updated = withStep(stepId) { it.copy(status = PerpsFundingStepStatus.SENT, txHash = hash) }
    val step = updated.steps.first { it.stepId == stepId }
    if (step.kind != PerpsFundingStepKind.MAIN) {
        return updated
    }
    return when (route) {
        PerpsDepositRoute.DIRECT_GATEWAY -> updated.copy(
            l1TxHash = hash.lowercase(),
            stage = PerpsDepositStage.REGISTRATION_PENDING,
        )

        PerpsDepositRoute.CROSSCHAIN_SWAP -> updated.copy(
            sourceTxHash = hash,
            stage = PerpsDepositStage.REGISTRATION_PENDING,
        )
    }
}

internal fun PerpsFundingRecord.settled(stepId: String, outcome: PerpsStepOutcome): PerpsFundingRecord {
    return when (outcome) {
        is PerpsStepOutcome.Sent -> sent(stepId, outcome.hash)
        is PerpsStepOutcome.Failed -> withStep(stepId) { it.copy(status = PerpsFundingStepStatus.FAILED) }
            .copy(stage = PerpsDepositStage.FAILED, failureReason = outcome.reason)

        PerpsStepOutcome.Unknown -> withStep(stepId) { it.copy(status = PerpsFundingStepStatus.UNKNOWN) }
            .copy(stage = PerpsDepositStage.SOURCE_UNKNOWN)
    }
}

internal fun NodeError.isRejection(chainId: String): Boolean {
    return when (this) {
        is NodeError.Rejected, is NodeError.InsufficientFunds, is NodeError.Unauthorized -> true
        is NodeError.NetworkWrap -> value is ChainError.BitcoinMemPoolConflict ||
            (chainId.isTron() && (value as? NetworkError)?.code == TRON_BROADCAST_REFUSED_CODE)
        else -> false
    }
}

private fun String.isTron(): Boolean {
    return Chain.find(substringBefore('/'), substringAfter('/')) is Chain.Tron
}

internal fun BigInteger.toChain(): ChainInteger = ChainInteger.parseString(toString())

internal fun ChainInteger.toJava(): BigInteger = BigInteger(toString())
