package com.tonapps.tonkeeper.ui.screen.staking.stake

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.TransferEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.model.legacy.toAssetId
import com.tonapps.blockchain.model.legacy.toGrams
import com.tonapps.blockchain.ton.TONOpCode
import com.tonapps.blockchain.ton.TonSendMode
import com.tonapps.blockchain.ton.extensions.storeCoins
import com.tonapps.blockchain.ton.extensions.storeOpCode
import com.tonapps.blockchain.ton.extensions.storeQueryId
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.opTerminal
import com.tonapps.core.helper.TON_COIN_ASSET_ID
import com.tonapps.core.helper.TransactionSentAnalytics
import com.tonapps.core.helper.WalletRedMetadata
import com.tonapps.deposit.usecase.emulation.Emulated
import com.tonapps.deposit.usecase.emulation.EmulationUseCase
import com.tonapps.deposit.usecase.sign.SignUseCase
import com.tonapps.extensions.currentTimeMillis
import com.tonapps.extensions.currentTimeSecondsInt
import com.tonapps.extensions.generateUuid
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.ledger.ton.Transaction
import com.tonapps.legacy.enteties.SendMetadataEntity
import com.tonapps.portfolio.analytics.StakingAnalytics
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.toAccountTokenEntity
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.staking.StakingPool
import com.tonapps.wallet.data.staking.StakingRepository
import com.tonapps.wallet.data.staking.entities.PoolEntity
import com.tonapps.wallet.data.staking.entities.PoolInfoEntity
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.data.tx.TransactionManager
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.block.AddrStd
import org.ton.block.StateInit
import org.ton.cell.buildCell
import org.ton.contract.wallet.MessageData
import org.ton.contract.wallet.WalletTransfer
import org.ton.contract.wallet.WalletTransferBuilder
import org.ton.tlb.CellRef
import uikit.extensions.collectFlow

class StakingViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val poolAddress: String,
    private val from: String,
    private val accountRepository: AccountRepository,
    private val stakingRepository: StakingRepository,
    private val tokenRepository: TokenRepository,
    private val ratesRepository: RatesRepository,
    private val settingsRepository: SettingsRepository,
    private val transactionManager: TransactionManager,
    private val signUseCase: SignUseCase,
    private val emulationUseCase: EmulationUseCase,
    private val api: API,
    private val mcAccountRepository: McAccountRepository,
) : BaseWalletVM(app) {

    val installId: String
        get() = settingsRepository.installId

    data class AvailableUiState(
        val balanceFormat: CharSequence,
        val remainingFormat: CharSequence,
        val minStakeFormat: CharSequence,
        val insufficientBalance: Boolean,
        val requestMinStake: Boolean,
        val hiddenBalance: Boolean,
    )

    private val _poolsFlow = MutableStateFlow<List<PoolInfoEntity>?>(null)
    val poolsFlow = _poolsFlow.asStateFlow().filterNotNull().filter { it.isNotEmpty() }

    private val _amountFlow = MutableStateFlow(0.0)
    private val amountFlow = _amountFlow.map { Coins.of(it) }

    private val _selectedPoolFlow = MutableStateFlow<PoolEntity?>(null)
    val selectedPoolFlow = _selectedPoolFlow.asStateFlow().filterNotNull()

    private val _tokenFlow = MutableStateFlow<AccountTokenEntity?>(null)
    val tokenFlow = _tokenFlow.asStateFlow().filterNotNull()

    val availableUiStateFlow = combine(
        amountFlow,
        tokenFlow,
        selectedPoolFlow
    ) { amount, token, pool ->
        val balance = token.balance.value
        val balanceFormat = CurrencyFormatter.format(token.symbol, balance)
        val minStakeFormat = CurrencyFormatter.format(token.symbol, pool.minStake)
        if (amount == Coins.ZERO) {
            AvailableUiState(
                balanceFormat = balanceFormat,
                remainingFormat = balanceFormat,
                minStakeFormat = minStakeFormat,
                insufficientBalance = false,
                requestMinStake = false,
                hiddenBalance = settingsRepository.hiddenBalances,
            )
        } else {
            val remaining = balance - amount
            AvailableUiState(
                balanceFormat = balanceFormat,
                remainingFormat = CurrencyFormatter.format(token.symbol, remaining),
                minStakeFormat = minStakeFormat,
                insufficientBalance = if (remaining.isZero) false else remaining.isNegative,
                requestMinStake = pool.minStake > amount,
                hiddenBalance = settingsRepository.hiddenBalances,
            )
        }
    }

    val fiatFlow = combine(amountFlow, tokenFlow) { amount, token ->
        amount.multiply(token.rateNow)
    }

    val fiatFormatFlow = fiatFlow.map {
        CurrencyFormatter.format(settingsRepository.currency.code, it, replaceSymbol = false)
    }

    val amountFormatFlow = combine(amountFlow, tokenFlow) { amount, token ->
        CurrencyFormatter.formatFull(token.symbol, amount, token.decimals)
    }

    val apyFormatFlow = combine(
        amountFlow,
        tokenFlow,
        selectedPoolFlow,
        poolsFlow
    ) { amount, token, pool, pools ->
        val info = pools.find { it.implementation == pool.implementation } ?: return@combine ""
        val apyFormat = CurrencyFormatter.formatPercent(info.apy)
        if (amount.isPositive) {
            val earning = amount.multiply(pool.apy).divide(100)
            "%s ≈ %s · %s".format(
                getString(Localization.staking_apy),
                apyFormat,
                CurrencyFormatter.format(token.symbol, earning)
            )
        } else {
            "%s ≈ %s".format(
                getString(Localization.staking_apy),
                apyFormat
            )
        }
    }

    init {
        StakingAnalytics.open(from)

        collectFlow(poolsFlow) { pools ->
            if (_selectedPoolFlow.value != null) {
                return@collectFlow
            }

            val poolAddress = poolAddress.ifBlank {
                pools.first().pools.first().address
            }
            pools.map { it.pools }.flatten().find {
                it.address == poolAddress
            }?.let {
                selectPool(it)
            }
        }
        updateAmount(0.0)

        viewModelScope.launch(Dispatchers.IO) {
            launch {
                _poolsFlow.value = stakingRepository.get(wallet.accountId, wallet.network).pools.filter {
                    api.getConfig(wallet.network).enabledStaking.contains(it.implementation.title)
                }
                trackStakeInputIfReady()
            }
            launch {
                _tokenFlow.value = loadTonToken()
                trackStakeInputIfReady()
            }
        }
    }

    private var stakeInputTracked = false

    private fun analyticsProps(): StakingAnalytics.Props? {
        val pool = _selectedPoolFlow.value ?: return null
        val providers = _poolsFlow.value ?: return null
        val token = _tokenFlow.value ?: return null
        val provider = providers.find { item -> item.pools.any { it.address == pool.address } }
        return StakingAnalytics.Props(
            jettonSymbol = token.symbol,
            providerName = provider?.implementation?.title.orEmpty(),
            providerDomain = provider?.details?.url.orEmpty(),
        )
    }

    private fun trackStakeInputIfReady() {
        if (stakeInputTracked) return
        val props = analyticsProps() ?: return
        stakeInputTracked = true
        StakingAnalytics.stakeInput(from, props)
    }

    fun onConfirm() {
        analyticsProps()?.let(StakingAnalytics::stakeConfirm)
    }

    private fun trackStakeSuccess() {
        analyticsProps()?.let(StakingAnalytics::stakeSuccess)
        val pool = _selectedPoolFlow.value
        TransactionSentAnalytics.transactionSent(
            wallet = wallet,
            category = Events.TransactionSent.TransactionSentCategory.Staking,
            categoryDetail = Events.TransactionSent.TransactionSentCategoryDetail.Stake,
            asset = TON_COIN_ASSET_ID,
            amount = _amountFlow.value,
            feeAsset = Events.TransactionSent.TransactionSentFeeAsset.Coin,
            initiatedBy = Events.TransactionSent.TransactionSentInitiatedBy.User,
            stakingProvider = pool?.address,
            isLiquid = pool?.let { it.implementation == StakingPool.Implementation.LiquidTF },
        )
    }

    private suspend fun loadTonToken(): AccountTokenEntity? {
        if (wallet.type == WalletType.Multichain) {
            return loadMultichainTonToken()
        }
        return tokenRepository.getTON(
            settingsRepository.currency,
            wallet.accountId,
            wallet.network,
        )
    }

    private suspend fun loadMultichainTonToken(): AccountTokenEntity? {
        val assetId = WalletCurrency.TON.toAssetId(wallet.testnet)
        val currency = settingsRepository.currency.code
        val cached = runCatching {
            mcAccountRepository.getCachedAccounts(
                walletId = wallet.id,
                currency = currency,
                hideDust = settingsRepository.hideDustAssets,
            )
        }.getOrNull()?.accounts?.firstOrNull { it.asset.id == assetId }

        val account = cached
            ?: runCatching {
                mcAccountRepository.findAccount(wallet.id, assetId, currency)
            }.getOrNull()
            ?: return null
        return account.toAccountTokenEntity(settingsRepository.currency)
    }

    fun requestMax() = tokenFlow.take(1).map {
        it.balance.value
    }

    fun updateAmount(amount: Double) {
        _amountFlow.value = amount
    }

    fun selectPool(pool: PoolEntity) {
        if (_selectedPoolFlow.value?.address == pool.address) return
        _selectedPoolFlow.value = pool
        trackStakeInputIfReady()
    }

    private suspend fun getSendParams(
        wallet: WalletEntity,
    ): SendMetadataEntity = withContext(Dispatchers.IO) {
        val seqnoDeferred = async { accountRepository.getSeqno(wallet) }
        val validUntilDeferred = async { accountRepository.getValidUntil(wallet.network) }

        SendMetadataEntity(
            seqno = seqnoDeferred.await(),
            validUntil = validUntilDeferred.await(),
        )
    }

    private suspend fun buildTransfer(
        wallet: WalletEntity,
        amount: Coins,
        pool: PoolEntity,
        token: TokenEntity,
        sendParams: SendMetadataEntity,
    ): WalletTransfer {
        val stateInitRef = if (0 >= sendParams.seqno) {
            wallet.contract.stateInitRef
        } else {
            null
        }
        val builder = WalletTransferBuilder()
        builder.bounceable = true
        builder.sendMode = (TonSendMode.PAY_GAS_SEPARATELY.value + TonSendMode.IGNORE_ERRORS.value)
        builder.destination = AddrStd.parse(pool.address)
        when (pool.implementation) {
            StakingPool.Implementation.Whales -> builder.applyWhales(amount, stateInitRef)
            StakingPool.Implementation.TF -> builder.applyTF(amount, stateInitRef)
            StakingPool.Implementation.LiquidTF -> builder.applyLiquid(amount, stateInitRef)
            else -> throw IllegalStateException("Unsupported pool implementation: ${pool.implementation}")
        }
        /*val withdrawalFee = Coins.of(StakingUtils.getWithdrawalFee(pool.implementation))
        val coins = if (pool.implementation == StakingPool.Implementation.LiquidTF) {
            org.ton.block.Coins.ofNano(amount.toLong() + withdrawalFee.toLong())
        } else {
            org.ton.block.Coins.ofNano(amount.toLong())
        }

        val builder = WalletTransferBuilder()
        builder.body = buildPayload(pool)
        builder.coins = coins
        if (token.isTon) {
            builder.destination = AddrStd.parse(pool.address)
        } else {
            builder.destination = AddrStd.parse(token.address)
        }
        if (0 >= sendParams.seqno) {
            builder.stateInit = wallet.contract.stateInit
        }*/

        return builder.build()
    }

    private fun unsignedBodyFlow() = combine(
        amountFlow.take(1),
        selectedPoolFlow.take(1),
        tokenFlow.take(1),
    ) { amount, pool, token ->
        val params = getSendParams(wallet)
        val gift = buildTransfer(wallet, amount, pool, token.balance.token, params)
        accountRepository.messageBody(
            wallet = wallet,
            seqNo = params.seqno,
            validUntil = params.validUntil,
            transfers = listOf(gift),
        )
    }.flowOn(Dispatchers.IO)

    private fun ledgerTransactionFlow() = combine(
        amountFlow.take(1),
        selectedPoolFlow.take(1),
        tokenFlow.take(1),
    ) { amount, pool, token ->
        val params = getSendParams(wallet)
        val gift = buildTransfer(wallet, amount, pool, token.balance.token, params)
        val transaction = Transaction.fromWalletTransfer(gift, params.seqno, params.validUntil)
        Pair(params.seqno, transaction)
    }.flowOn(Dispatchers.IO)

    private fun requestFee() = unsignedBodyFlow().map { message ->
        val operationId = generateUuid()
        val startedAtMs = currentTimeMillis()
        AnalyticsHelper.Default.events.redOperations.opAttempt(
            operationId = operationId,
            flow = Events.RedOperations.RedOperationsFlow.Stake,
            operation = Events.RedOperations.RedOperationsOperation.Emulate,
            attemptSource = null,
            startedAtMs = currentTimeSecondsInt(),
            otherMetadata = WalletRedMetadata.walletKit(),
        )
        try {
            val result = emulationUseCase(message, wallet.testnet, params = true).extra
            val finishedAtMs = currentTimeMillis()
            AnalyticsHelper.Default.events.redOperations.opTerminal(
                operationId = operationId,
                flow = Events.RedOperations.RedOperationsFlow.Stake,
                operation = Events.RedOperations.RedOperationsOperation.Emulate,
                durationMs = (finishedAtMs - startedAtMs).toDouble(),
                finishedAtMs = currentTimeSecondsInt(),
                error = null,
                otherMetadata = WalletRedMetadata.walletKit(),
            )
            result
        } catch (e: Throwable) {
            val finishedAtMs = currentTimeMillis()
            AnalyticsHelper.Default.events.redOperations.opTerminal(
                operationId = operationId,
                flow = Events.RedOperations.RedOperationsFlow.Stake,
                operation = Events.RedOperations.RedOperationsOperation.Emulate,
                durationMs = (finishedAtMs - startedAtMs).toDouble(),
                finishedAtMs = currentTimeSecondsInt(),
                error = e,
                otherMetadata = WalletRedMetadata.walletKit(),
            )
            Emulated.defaultExtra
        }
    }.flowOn(Dispatchers.IO)

    fun requestFeeFormat() = combine(
        requestFee(),
        selectedPoolFlow,
    ) { extra, pool ->
        val currency = settingsRepository.currency
        val rates = ratesRepository.getTONRates(wallet.network, currency)
        val fee = StakingPool.getTotalFee(extra.value, pool.implementation)

        val fiat = rates.convertTON(fee)
        val first = CurrencyFormatter.format(TokenEntity.TON.symbol, fee)
        val second = CurrencyFormatter.formatFiat(currency.code, fiat)

        Pair(first, second)
    }

    fun stake(
        context: Context,
    ) = (if (wallet.isLedger) {
        createLedgerStakeFlow(context, wallet)
    } else {
        createStakeFlow(wallet)
    }).flowOn(Dispatchers.IO)

    private fun WalletTransferBuilder.applyLiquid(amount: Coins, stateInitRef: CellRef<StateInit>?) {
        val body = buildCell {
            storeOpCode(TONOpCode.LIQUID_TF_DEPOSIT)
            storeQueryId(TransferEntity.newWalletQueryId())
            storeUInt(0x000000000005b7c1, 64)
        }
        val withdrawalFee = Coins.ONE // TODO fees: will it remain static?
        val amountWithFee = withdrawalFee + amount

        this.coins = amountWithFee.toGrams()
        this.messageData = MessageData.raw(body, stateInitRef)
    }

    private fun WalletTransferBuilder.applyWhales(amount: Coins, stateInitRef: CellRef<StateInit>?) {
        val body = buildCell {
            storeOpCode(TONOpCode.WHALES_DEPOSIT)
            storeQueryId(TransferEntity.newWalletQueryId())
            storeCoins(Coins.of(0.1).toGrams()) // TODO fees: internal fee should be less as well
        }

        this.coins = amount.toGrams() // TODO fees: should we add 0.1 here?
        this.messageData = MessageData.raw(body, stateInitRef)
    }

    private fun WalletTransferBuilder.applyTF(amount: Coins, stateInitRef: CellRef<StateInit>?) {
        val body = buildCell {
            storeUInt(0, 32)
            storeBytes("d".toByteArray())
        }

        this.coins = amount.toGrams()
        this.messageData = MessageData.raw(body, stateInitRef)
    }

    private fun createLedgerStakeFlow(
        context: Context,
        wallet: WalletEntity
    ) = ledgerTransactionFlow().map { (seqno, transaction) ->
        val operationId = generateUuid()
        val startedAtMs = currentTimeMillis()
        AnalyticsHelper.Default.events.redOperations.opAttempt(
            operationId = operationId,
            flow = Events.RedOperations.RedOperationsFlow.Stake,
            operation = Events.RedOperations.RedOperationsOperation.Stake,
            attemptSource = null,
            startedAtMs = currentTimeSecondsInt(),
            otherMetadata = WalletRedMetadata.walletKit(),
        )
        try {
            val message = signUseCase(context, wallet, seqno, transaction)

            transactionManager.send(wallet, message, false, "", 0.0)
            trackStakeSuccess()
            val finishedAtMs = currentTimeMillis()
            AnalyticsHelper.Default.events.redOperations.opTerminal(
                operationId = operationId,
                flow = Events.RedOperations.RedOperationsFlow.Stake,
                operation = Events.RedOperations.RedOperationsOperation.Stake,
                durationMs = (finishedAtMs - startedAtMs).toDouble(),
                finishedAtMs = currentTimeSecondsInt(),
                error = null,
                otherMetadata = WalletRedMetadata.walletKit(),
            )
        } catch (e: Throwable) {
            val finishedAtMs = currentTimeMillis()
            AnalyticsHelper.Default.events.redOperations.opTerminal(
                operationId = operationId,
                flow = Events.RedOperations.RedOperationsFlow.Stake,
                operation = Events.RedOperations.RedOperationsOperation.Stake,
                durationMs = (finishedAtMs - startedAtMs).toDouble(),
                finishedAtMs = currentTimeSecondsInt(),
                error = e,
                otherMetadata = WalletRedMetadata.walletKit(),
            )
            throw e
        }
    }

    private fun createStakeFlow(
        wallet: WalletEntity
    ) = unsignedBodyFlow().map { message ->
        val operationId = generateUuid()
        val startedAtMs = currentTimeMillis()
        AnalyticsHelper.Default.events.redOperations.opAttempt(
            operationId = operationId,
            flow = Events.RedOperations.RedOperationsFlow.Stake,
            operation = Events.RedOperations.RedOperationsOperation.Stake,
            attemptSource = null,
            startedAtMs = currentTimeSecondsInt(),
            otherMetadata = WalletRedMetadata.walletKit(),
        )
        try {
            val cell = message.createUnsignedBody(false)
            val boc = signUseCase(context, wallet, cell, message.seqNo)
            transactionManager.send(wallet, boc, false, "", 0.0)
            trackStakeSuccess()
            val finishedAtMs = currentTimeMillis()
            AnalyticsHelper.Default.events.redOperations.opTerminal(
                operationId = operationId,
                flow = Events.RedOperations.RedOperationsFlow.Stake,
                operation = Events.RedOperations.RedOperationsOperation.Stake,
                durationMs = (finishedAtMs - startedAtMs).toDouble(),
                finishedAtMs = currentTimeSecondsInt(),
                error = null,
                otherMetadata = WalletRedMetadata.walletKit(),
            )
        } catch (e: Throwable) {
            val finishedAtMs = currentTimeMillis()
            AnalyticsHelper.Default.events.redOperations.opTerminal(
                operationId = operationId,
                flow = Events.RedOperations.RedOperationsFlow.Stake,
                operation = Events.RedOperations.RedOperationsOperation.Stake,
                durationMs = (finishedAtMs - startedAtMs).toDouble(),
                finishedAtMs = currentTimeSecondsInt(),
                error = e,
                otherMetadata = WalletRedMetadata.walletKit(),
            )
            throw e
        }
    }

    fun getAmount(): Double {
        return _amountFlow.value
    }
}