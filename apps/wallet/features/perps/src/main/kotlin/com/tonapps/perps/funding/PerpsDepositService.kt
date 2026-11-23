package com.tonapps.perps.funding

import com.ionspin.kotlin.bignum.integer.BigInteger as ChainInteger
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.blockchain.utils.add0x
import com.tonapps.bus.generated.Events.Perps.PerpsSourceScreen
import com.tonapps.chainkit.core.chain.api.models.ChainError
import com.tonapps.chainkit.core.chain.api.models.ChainRes
import com.tonapps.chainkit.core.chain.api.models.NodeError
import com.tonapps.chainkit.core.chain.model.MessageType
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.core.common.NetworkError
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.chainkit.transaction.GasReservePolicy
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.QuoteChangedException
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.coinValue
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeLogic
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualDepositAuthorizer
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualDepositFeeMethods
import com.tonapps.log.L
import com.tonapps.mvi.graph.KResult
import com.tonapps.perps.data.LIGHTER_API_KEY_INDEX
import com.tonapps.perps.data.PerpsLoadException
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.perps.data.USD_CURRENCY
import com.tonapps.perps.data.toWireDecimalOrNull
import com.tonapps.perps.security.PerpsSecurityConfig
import com.tonapps.perps.security.PerpsSecurityConfigRepository
import com.tonapps.perps.trade.PerpsAccountProofs
import com.tonapps.perps.trade.PerpsAccountService
import com.tonapps.time.TimeProvider
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.data.multichain.account.AccountEntity
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.AssetCapability
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.fees.NetworkFeesRepository
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
import io.perpsapi.models.DepositQuote
import io.perpsapi.models.DepositQuoteRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.concurrent.ConcurrentHashMap

private const val EMPTY_CALLDATA = "0x"
private const val CHANGE_PUB_KEY_PREVIEW = "change_pub_key preview"
private const val PERPS_SECURITY = "perps_security"
private const val BITCOIN_FEE_RATE_PROBE = "bitcoin fee rate"
private const val TRON_BANDWIDTH_PROBE = "tron bandwidth"
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
    private val feeMethods: PerpetualDepositFeeMethods,
    private val timeProvider: TimeProvider,
    private val networkFees: NetworkFeesRepository,
    private val securityConfig: PerpsSecurityConfigRepository,
) : PerpetualDepositAuthorizer {

    private val walletsWithAccount = ConcurrentHashMap.newKeySet<String>()

    suspend fun sources(walletId: String): PerpsDepositSources {
        val currency = settingsRepository.currency.code
        var fresh = true
        val accounts = try {
            fetchAllAccounts(walletId, currency)
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e)
            fresh = false
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
        val missing = coroutineScope {
            val bestValue = async {
                if (sources.any { it.isBestValue }) {
                    null
                } else {
                    fetchSource(walletId, PerpsFundingAssetId.USDC_ETHEREUM, currency)
                }
            }
            val usdtTon = async {
                if (sources.any { it.isUsdtTon }) {
                    null
                } else {
                    fetchSource(walletId, PerpsFundingAssetId.USDT_TON, currency)?.takeIf { it.balanceUnits.signum() > 0 }
                }
            }
            listOfNotNull(bestValue.await(), usdtTon.await())
        }
        return PerpsDepositSources(
            items = (sources + missing).sortedByDescending { it.fiatValue },
            fresh = fresh,
        )
    }

    private suspend fun fetchSource(walletId: String, assetId: String, currency: String): PerpsDepositSource? {
        val account = try {
            mcAccountRepository.findAccount(
                walletId = walletId,
                assetId = assetId,
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

    suspend fun acknowledgeDepositStatuses(operationIds: Set<String>) {
        journal.acknowledgeDepositStatuses(operationIds)
    }

    suspend fun dismissOutcomes(walletId: String, kind: PerpsFundingKind? = null) {
        val outcomes = journal.list(walletId)
            .filter { it.stage == PerpsDepositStage.FAILED || it.stage == PerpsDepositStage.REFUNDED }
            .filter { kind == null || it.kind == kind }
            .map { it.operationId }
        if (outcomes.isNotEmpty()) {
            journal.delete(outcomes)
        }
    }

    /** Loads `/network-fees` as the deposit screen opens, so that MAX finds them in the cache. */
    suspend fun loadNetworkFees() {
        networkFees.load()
    }

    suspend fun maxAmount(walletId: String, source: PerpsDepositSource): BigInteger {
        val balance = source.balanceUnits
        if (!source.fundingAsset.isCoin || balance.signum() <= 0) {
            return balance
        }

        // GRAM keeps a fixed 1 GRAM for gas, as the swap does, whatever `/network-fees` says.
        if (source.account.value.chain is Chain.Ton) {
            return (balance - TON_MAX_RESERVE_UNITS).coerceAtLeast(BigInteger.ZERO)
        }

        // The fees the deposit screen loaded: MAX never waits on the network for them.
        val reserve = networkFees.fees.value?.perpsReserve(source.assetId)
        if (reserve != null) {
            return reservedMaxAmount(walletId, source, reserve)
        }

        if (source.account.value.chain !is Chain.Tvm) {
            quotedMaxAmount(walletId, source)?.let { return it }
        }
        return probedMaxAmount(source)
    }

    // The balance less the `/network-fees` safe fee, in place of the quotes and probes. That fee is the gas the
    // wallet pays itself, so a relayer that pays it (battery or a token) still leaves the whole balance, on the
    // chains whose MAX asks for that.
    private suspend fun reservedMaxAmount(walletId: String, source: PerpsDepositSource, reserve: BigInteger): BigInteger {
        val balance = source.balanceUnits
        if (source.account.value.chain !is Chain.Tvm && relayerPaysWholeBalance(walletId, source)) {
            return balance
        }

        return (balance - reserve).coerceAtLeast(BigInteger.ZERO)
    }

    private suspend fun relayerPaysWholeBalance(walletId: String, source: PerpsDepositSource): Boolean {
        val balance = source.balanceUnits
        val beneficiary = beneficiary(walletId)
        val (registration, security) = try {
            keyRegistration(walletId) to verifiedSecurity(source)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e(e, "perps funding: no key registration or security block to probe MAX of ${source.assetId}")
            return false
        }

        val probe = probeQuote(walletId, source, balance, beneficiary, registration, security) as? MaxProbe.Priced
            ?: return false
        if (probe.quote.preview) {
            return false
        }

        val fee = networkFee(source, probe.quote.operation) ?: return false
        val payableByRelayer = payableByRelayer(source, registersKey = probe.registersKey)
        return relayerPaysFee(walletId, source, balance, beneficiary, probe.quote, fee, payableByRelayer)
    }

    private suspend fun quotedMaxAmount(walletId: String, source: PerpsDepositSource): BigInteger? {
        val balance = source.balanceUnits
        val beneficiary = beneficiary(walletId)
        val (registration, security) = try {
            keyRegistration(walletId) to verifiedSecurity(source)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e(e, "perps funding: no key registration or security block to quote MAX of ${source.assetId}")
            return null
        }
        for (percent in MAX_QUOTE_PERCENTS) {
            val amount = balance * percent.toBigInteger() / HUNDRED
            val probe = when (val probe = probeQuote(walletId, source, amount, beneficiary, registration, security)) {
                is MaxProbe.Priced -> probe
                MaxProbe.BelowMinimum -> return balance
                MaxProbe.Refused -> continue
            }
            val quote = probe.quote
            if (quote.preview) {
                return null
            }
            val fee = networkFee(source, quote.operation) ?: return null
            val payableByRelayer = payableByRelayer(source, registersKey = probe.registersKey)
            if (amount == balance && relayerPaysFee(walletId, source, amount, beneficiary, quote, fee, payableByRelayer)) {
                return balance
            }
            val reserve = fee.amount.toJava() * MAX_QUOTED_FEE_MARGIN_PERCENT.toBigInteger() / HUNDRED
            val candidate = (balance - reserve).coerceAtLeast(BigInteger.ZERO)
            if (source.account.value.chain is Chain.Bitcoin && candidate > amount) {
                return largestQuotable(walletId, source, beneficiary, registration, security, quoted = amount, candidate = candidate)
            }
            return candidate
        }
        return null
    }

    private suspend fun relayerPaysFee(
        walletId: String,
        source: PerpsDepositSource,
        amount: BigInteger,
        beneficiary: String,
        quote: PerpetualDepositQuote,
        fee: Fee,
        payableByRelayer: Boolean,
    ): Boolean {
        val coin = safeCall { mcAccountRepository.findAccount(walletId, fee.asset.id, forceRefresh = true) } ?: return false
        val request = PerpsConfirmRequests.deposit(
            walletId = walletId,
            sourceAssetId = source.assetId,
            amountUnits = amount,
            beneficiary = beneficiary,
            quote = quote,
            sourceScreen = null,
            serverNowMs = timeProvider.currentTimeMillis(),
            payableByRelayer = payableByRelayer,
        )
        val selected = safeCall { feeMethods.build(request, source.account, coin, fee).resolve(pickedId = null) }
            ?: return false
        return selected.viaRelayer && TxFeeLogic.isSufficient(selected)
    }

    private suspend fun largestQuotable(
        walletId: String,
        source: PerpsDepositSource,
        beneficiary: String,
        registration: PerpsKeyRegistration?,
        security: PerpsSecurityConfig?,
        quoted: BigInteger,
        candidate: BigInteger,
    ): BigInteger {
        if (probeQuote(walletId, source, candidate, beneficiary, registration, security) is MaxProbe.Priced) {
            return candidate
        }
        var priced = quoted
        var refused = candidate
        repeat(MAX_BISECTIONS) {
            val middle = (priced + refused) / BigInteger.TWO
            if (middle <= priced) {
                return priced
            }
            if (probeQuote(walletId, source, middle, beneficiary, registration, security) is MaxProbe.Priced) {
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
        registration: PerpsKeyRegistration?,
        security: PerpsSecurityConfig?,
    ): MaxProbe {
        return try {
            val request = quoteRequest(source, amountUnits, beneficiary, registration)
            val quote = requestQuote(walletId, request)
            val priced = if (quote.preview) {
                paymentQuote(walletId, request) ?: return MaxProbe.Refused
            } else {
                quote
            }
            MaxProbe.Priced(readPayment(source, priced, amountUnits, beneficiary, registration, security).quote, registersKey = quote.preview)
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
        sourceScreen: PerpsSourceScreen?,
    ): ConfirmRequest = quote(walletId, source, amountUnits, sourceScreen).request

    suspend fun quote(
        walletId: String,
        source: PerpsDepositSource,
        amountUnits: BigInteger,
        sourceScreen: PerpsSourceScreen?,
    ): PerpsDepositQuote {
        val beneficiary = beneficiary(walletId)
        val registration = keyRegistration(walletId)
        val quoteRequest = quoteRequest(source, amountUnits, beneficiary, registration)
        val quoted = requestQuote(walletId, quoteRequest)
        val security = verifiedSecurity(source)
        if (registration != null && quoted.preview) {
            readPreview(quoted.operation, beneficiary, source, registration, requireSecurity(security))
        }
        val quotedPayment = if (quoted.preview) {
            null
        } else {
            readPayment(source, quoted, amountUnits, beneficiary, registration, security)
        }
        val quote = quotedPayment?.quote ?: quoted
        checkBalance(source, quote)
        val checkedPayment = if (quote.preview) {
            paymentQuote(walletId, quoteRequest)?.let { readPayment(source, it, amountUnits, beneficiary, registration, security) }
        } else {
            null
        }
        val payment = checkedPayment?.quote
        val verifiedPayment = checkedPayment?.verified ?: quotedPayment?.verified
        val payableByRelayer = payableByRelayer(source, registersKey = quote.preview)
        val request = PerpsConfirmRequests.deposit(
            walletId = walletId,
            sourceAssetId = source.assetId,
            amountUnits = amountUnits,
            beneficiary = beneficiary,
            quote = quote,
            sourceScreen = sourceScreen,
            serverNowMs = timeProvider.currentTimeMillis(),
            preview = quote.preview,
            payment = payment,
            payableByRelayer = payableByRelayer,
            verifiedPayment = verifiedPayment,
        )
        val paid = payment ?: quote.takeUnless { it.preview } ?: return PerpsDepositQuote(request, networkFeeUsd = null)
        val fee = networkFee(source, paid.operation) ?: return PerpsDepositQuote(request, networkFeeUsd = null)
        val paidRequest = if (payment == null) {
            request
        } else {
            PerpsConfirmRequests.deposit(
                walletId = walletId,
                sourceAssetId = source.assetId,
                amountUnits = amountUnits,
                beneficiary = beneficiary,
                quote = payment,
                sourceScreen = sourceScreen,
                serverNowMs = timeProvider.currentTimeMillis(),
                payableByRelayer = payableByRelayer,
            )
        }
        checkNetworkFee(walletId, source, paidRequest, paid, fee)
        return PerpsDepositQuote(
            request = request,
            networkFeeUsd = feeUsd(walletId, fee),
            paysFeeInCharges = paysFeeInCharges(walletId, source, paidRequest, fee),
        )
    }

    private suspend fun paysFeeInCharges(
        walletId: String,
        source: PerpsDepositSource,
        request: ConfirmRequest,
        fee: Fee,
    ): Boolean {
        val coin = safeCall { mcAccountRepository.findAccount(walletId, fee.asset.id) } ?: return false
        val selected = safeCall { feeMethods.build(request, source.account, coin, fee).resolve(pickedId = null) }
        return selected != null && selected.isBattery && !selected.isUnpriced
    }

    private suspend fun feeUsd(walletId: String, fee: Fee): BigDecimal? {
        val price = usdPrice(walletId, fee.asset.id) ?: return null
        return PerpsFundingAmount.toDecimal(fee.amount.toJava(), fee.asset.decimals.value).multiply(price)
    }

    private suspend fun paymentQuote(walletId: String, quoteRequest: DepositQuoteRequest): PerpetualDepositQuote? {
        return safeCall { requestQuote(walletId, quoteRequest.copy(changePubKey = null)) }
    }

    private fun payableByRelayer(source: PerpsDepositSource, registersKey: Boolean): Boolean {
        return !registersKey || !source.isOnEthereum
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
            repository.quoteDeposit(walletId, quoteRequest(source, probeUnits, beneficiary(walletId), keyRegistration(walletId)))
            return null
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            val refused = e.toPerpsFundingError() as? PerpsFundingError.Refused
            if (refused?.blocksSender == true) {
                throw PerpsDepositException.AddressBlocked()
            }
            if (refused?.regionRestricted == true) {
                throw e
            }
            refused
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

    private suspend fun checkNetworkFee(
        walletId: String,
        source: PerpsDepositSource,
        request: ConfirmRequest,
        quote: PerpetualDepositQuote,
        fee: Fee,
    ) {
        val cached = safeCall { mcAccountRepository.findAccount(walletId, fee.asset.id) } ?: return
        val spent = quote.operation.payloads.fold(fee.amount) { sum, payload -> sum + payload.meta.coinValue() }
        if (spent <= cached.unitBalance.value) {
            return
        }
        val coin = safeCall { mcAccountRepository.findAccount(walletId, fee.asset.id, forceRefresh = true) } ?: cached
        if (spent <= coin.unitBalance.value) {
            return
        }
        val relayable = safeCall { feeMethods.canRelayShortfall(request, source.account, coin, fee) } == true
        if (!relayable) {
            throw PerpsDepositException.InsufficientNetworkFee(
                symbol = coin.asset.symbol,
                required = PerpsFundingAmount.toDecimal(spent.toJava(), fee.asset.decimals.value).stripTrailingZeros(),
            )
        }
    }

    private suspend fun usdPrice(walletId: String, assetId: String): BigDecimal? {
        return safeCall { mcAccountRepository.findAccount(walletId, assetId, currencyOverride = USD_CURRENCY) }
            ?.rate
            ?.takeIf { it.currencyCode.equals(USD_CURRENCY, ignoreCase = true) }
            ?.price
            .toWireDecimalOrNull()
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

    override suspend fun authorize(request: ConfirmRequest, wallet: MnemonicWallet): ConfirmRequest {
        val deposit = request.type as ConfirmType.PerpetualDeposit
        val walletId = request.data.walletId
        val source = sourceOf(request)
        val beneficiary = deposit.quote.beneficiary
        val registration = keptKeyRegistration(walletId)
        val quoteRequest = quoteRequest(source, deposit.amount.toJava(), beneficiary, registration)
        val security = requireSecurity(verifiedSecurity(source))
        val preview = readPreview(deposit.operation, beneficiary, source, registration, security)
        val operation = signPreview(walletId, preview, wallet)

        val checked = readPayment(
            source = source,
            quote = explainingQuoteFailure { requestQuote(walletId, quoteRequest.copy(operation = operation)) },
            amountUnits = deposit.amount.toJava(),
            beneficiary = beneficiary,
            registration = registration,
            security = security,
        )
        val quote = checked.quote
        val verifiedPayment = checked.verified
        checkBalance(source, quote)
        val authorized = PerpsConfirmRequests.deposit(
            walletId = walletId,
            sourceAssetId = source.assetId,
            amountUnits = deposit.amount.toJava(),
            beneficiary = beneficiary,
            quote = quote,
            sourceScreen = deposit.sourceScreen,
            serverNowMs = timeProvider.currentTimeMillis(),
            preview = false,
            payableByRelayer = payableByRelayer(source, registersKey = true),
            verifiedPayment = verifiedPayment,
        )
        checkConfirmed(deposit, quote, authorized, verifiedPayment)
        return authorized
    }

    override suspend fun requote(request: ConfirmRequest): ConfirmRequest {
        val deposit = request.type as ConfirmType.PerpetualDeposit
        val source = sourceOf(request)
        return explainingQuoteFailure {
            confirmRequest(request.data.walletId, source, deposit.amount.toJava(), deposit.sourceScreen)
        }
    }

    private suspend fun <T> explainingQuoteFailure(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val confirmationError = when (val error = e.toDepositQuoteError()) {
                is PerpsDepositQuoteError.BelowMinimum -> ConfirmationError.DepositBelowMinimum(error.minUsd)
                PerpsDepositQuoteError.ServiceUnavailable -> ConfirmationError.NetworkError
                PerpsDepositQuoteError.InsufficientBalance -> ConfirmationError.InsufficientBalance
                is PerpsDepositQuoteError.InsufficientNetworkFee -> ConfirmationError.InsufficientNetworkFee(error.symbol, error.required)
                PerpsDepositQuoteError.QuoteFailed,
                PerpsDepositQuoteError.AddressBlocked,
                PerpsDepositQuoteError.Failed -> null
            }
            throw confirmationError?.let { PerpsDepositException.QuoteRefused(it, e) } ?: e
        }
    }

    private fun quoteRequest(
        source: PerpsDepositSource,
        amountUnits: BigInteger,
        beneficiary: String,
        registration: PerpsKeyRegistration?,
    ): DepositQuoteRequest = DepositQuoteRequest(
        sourceAsset = source.fundingAsset.raw,
        sourceAmount = amountUnits.toString(),
        senderAddress = source.account.data.displayAddress,
        destinationAddress = beneficiary,
        changePubKey = registration?.request(),
    )

    private suspend fun requestQuote(walletId: String, request: DepositQuoteRequest): PerpetualDepositQuote {
        val quote = depositQuote(repository.quoteDeposit(walletId, request))
        if (quote.preview && (request.changePubKey == null || request.operation != null)) {
            throw PerpsDepositException.QuoteRejected(PerpsQuoteError.UnexpectedOperation(CHANGE_PUB_KEY_PREVIEW))
        }
        return quote
    }

    private suspend fun readPayment(
        source: PerpsDepositSource,
        quote: PerpetualDepositQuote,
        amountUnits: BigInteger,
        beneficiary: String,
        registration: PerpsKeyRegistration?,
        security: PerpsSecurityConfig?,
    ): CheckedPayment {
        val chain = source.account.value.chain
        return when {
            source.isUsdtTon -> {
                val sent = PerpsTonJettonDepositReader.read(quote.provider, quote.operation, amountUnits, source.account.data.displayAddress)
                CheckedPayment(quote, ConfirmType.PerpetualDeposit.VerifiedPayment(provider = OMNISTON_PROVIDER, amount = sent.toChain()))
            }

            source.isOnTon -> {
                val sent = PerpsTonDepositReader.read(quote.provider, quote.operation, amountUnits, security)
                CheckedPayment(quote, ConfirmType.PerpetualDeposit.VerifiedPayment(provider = RELAY_PROVIDER, amount = sent.toChain()))
            }

            chain.isEvm || chain is Chain.Tron -> {
                val verified = PerpsChainDepositReader.read(
                    source = source,
                    provider = quote.provider,
                    payment = quote.operation,
                    amountUnits = amountUnits,
                    beneficiary = beneficiary,
                    registration = registration,
                    security = security,
                )
                val priced = if (chain is Chain.Tron) {
                    quote.copy(operation = withTronNetworkFee(source, quote.operation))
                } else {
                    quote
                }
                CheckedPayment(quote = priced, verified = verified)
            }

            chain is Chain.Bitcoin -> {
                val deposit = PerpsBtcDepositReader.read(quote.provider, quote.operation, source.account.data.displayAddress, amountUnits, security)
                PerpsBtcDepositReader.checkFeeRate(deposit, bitcoinFeeRate(source, deposit))
                CheckedPayment(
                    quote = quote.copy(operation = deposit.operation),
                    verified = ConfirmType.PerpetualDeposit.VerifiedPayment(provider = RELAY_PROVIDER, amount = deposit.sent.toChain()),
                )
            }

            else -> {
                PerpsKeyRegistrationReader.readPayment(quote.operation, security)
                CheckedPayment(quote, verified = null)
            }
        }
    }

    private suspend fun bitcoinFeeRate(source: PerpsDepositSource, deposit: PerpsBtcDeposit): BigInteger? {
        val account = source.account.data.chainAccount
        val payment = Payload(
            id = BITCOIN_FEE_RATE_PROBE,
            type = Payload.Type.Main,
            chain = account.chain,
            version = Payload.VERSION,
            fee = null,
            meta = PayloadMeta.Bitcoin.Main(to = account.address, value = deposit.sent.toChain()),
        )
        val fee = safeCall { provider.estimateFee(payment, account).getOrNull() } as? Fee.Gas
        return fee?.price?.toJava()
    }

    private suspend fun withTronNetworkFee(source: PerpsDepositSource, payment: Operation): Operation {
        val account = source.account.data.chainAccount
        val coinId = account.chain.toAsset().id
        val payloads = payment.payloads.map { payload ->
            val quotedFee = payload.quotedFeeIn(coinId) ?: return@map payload
            val networkFee = walletNetworkFee(payload, account) ?: (quotedFee.amount + tronBandwidthFee(account))
            if (networkFee > quotedFee.amount) {
                payload.copy(fee = quotedFee.copy(amount = networkFee))
            } else {
                payload
            }
        }
        return if (payloads == payment.payloads) {
            payment
        } else {
            Operation.build(payment.id, payloads)
        }
    }

    private fun Payload.quotedFeeIn(coinId: String): Fee.Value? {
        return (fee as? Fee.Value)?.takeIf { it.asset.id == coinId }
    }

    private suspend fun walletNetworkFee(payload: Payload, account: Account): ChainInteger? {
        return estimateTronFee(payload.copy(fee = null), account)?.getOrNull()?.amount
    }

    private suspend fun tronBandwidthFee(account: Account): ChainInteger {
        val selfTransfer = Payload(
            id = TRON_BANDWIDTH_PROBE,
            type = Payload.Type.Main,
            chain = account.chain,
            version = Payload.VERSION,
            fee = null,
            meta = PayloadMeta.Tron.Main(to = account.address, value = ChainInteger.ZERO),
        )
        return estimateTronFee(selfTransfer, account)?.getOrNull()?.amount ?: throw PerpsDepositException.NetworkFeeUnavailable()
    }

    private suspend fun estimateTronFee(payload: Payload, account: Account): ChainRes<Fee>? {
        val estimate = safeCall { provider.estimateFee(payload, account) }
        currentCoroutineContext().ensureActive()
        estimate?.error?.let { L.e(it, "perps funding: no wallet estimate of ${payload.id} on ${account.chain.caip2}") }
        return estimate
    }

    private suspend fun verifiedSecurity(source: PerpsDepositSource): PerpsSecurityConfig? {
        if (!source.account.value.chain.isEvm || source.isBestValue) {
            return securityConfig.verified()
        }
        val chainKey = source.account.value.chain.network.type.id
        return securityConfig.verified { it.evmRelayRouters[chainKey].orEmpty().isNotEmpty() }
    }

    private fun requireSecurity(security: PerpsSecurityConfig?): PerpsSecurityConfig {
        return security ?: throw PerpsDepositException.QuoteRejected(PerpsQuoteError.MalformedKeyRegistration(PERPS_SECURITY))
    }

    private fun readPreview(
        preview: Operation,
        owner: String,
        source: PerpsDepositSource,
        registration: PerpsKeyRegistration,
        security: PerpsSecurityConfig,
    ): PerpsKeyRegistrationPreview {
        return PerpsKeyRegistrationReader.read(preview, owner, source, registration, security, timeProvider.currentTimeSeconds())
    }

    suspend fun sourceOf(request: ConfirmRequest): PerpsDepositSource {
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
    private fun checkConfirmed(
        preview: ConfirmType.PerpetualDeposit,
        quote: PerpetualDepositQuote,
        authorized: ConfirmRequest,
        signedPayment: ConfirmType.PerpetualDeposit.VerifiedPayment?,
    ) {
        val mismatch = when {
            quote.sourceAmount != preview.amount -> "input.amount"
            (quote.route == PerpsDepositRoute.CROSSCHAIN_SWAP) != preview.quote.crossChain -> "provider"
            else -> null
        }
        if (mismatch != null) {
            throw PerpsDepositException.QuoteRejected(PerpsQuoteError.Changed(mismatch))
        }
        if (quote.depositAmount * BPS < preview.quote.depositAmount * (BPS - MAX_CREDIT_DRIFT_BPS)) {
            throw QuoteChangedException(authorized, "output.min_amount")
        }
        if ((preview.verifiedPayment == null) != (signedPayment == null)) {
            throw QuoteChangedException(authorized, "payment.value")
        }
        val shownFee = preview.paymentOperation?.prebuiltFee()
        val signedFee = (authorized.type as? ConfirmType.PerpetualDeposit)?.operation?.prebuiltFee()
        if (shownFee != null && signedFee != null && signedFee * BPS > shownFee * (BPS + MAX_FEE_DRIFT_BPS)) {
            throw QuoteChangedException(authorized, "payment.fee")
        }
    }

    private fun Operation.prebuiltFee(): ChainInteger? {
        return payloads.singleOrNull()?.takeIf { it.meta is PayloadMeta.Bitcoin.Prebuild }?.fee?.amount
    }

    private fun depositQuote(quote: DepositQuote): PerpetualDepositQuote {
        return when (val result = perpetualRepository.depositQuote(quote)) {
            is KResult.Ok -> result.value
            is KResult.Err -> throw PerpsDepositException.QuoteRejected(result.value)
        }
    }

    private suspend fun signPreview(walletId: String, preview: PerpsKeyRegistrationPreview, wallet: MnemonicWallet): JsonObject {
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
            put("id", preview.operationId)
            put("payloads", JsonArray(payloads))
        }
    }

    private suspend fun keyRegistration(walletId: String): PerpsKeyRegistration? {
        if (walletId in walletsWithAccount) {
            return null
        }
        val account = when (val result = repository.account(walletId)) {
            is KResult.Ok -> result.value
            is KResult.Err -> throw PerpsLoadException(result.value)
        }
        if (account.accountIndex != null) {
            walletsWithAccount += walletId
            return null
        }
        return keptKeyRegistration(walletId)
    }

    private suspend fun keptKeyRegistration(walletId: String): PerpsKeyRegistration {
        val pubKey = checkNotNull(accountService.keptTradingPublicKey(walletId)) {
            "perps: no trading key kept for $walletId"
        }
        return PerpsKeyRegistration.of(LIGHTER_API_KEY_INDEX, pubKey)
            ?: error("perps: the trading key kept for $walletId is malformed")
    }

    suspend fun relinkWallet(walletId: String, wallet: MnemonicWallet): Int {
        val link = signLinkWallet(walletId, wallet)
            ?: throw PerpsDepositException.SigningFailed("LinkWallet")
        val reauthorized = reauthorize(walletId, link)
        recovery.refresh(walletId)
        return reauthorized
    }

    /** Signs LinkWallet with [wallet] and hands it to the deposits stuck waiting for one; null when it can't be signed. */
    internal suspend fun linkWallet(walletId: String, wallet: MnemonicWallet): LinkWalletSignature? {
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

    private suspend fun signLinkWallet(walletId: String, wallet: MnemonicWallet): LinkWalletSignature? {
        return try {
            accountService.ensureBound(walletId, wallet)
            val deadline = timeProvider.currentTimeSeconds() + LINK_DEADLINE_SECONDS
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

    private class CheckedPayment(
        val quote: PerpetualDepositQuote,
        val verified: ConfirmType.PerpetualDeposit.VerifiedPayment?,
    )

    private sealed interface MaxProbe {
        class Priced(val quote: PerpetualDepositQuote, val registersKey: Boolean) : MaxProbe
        data object BelowMinimum : MaxProbe
        data object Refused : MaxProbe
    }

    private companion object {
        const val LINK_DEADLINE_SECONDS = 7L * 24L * 60L * 60L
        const val MAX_SOURCE_PAGES = 10
        val DEPOSIT_SOURCE_CAPABILITIES = listOf(AssetCapability.PerpDeposit)
        val MAX_FEE_MARGIN: ChainInteger = ChainInteger.TWO
        val BPS: ChainInteger = ChainInteger.fromInt(10_000)
        val MAX_CREDIT_DRIFT_BPS: ChainInteger = ChainInteger.fromInt(100)
        val MAX_FEE_DRIFT_BPS: ChainInteger = ChainInteger.fromInt(100)
        val HUNDRED: BigInteger = BigInteger.valueOf(100L)
        val MAX_QUOTE_PERCENTS = listOf(100, 90, 50)
        const val MAX_QUOTED_FEE_MARGIN_PERCENT = 125
        const val MAX_BISECTIONS = 3
        val TON_MAX_RESERVE_UNITS: BigInteger = BigInteger.valueOf(1_000_000_000L)
        val MINIMUM_PROBE_USD: BigDecimal = BigDecimal("0.5")
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

internal fun PerpsFundingRecord.relayed(stepId: String, hash: String?): PerpsFundingRecord {
    if (hash != null) {
        return sent(stepId, hash)
    }
    if (route == PerpsDepositRoute.DIRECT_GATEWAY) {
        return settled(stepId, PerpsStepOutcome.Unknown)
    }
    return withStep(stepId) { it.copy(status = PerpsFundingStepStatus.SENT) }
        .copy(stage = PerpsDepositStage.REGISTRATION_PENDING)
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
        // The nonce may be this step's own transaction, mined behind a lagging lookup (a resend of a
        // first broadcast whose answer was lost): it stays unknown, for recovery to look it up.
        is NodeError.NonceTaken -> false
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
