package com.tonapps.tonkeeper.ui.screen.staking.stake

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.flattenFirst
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.ledger.ton.Transaction
import com.tonapps.tonkeeper.api.totalFees
import com.tonapps.tonkeeper.core.entities.SendMetadataEntity
import com.tonapps.tonkeeper.core.entities.TransferEntity
import com.tonapps.tonkeeper.extensions.signLedgerTransaction
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.send.main.SendException
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.staking.StakingPool
import com.tonapps.wallet.data.staking.StakingRepository
import com.tonapps.wallet.data.staking.StakingUtils
import com.tonapps.wallet.data.staking.entities.PoolEntity
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.localization.Localization
import io.ktor.util.reflect.instanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withContext
import org.ton.block.AddrStd
import org.ton.cell.Cell
import org.ton.contract.wallet.WalletTransfer
import org.ton.contract.wallet.WalletTransferBuilder
import uikit.extensions.collectFlow
import uikit.widget.ProcessTaskView

class StakingViewModel(
    app: Application,
    address: String,
    private val accountRepository: AccountRepository,
    private val stakingRepository: StakingRepository,
    private val tokenRepository: TokenRepository,
    private val ratesRepository: RatesRepository,
    private val passcodeManager: PasscodeManager,
    private val settingsRepository: SettingsRepository,
    private val api: API,
) : BaseWalletVM(app) {

    data class AvailableUiState(
        val balanceFormat: CharSequence,
        val remainingFormat: CharSequence,
        val minStakeFormat: CharSequence,
        val insufficientBalance: Boolean,
        val requestMinStake: Boolean,
        val hiddenBalance: Boolean
    )

    val poolsFlow = accountRepository.selectedWalletFlow.map { wallet ->
        stakingRepository.get(wallet.accountId, wallet.testnet).pools
    }.flowOn(Dispatchers.IO).filter { it.isNotEmpty() }

    private val _amountFlow = MutableStateFlow(0.0)
    private val amountFlow = _amountFlow.map { Coins.of(it) }

    private val _selectedPoolFlow = MutableStateFlow<PoolEntity?>(null)
    val selectedPoolFlow = _selectedPoolFlow.asStateFlow().filterNotNull()

    private val tokenFlow = accountRepository.selectedWalletFlow.map { wallet ->
        tokenRepository.get(settingsRepository.currency, wallet.accountId, wallet.testnet) ?: emptyList()
    }.mapNotNull { it.firstOrNull() }
        .flowOn(Dispatchers.IO)
        .shareIn(viewModelScope, SharingStarted.Lazily, 1)

    private val ratesFlow = tokenFlow.map { token ->
        ratesRepository.getRates(settingsRepository.currency, token.address)
    }.flowOn(Dispatchers.IO)

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

    val fiatFlow = combine(amountFlow, ratesFlow, tokenFlow) { amount, rates, token ->
        rates.convert(token.address, amount)
    }

    val fiatFormatFlow = fiatFlow.map {
        CurrencyFormatter.format(settingsRepository.currency.code, it, replaceSymbol = false)
    }

    val amountFormatFlow = combine(amountFlow, tokenFlow) { amount, token ->
        CurrencyFormatter.format(token.symbol, amount)
    }

    val apyFormatFlow = combine(
        amountFlow,
        selectedPoolFlow,
        poolsFlow
    ) { amount, pool, pools ->
        val info = pools.find { it.implementation == pool.implementation } ?: return@combine ""
        val apyFormat = CurrencyFormatter.formatPercent(info.apy)
        if (amount.isPositive) {
            val earning = amount.multiply(pool.apy).divide(100)
            "%s ≈ %s · %s".format(
                getString(Localization.staking_apy),
                apyFormat,
                CurrencyFormatter.format(TokenEntity.TON.symbol, earning)
            )
        } else {
            "%s ≈ %s".format(
                getString(Localization.staking_apy),
                apyFormat
            )
        }
    }

    val walletFlow = accountRepository.selectedWalletFlow

    init {
        collectFlow(poolsFlow) { pools ->
            if (_selectedPoolFlow.value != null) {
                return@collectFlow
            }

            val poolAddress = address.ifBlank {
                pools.first().pools.first().address
            }
            pools.map { it.pools }.flatten().find {
                it.address == poolAddress
            }?.let {
                selectPool(it)
            }
        }
        updateAmount(0.0)
    }

    fun requestMax() = tokenFlow.take(1).map {
        it.balance.value
    }

    fun updateAmount(amount: Double) {
        _amountFlow.value = amount
    }

    fun selectPool(pool: PoolEntity) {
        _selectedPoolFlow.value = pool
    }

    private suspend fun getSendParams(
        wallet: WalletEntity,
    ): SendMetadataEntity = withContext(Dispatchers.IO) {
        val seqnoDeferred = async { accountRepository.getSeqno(wallet) }
        val validUntilDeferred = async { accountRepository.getValidUntil(wallet.testnet) }

        SendMetadataEntity(
            seqno = seqnoDeferred.await(),
            validUntil = validUntilDeferred.await(),
        )
    }

    private suspend fun buildPayload(pool: PoolEntity): Cell = withContext(Dispatchers.IO) {
        val queryId = TransferEntity.newWalletQueryId()
        when (pool.implementation) {
            StakingPool.Implementation.Whales -> StakingUtils.createWhalesAddStakeCommand(queryId)
            StakingPool.Implementation.LiquidTF -> StakingUtils.createLiquidTfAddStakeCommand(queryId)
            StakingPool.Implementation.TF -> StakingUtils.createTfAddStakeCommand()
            else -> throw IllegalStateException("Unsupported pool implementation: ${pool.implementation}")
        }
    }

    private suspend fun buildTransfer(
        wallet: WalletEntity,
        amount: Coins,
        pool: PoolEntity,
        token: TokenEntity,
        sendParams: SendMetadataEntity,
    ): WalletTransfer {
        val withdrawalFee = Coins.of(StakingUtils.getWithdrawalFee(pool.implementation))
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
        }
        return builder.build()
    }

    private fun unsignedBodyFlow() = combine(
        walletFlow.take(1),
        amountFlow.take(1),
        selectedPoolFlow.take(1),
        tokenFlow.take(1),
    ) { wallet, amount, pool, token ->
        val params = getSendParams(wallet)
        val gift = buildTransfer(wallet, amount, pool, token.balance.token, params)
        val body = wallet.contract.createTransferUnsignedBody(
            validUntil = params.validUntil,
            seqno = params.seqno,
            gifts = arrayOf(gift),
        )
        Pair(params.seqno, body)
    }.flowOn(Dispatchers.IO)

    private fun ledgerTransactionFlow() = combine(
        walletFlow.take(1),
        amountFlow.take(1),
        selectedPoolFlow.take(1),
        tokenFlow.take(1),
    ) { wallet, amount, pool, token ->
        val params = getSendParams(wallet)
        val gift = buildTransfer(wallet, amount, pool, token.balance.token, params)
        val transaction = Transaction.fromWalletTransfer(gift, params.seqno, params.validUntil)
        Pair(params.seqno, transaction)
    }.flowOn(Dispatchers.IO)

    private fun requestFee() = combine(
        walletFlow.take(1),
        unsignedBodyFlow()
    ) { wallet, (seqno, unsignedBody) ->
        val contract = wallet.contract
        val message = contract.createTransferMessageCell(
            address = contract.address,
            privateKey = EmptyPrivateKeyEd25519,
            seqno = seqno,
            unsignedBody = unsignedBody,
        )
        api.emulate(message, wallet.testnet)?.totalFees ?: 0L
    }.flowOn(Dispatchers.IO)

    fun requestFeeFormat() = combine(
        ratesFlow.take(1),
        requestFee(),
        tokenFlow.take(1)
    ) { rates, fee, token ->
        val currency = settingsRepository.currency
        val coins = Coins.of(fee)
        val converted = rates.convert(token.address, coins)
        Pair(
            CurrencyFormatter.format(TokenEntity.TON.symbol, coins, TokenEntity.TON.decimals),
            CurrencyFormatter.format(currency.code, converted, currency.decimals)
        )
    }

    fun stake(context: Context) = walletFlow.take(1).map { wallet ->
        if (wallet.isLedger) {
            createLedgerStakeFlow(context, wallet)
        } else {
            createStakeFlow(wallet)
        }
    }.flattenFirst().flowOn(Dispatchers.IO)

    private fun createLedgerStakeFlow(
        context: Context,
        wallet: WalletEntity
    ) = ledgerTransactionFlow().map { (seqno, transaction) ->
        val signedBody = context.signLedgerTransaction(transaction, wallet.id)
            ?: throw SendException.Cancelled()

        val contract = wallet.contract
        val message = contract.createTransferMessageCell(contract.address, seqno, signedBody)

        if (!api.sendToBlockchain(message, wallet.testnet)) {
            throw SendException.FailedToSendTransaction()
        }
    }

    private fun createStakeFlow(wallet: WalletEntity) = unsignedBodyFlow().map { (seqno, unsignedBody) ->
        if (!passcodeManager.confirmation(context, context.getString(Localization.app_name))) {
            throw SendException.Cancelled()
        }

        val contract = wallet.contract
        val privateKey = accountRepository.getPrivateKey(wallet.id)
        val message = contract.createTransferMessageCell(
            address = contract.address,
            privateKey = privateKey,
            seqno = seqno,
            unsignedBody = unsignedBody
        )

        if (!api.sendToBlockchain(message, wallet.testnet)) {
            throw SendException.FailedToSendTransaction()
        }
    }
}