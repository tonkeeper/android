package com.tonapps.tonkeeper.ui.screen.send.main

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.tonkeeper.core.Fee
import com.tonapps.blockchain.ton.contract.WalletFeature
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.blockchain.ton.extensions.isTestnetAddress
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.filterList
import com.tonapps.extensions.state
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.core.Amount
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.core.SendBlockchainException
import com.tonapps.tonkeeper.core.entities.SendMetadataEntity
import com.tonapps.tonkeeper.core.entities.TransferEntity
import com.tonapps.tonkeeper.extensions.isPrintableAscii
import com.tonapps.tonkeeper.extensions.isSafeModeEnabled
import com.tonapps.tonkeeper.extensions.with
import com.tonapps.tonkeeper.manager.tx.TransactionManager
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.send.main.SendScreen.Companion.Type
import com.tonapps.tonkeeper.ui.screen.send.main.helper.InsufficientBalanceType
import com.tonapps.tonkeeper.ui.screen.send.main.helper.isEmptyBalance
import com.tonapps.tonkeeper.ui.screen.send.main.helper.isTON
import com.tonapps.tonkeeper.ui.screen.send.main.state.SendAmountState
import com.tonapps.tonkeeper.ui.screen.send.main.state.SendDestination
import com.tonapps.tonkeeper.ui.screen.send.main.state.SendTransaction
import com.tonapps.tonkeeper.ui.screen.send.main.state.SendTransferType
import com.tonapps.tonkeeper.usecase.sign.SignUseCase
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.SendBlockchainState
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.battery.entity.BatteryBalanceEntity
import com.tonapps.wallet.data.collectibles.CollectiblesRepository
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.BatteryTransaction
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.block.AddrStd
import org.ton.cell.Cell
import uikit.extensions.collectFlow
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

@OptIn(FlowPreview::class)
class SendViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val nftAddress: String,
    private val accountRepository: AccountRepository,
    private val api: API,
    private val settingsRepository: SettingsRepository,
    private val tokenRepository: TokenRepository,
    private val ratesRepository: RatesRepository,
    private val collectiblesRepository: CollectiblesRepository,
    private val batteryRepository: BatteryRepository,
    private val transactionManager: TransactionManager,
    private val signUseCase: SignUseCase
) : BaseWalletVM(app) {

    private val isNft: Boolean
        get() = nftAddress.isNotBlank()

    val installId: String
        get() = settingsRepository.installId

    data class UserInput(
        val address: String = "",
        val amount: Coins = Coins.ZERO,
        val token: TokenEntity = TokenEntity.TON,
        val comment: String? = null,
        val nft: NftEntity? = null,
        val encryptedComment: Boolean = false,
        val max: Boolean = false,
        val amountCurrency: Boolean = false,
        val bin: Cell? = null,
        val type: Type = Type.Default
    )

    private val currency = settingsRepository.currency
    private val queryId: BigInteger by lazy { TransferEntity.newWalletQueryId() }

    private val _userInputFlow = MutableStateFlow(UserInput())
    private val userInputFlow = _userInputFlow.asStateFlow()

    private var lastTransferEntity: TransferEntity? = null
    private val lastRawExtra: AtomicLong = AtomicLong(0)
    private var sendTransferType: SendTransferType = SendTransferType.Default
    private var tokenCustomPayload: TokenEntity.TransferPayload? = null

    private val userInputAddressFlow = userInputFlow
        .map { it.address }
        .distinctUntilChanged()
        .debounce { if (it.isEmpty()) 0 else 600 }

    private val destinationFlow = userInputAddressFlow.map { address ->
        if (address.isEmpty()) {
            SendDestination.Empty
        } else if (wallet.testnet != address.isTestnetAddress()) {
            SendDestination.NotFound
        } else {
            getDestinationAccount(address, wallet.testnet)
        }
    }.flowOn(Dispatchers.IO).state(viewModelScope)

    private val _tokensFlow = MutableStateFlow<List<AccountTokenEntity>?>(null)
    private val tokensFlow = _tokensFlow.asStateFlow().filterNotNull()

    private val _feeFlow = MutableStateFlow<Fee?>(null)
    private val feeFlow = _feeFlow.asStateFlow().filterNotNull()

    private val selectedTokenFlow = combine(
        tokensFlow,
        userInputFlow.map { it.token }.distinctUntilChanged()
    ) { tokens, selectedToken ->
        tokens.find { it.address == selectedToken.address } ?: AccountTokenEntity.createEmpty(selectedToken, wallet.address)
    }.distinctUntilChanged()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, AccountTokenEntity.EMPTY)

    private val ratesTokenFlow = selectedTokenFlow.map { token ->
        ratesRepository.getRates(currency, token.address)
    }.state(viewModelScope)

    val uiInputAddressErrorFlow = destinationFlow.map { it is SendDestination.NotFound }

    private val _uiInputAmountFlow = MutableEffectFlow<Coins>()
    val uiInputAmountFlow = _uiInputAmountFlow.asSharedFlow()

    val uiInputTokenFlow = userInputFlow.map { it.token }.filter { !isNft }.distinctUntilChanged()

    val uiInputNftFlow = userInputFlow.map { it.nft }.distinctUntilChanged().filterNotNull()

    val uiRequiredMemoFlow =
        destinationFlow.map { it as? SendDestination.Account }.map { it?.memoRequired == true }

    val uiExistingTargetFlow =
        destinationFlow.map { it as? SendDestination.Account }.map { it?.existing == true }

    val uiEncryptedCommentAvailableFlow = combine(
        uiRequiredMemoFlow,
        uiExistingTargetFlow,
    ) { requiredMemo, existingTarget ->
        existingTarget && !requiredMemo && (wallet.type == Wallet.Type.Default || wallet.type == Wallet.Type.Testnet || wallet.type == Wallet.Type.Lockup)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val uiInputEncryptedComment = combine(
        userInputFlow.map { it.encryptedComment }.distinctUntilChanged(),
        uiEncryptedCommentAvailableFlow,
    ) { encryptedComment, available ->
        encryptedComment && available
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val uiInputComment = userInputFlow.map { it.comment }.distinctUntilChanged()

    private val uiInputAmountCurrency = userInputFlow.map { it.amountCurrency }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val inputAmountFlow = userInputFlow.map { it.amount }.distinctUntilChanged()

    val uiInputCommentErrorFlow = uiInputComment.map { comment ->
        if (wallet.isLedger && !comment.isNullOrEmpty() && !comment.isPrintableAscii()) {
            Localization.ledger_comment_error
        } else {
            null
        }
    }

    private val _uiEventFlow = MutableEffectFlow<SendEvent>()
    val uiEventFlow = _uiEventFlow.asSharedFlow()

    val uiBalanceFlow = combine(
        selectedTokenFlow,
        inputAmountFlow,
        ratesTokenFlow,
        uiInputAmountCurrency,
    ) { token, amount, rates, amountCurrency ->
        val (balance, currencyCode) = if (amountCurrency) {
            Pair(token.fiat, currency.code)
        } else {
            Pair(token.balance.value, token.symbol)
        }

        val remaining = balance - amount

        val convertedCode = if (amountCurrency) token.symbol else currency.code
        val converted = if (amountCurrency) {
            rates.convertFromFiat(token.address, amount)
        } else {
            rates.convert(token.address, amount)
        }

        val remainingToken = if (!amountCurrency) {
            token.balance.value - amount
        } else {
            rates.convertFromFiat(token.address, token.fiat - amount)
        }

        val remainingFormat = CurrencyFormatter.format(
            currency = token.symbol,
            value = remainingToken,
            customScale = 2,
            roundingMode = RoundingMode.DOWN,
            replaceSymbol = false
        )

        SendAmountState(
            remainingFormat = getString(Localization.remaining_balance, remainingFormat),
            converted = converted.stripTrailingZeros(),
            convertedFormat = CurrencyFormatter.format(
                currency = convertedCode,
                value = converted,
                customScale = 2,
                roundingMode = RoundingMode.DOWN,
                replaceSymbol = false
            ),
            insufficientBalance = if (remaining.isZero) false else remaining.isNegative,
            currencyCode = if (amountCurrency) currencyCode else "",
            amountCurrency = amountCurrency,
            hiddenBalance = settingsRepository.hiddenBalances
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SendAmountState())

    val uiButtonEnabledFlow = combine(
        destinationFlow,
        uiBalanceFlow,
        inputAmountFlow,
        uiInputComment,
        uiInputCommentErrorFlow,
    ) { recipient, balance, amount, comment, commentError ->
        if (recipient !is SendDestination.Account) {
            false
        } else if (recipient.memoRequired && comment.isNullOrEmpty()) {
            false
        } else if (commentError != null) {
            false
        } else if (isNft || (!balance.insufficientBalance && amount.isPositive)) {
            true
        } else if (balance.insufficientBalance) {
            false
        } else {
            false
        }
    }

    private val amountTokenFlow = combine(
        selectedTokenFlow,
        inputAmountFlow,
        ratesTokenFlow,
        uiInputAmountCurrency,
    ) { token, amount, rates, amountCurrency ->
        if (!amountCurrency) {
            amount
        } else {
            rates.convertFromFiat(token.address, amount)
        }
    }

    private val transferAmountFlow = combine(
        amountTokenFlow,
        selectedTokenFlow,
        ratesTokenFlow,
    ) { amount, token, rates ->
        SendTransaction.Amount(
            value = amount,
            converted = rates.convert(token.address, amount),
            format = CurrencyFormatter.format(
                token.symbol,
                amount,
                token.decimals,
                RoundingMode.UP,
                false
            ),
            convertedFormat = CurrencyFormatter.format(
                currency.code,
                rates.convert(token.address, amount),
                token.decimals,
                RoundingMode.UP,
            ),
        )
    }

    // Using only for UI
    private val uiTransferAmountFlow = combine(
        amountTokenFlow,
        selectedTokenFlow,
        ratesTokenFlow,
        feeFlow,
    ) { amount, token, rates, fee ->
        var value = when {
            sendTransferType is SendTransferType.Gasless && amount >= token.balance.value -> amount - fee.fee
            token.isTon && amount >= token.balance.value -> amount - fee.fee
            else -> amount
        }
        if (value.isNegative) {
            value = Coins.ZERO
        }

        SendTransaction.Amount(
            value = value,
            converted = rates.convert(token.address, value),
            format = CurrencyFormatter.format(
                token.symbol,
                value,
                token.decimals,
                RoundingMode.UP,
                false
            ),
            convertedFormat = CurrencyFormatter.format(
                currency.code,
                rates.convert(token.address, value),
                token.decimals,
                RoundingMode.UP,
            ),
        )
    }

    private val transactionFlow = combine(
        destinationFlow.mapNotNull { it as? SendDestination.Account },
        selectedTokenFlow,
        transferAmountFlow,
        userInputFlow,
    ) { destination, token, amount, userInput ->
        SendTransaction(
            fromWallet = wallet,
            destination = destination,
            token = token.balance,
            comment = userInput.comment,
            encryptedComment = userInput.encryptedComment,
            amount = amount,
            max = userInput.max
        )
    }

    val uiTransactionFlow = combine(
        destinationFlow.mapNotNull { it as? SendDestination.Account },
        selectedTokenFlow,
        uiTransferAmountFlow,
        userInputFlow,
    ) { destination, token, amount, userInput ->
        SendTransaction(
            fromWallet = wallet,
            destination = destination,
            token = token.balance,
            comment = userInput.comment,
            encryptedComment = userInput.encryptedComment,
            amount = amount,
            max = userInput.max
        )
    }

    private val transferFlow = combine(
        transactionFlow.distinctUntilChanged().debounce(300),
        userInputFlow.map { Pair(it.comment, it.encryptedComment) }.distinctUntilChanged()
            .debounce(300),
        selectedTokenFlow.debounce(300),
    ) { transaction, (comment, encryptedComment), token ->
        val customPayload = getTokenCustomPayload(token.balance.token)
        val sendMetadata = getSendParams(wallet)
        val builder = TransferEntity.Builder(wallet)
        if (!customPayload.isEmpty) {
            builder.setTokenPayload(customPayload)
        }
        builder.setToken(transaction.token)
        builder.setDestination(transaction.destination.address, transaction.destination.publicKey)
        builder.setSeqno(sendMetadata.seqno)
        builder.setQueryId(queryId)
        builder.setComment(comment, encryptedComment)
        builder.setValidUntil(sendMetadata.validUntil)
        if (isNft) {
            builder.setNftAddress(nftAddress)
            builder.setBounceable(true)
            builder.setAmount(Coins.ZERO)
            builder.setMax(false)
        } else {
            builder.setMax(transaction.isRealMax(token.balance.value))
            builder.setAmount(transaction.amount.value)
            builder.setBounceable(transaction.destination.isBounce)
        }
        builder.build()
    }.flowOn(Dispatchers.IO).shareIn(viewModelScope, SharingStarted.Eagerly, 1)

    val userInputMaxFlow = combine(
        userInputFlow,
        selectedTokenFlow,
        uiInputAmountCurrency,
    ) { input, selected, amountCurrency ->
        val tokenAddress = selected.address
        val amount = if (amountCurrency) {
            val rates = ratesRepository.getRates(currency, tokenAddress)
            rates.convertFromFiat(tokenAddress, input.amount)
        } else {
            input.amount
        }
        amount >= selected.balance.value
    }.flowOn(Dispatchers.IO)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _tokensFlow.value = tokenRepository.get(currency, wallet.accountId, wallet.testnet)
        }

        if (isNft) {
            loadNft()
        }
    }

    fun initializeTokenAndAmount(
        tokenAddress: String?,
        amountNano: Long?,
        type: Type
    ) {
        collectFlow(uiInputTokenFlow.filter {
            it.address.equals(tokenAddress, ignoreCase = true)
        }.take(1)) { token ->
            applyAmount(token, amountNano)
        }

        tokenAddress?.let {
            userInputTokenByAddress(it)
        }

        _userInputFlow.update {
            it.copy(type = type)
        }
    }

    private fun applyAmount(token: TokenEntity, amountNano: Long?) {
        amountNano?.let {
            _uiInputAmountFlow.tryEmit(Coins.of(it, token.decimals))
        }
    }

    private suspend fun getDestinationAccount(
        address: String,
        testnet: Boolean
    ) = withContext(Dispatchers.IO) {
        val accountDeferred = async { api.resolveAccount(address, testnet) }
        val publicKeyDeferred = async { api.safeGetPublicKey(address, testnet) }

        val account = accountDeferred.await() ?: return@withContext SendDestination.NotFound
        val publicKey = publicKeyDeferred.await()

        SendDestination.Account(address, publicKey, account)
    }

    private fun getFee(): Fee {
        return Fee(lastRawExtra.get())
    }

    private fun showIfInsufficientBalance(onContinue: () -> Unit) {
        viewModelScope.launch(Dispatchers.Main) {
            val type = userInputFlow.value.type
            val isDirectTransferType = type == SendScreen.Companion.Type.Direct
            val fee = getFee()
            val tonBalance = getTONBalance()
            val token = selectedTokenFlow.value
            val tokenBalance = token.balance.value
            val tokenAmount = getTokenAmount()
            val isUseGasless = sendTransferType is SendTransferType.Gasless
            val withRelayer = isUseGasless || sendTransferType is SendTransferType.Battery
            if (token.isTon) {
                if (!isDirectTransferType && (tokenAmount == tonBalance || userInputFlow.value.max)) {
                    onContinue()
                } else {
                    val totalAmount = fee.fee + tokenAmount
                    val insufficientBalanceType = when {
                        tonBalance.isZero -> InsufficientBalanceType.EmptyBalance
                        tokenAmount > tonBalance -> InsufficientBalanceType.InsufficientTONBalance
                        totalAmount > tonBalance -> InsufficientBalanceType.InsufficientBalanceForFee
                        else -> null
                    }
                    if (insufficientBalanceType == null || withRelayer) {
                        onContinue()
                    } else {
                        showInsufficientBalance(
                            tonBalance = Amount(tonBalance),
                            balance = Amount(tonBalance),
                            amount = Amount(tokenAmount),
                            fee = fee,
                            type = insufficientBalanceType
                        )
                    }
                }
            } else if (isNft) {
                if (!withRelayer && fee.fee > tonBalance) {
                    showInsufficientBalance(
                        tonBalance = Amount(tonBalance),
                        balance = Amount(tonBalance),
                        amount = Amount(),
                        fee = fee,
                        type = InsufficientBalanceType.InsufficientBalanceForFee
                    )
                } else {
                    onContinue()
                }
            } else {
                val totalFee = fee.fee + TransferEntity.BASE_FORWARD_AMOUNT
                val gaslessFee = (sendTransferType as? SendTransferType.Gasless)?.gaslessFee ?: Coins.ZERO
                val insufficientBalanceType = when {
                    tokenBalance.isZero -> InsufficientBalanceType.EmptyJettonBalance
                    isUseGasless && gaslessFee > tokenAmount -> InsufficientBalanceType.InsufficientGaslessBalance
                    tokenAmount > tokenBalance -> InsufficientBalanceType.InsufficientJettonBalance
                    !isUseGasless && totalFee > tonBalance -> InsufficientBalanceType.InsufficientBalanceForFee
                    else -> null
                }
                if (withRelayer && insufficientBalanceType == InsufficientBalanceType.InsufficientBalanceForFee) {
                    onContinue()
                } else if (insufficientBalanceType != null) {
                    showInsufficientBalance(
                        tonBalance = Amount(tonBalance),
                        balance = Amount(tokenBalance, token.token),
                        amount = Amount(tokenAmount, token.token),
                        fee = fee,
                        gaslessFee = gaslessFee,
                        type = insufficientBalanceType,
                    )
                } else {
                    onContinue()
                }
            }
        }
    }

    private suspend fun getBatteryBalance(): BatteryBalanceEntity = withContext(Dispatchers.IO) {
        accountRepository.requestTonProofToken(wallet)?.let {
            batteryRepository.getBalance(it, wallet.publicKey, wallet.testnet, true)
        } ?: BatteryBalanceEntity.Empty
    }

    private suspend fun showInsufficientBalance(
        tonBalance: Amount,
        balance: Amount,
        amount: Amount,
        fee: Fee,
        gaslessFee: Coins = Coins.ZERO,
        type: InsufficientBalanceType,
    ) {
        val txType = when  {
            nftAddress.isNotBlank() -> BatteryTransaction.NFT
            !type.isTON() -> BatteryTransaction.JETTON
            else -> BatteryTransaction.UNKNOWN
        }
        val batteryBalance = getBatteryBalance()
        val batteryEnabled = !api.config.batteryDisabled && settingsRepository.batteryIsEnabledTx(wallet.accountId, txType)
        val required = when(type) {
            InsufficientBalanceType.InsufficientGaslessBalance -> Amount(gaslessFee, amount.token)
            InsufficientBalanceType.InsufficientJettonBalance, InsufficientBalanceType.EmptyJettonBalance -> amount
            InsufficientBalanceType.InsufficientTONBalance, InsufficientBalanceType.EmptyBalance -> amount
            InsufficientBalanceType.InsufficientBalanceForFee -> Amount(fee.fee)
            else -> Amount(fee.fee + amount.value, amount.token)
        }

        val showBalance = when(type) {
            InsufficientBalanceType.InsufficientBalanceForFee -> tonBalance
            else -> balance
        }

        var withRechargeBattery = batteryEnabled && batteryBalance.balance.value == BigDecimal.ZERO
        if (withRechargeBattery && type.isEmptyBalance()) {
            withRechargeBattery = false
        }
        if (gaslessFee.isZero && type == InsufficientBalanceType.InsufficientJettonBalance) {
            withRechargeBattery = false
        }

        _uiEventFlow.tryEmit(
            SendEvent.InsufficientBalance(
                balance = showBalance,
                required = required,
                withRechargeBattery = withRechargeBattery,
                singleWallet = 1 >= getWalletCount(),
                type = type
            )
        )
    }

    private suspend fun getWalletCount(): Int = withContext(Dispatchers.IO) {
        accountRepository.getWallets().size
    }

    private suspend fun getTokenAmount(): Coins = withContext(Dispatchers.IO) {
        val amount = userInputFlow.value.amount
        val token = selectedTokenFlow.value
        if (!userInputFlow.value.amountCurrency) {
            amount
        } else {
            val rates = ratesRepository.getRates(currency, token.address)
            rates.convertFromFiat(token.address, amount)
        }
    }

    private suspend fun getTONBalance(): Coins = withContext(Dispatchers.IO) {
        if (selectedTokenFlow.value.isTon) {
            selectedTokenFlow.value.balance.value
        } else {
            tokenRepository.getTON(currency, wallet.accountId, wallet.testnet)?.balance?.value
                ?: Coins.ZERO
        }
    }

    fun next() {
        combine(
            transferFlow.take(1),
            tokensFlow.take(1),
            userInputFlow.map { it.type }.take(1)
        ) { transfer, tokens, transferType ->
            val (fee, isSupportGasless) = calculateFee(transfer)
            _feeFlow.tryEmit(fee)
            eventFee(transfer, tokens, fee, isSupportGasless, transferType)
        }.filterNotNull().onEach {
            showPreview(it)
        }.flowOn(Dispatchers.IO).launchIn(viewModelScope)
    }

    private fun showPreview(fee: SendEvent.Fee) {
        showIfInsufficientBalance {
            _uiEventFlow.tryEmit(SendEvent.Confirm)
        }
        _uiEventFlow.tryEmit(fee)
    }

    private fun loadNft() {
        viewModelScope.launch(Dispatchers.IO) {
            val nft = collectiblesRepository.getNft(
                accountId = wallet.accountId,
                testnet = wallet.testnet,
                address = nftAddress
            ) ?: return@launch
            val pref = settingsRepository.getTokenPrefs(wallet.id, nftAddress)
            userInputNft(nft.with(pref))
        }
    }

    private fun shouldAttemptWithRelayer(transfer: TransferEntity): Boolean {
        if ((transfer.isTon && !transfer.isNft) || transfer.wallet.isExternal) {
            return false
        }

        val transactionType = if (transfer.isNft) {
            BatteryTransaction.NFT
        } else {
            BatteryTransaction.JETTON
        }

        return settingsRepository.batteryIsEnabledTx(transfer.wallet.accountId, transactionType)
    }

    private suspend fun calculateFee(
        transfer: TransferEntity,
        retryWithoutRelayer: Boolean = false,
        ignoreGasless: Boolean = false,
    ): Pair<Fee, Boolean> = withContext(Dispatchers.IO) {
        val transferType = userInputFlow.value.type
        val wallet = transfer.wallet
        val withRelayer = shouldAttemptWithRelayer(transfer)
        val tonProofToken = accountRepository.requestTonProofToken(wallet)
        val batteryConfig = batteryRepository.getConfig(wallet.testnet)
        val tokenAddress = transfer.token.token.address
        val excessesAddress = batteryConfig.excessesAddress
        val isGaslessToken = !transfer.token.isTon && batteryConfig.rechargeMethods.any {
            it.supportGasless && it.jettonMaster == tokenAddress
        }

        val isSupportsGasless = wallet.isSupportedFeature(WalletFeature.GASLESS) && tonProofToken != null && excessesAddress != null && isGaslessToken
        val isPreferGasless = batteryRepository.getPreferGasless(wallet.testnet)

        if (ignoreGasless && retryWithoutRelayer) {
            return@withContext calculateFeeDefault(transfer, isSupportsGasless)
        } else if (withRelayer && !retryWithoutRelayer && tonProofToken != null && excessesAddress != null) {
            return@withContext try {
                calculateFeeBattery(transfer, excessesAddress, isSupportsGasless, tonProofToken)
            } catch (e: Throwable) {
                calculateFee(transfer, retryWithoutRelayer = true)
            }
        } else if (!ignoreGasless && isPreferGasless && isSupportsGasless && tonProofToken != null && excessesAddress != null) {
            return@withContext try {
                calculateFeeGasless(transfer, excessesAddress, tonProofToken, tokenAddress, transferType = transferType)
            } catch (e: Throwable) {
                calculateFee(transfer, ignoreGasless = true)
            }
        } else {
            val result = calculateFeeDefault(transfer, isSupportsGasless)
            val (fee) = result
            if (!transfer.isTon && isSupportsGasless && tonProofToken != null && excessesAddress != null) {
                val totalAmount = fee.fee + TransferEntity.BASE_FORWARD_AMOUNT
                val tonBalance = getTONBalance()
                if (totalAmount > tonBalance) {
                    try {
                        return@withContext calculateFeeGasless(
                            transfer,
                            excessesAddress,
                            tonProofToken,
                            tokenAddress,
                            forceGasless = true,
                            transferType
                        )
                    } catch (ignored: Throwable) {  }
                }
            }
            return@withContext result
        }
    }

    private suspend fun calculateFeeBattery(
        transfer: TransferEntity,
        excessesAddress: AddrStd,
        isSupportsGasless: Boolean,
        tonProofToken: String,
    ): Pair<Fee, Boolean> {
        if (api.config.isBatteryDisabled) {
            return calculateFeeDefault(transfer, isSupportsGasless)
        }

        val message = transfer.signForEstimation(
            internalMessage = true,
            excessesAddress = excessesAddress,
            jettonTransferAmount = TransferEntity.BASE_FORWARD_AMOUNT,
        )

        val (consequences, withBattery) = batteryRepository.emulate(
            tonProofToken = tonProofToken,
            publicKey = wallet.publicKey,
            testnet = wallet.testnet,
            boc = message,
            safeModeEnabled = settingsRepository.isSafeModeEnabled(api)
        ) ?: return calculateFeeDefault(transfer, isSupportsGasless)

        sendTransferType = if (withBattery) {
            SendTransferType.Battery(excessesAddress)
        } else {
            SendTransferType.Default
        }

        val extra = consequences.event.extra

        lastRawExtra.set(extra)

        return Pair(getFee(), isSupportsGasless)
    }

    private suspend fun calculateFeeGasless(
        transfer: TransferEntity,
        excessesAddress: AddrStd,
        tonProofToken: String,
        tokenAddress: String,
        forceGasless: Boolean = false,
        transferType: Type,
    ): Pair<Fee, Boolean> {
        val isDirectTransfer = transferType == SendScreen.Companion.Type.Direct
        val message = transfer.signForEstimation(
            internalMessage = true,
            jettonAmount = if (transfer.max) {
                Coins.of(1, transfer.token.decimals)
            } else {
                null
            },
            additionalGifts = listOf(
                transfer.gaslessInternalGift(
                    jettonAmount = Coins.of(1, transfer.token.decimals),
                    batteryAddress = excessesAddress
                )
            ),
            excessesAddress = excessesAddress,
            jettonTransferAmount = TransferEntity.BASE_FORWARD_AMOUNT
        )

        val commission = api.estimateGaslessCost(
            tonProofToken = tonProofToken,
            jettonMaster = tokenAddress,
            cell = message,
            testnet = wallet.testnet,
        ) ?: throw IllegalStateException("Can't estimate gasless cost")

        val gaslessFee = Coins.ofNano(commission, transfer.token.decimals)

        val tokenBalance = selectedTokenFlow.value.balance.value

        if (gaslessFee > transfer.amount || gaslessFee + transfer.amount > tokenBalance) {
            return calculateFeeDefault(transfer, false)
        }

        sendTransferType = SendTransferType.Gasless(
            excessesAddress = excessesAddress,
            gaslessFee = gaslessFee
        )

        val fee = Fee(
            value = gaslessFee,
            isRefund = false,
        )

        return Pair(fee, !forceGasless)
    }

    private suspend fun calculateFeeDefault(
        transfer: TransferEntity,
        isSupportsGasless: Boolean,
    ): Pair<Fee, Boolean> {
        val message = transfer.signForEstimation(
            internalMessage = false,
            jettonTransferAmount = TransferEntity.BASE_FORWARD_AMOUNT
        )
        // Emulate with higher balance to calculate fair amount to send
        val emulated = api.emulate(
            cell = message,
            testnet = transfer.wallet.testnet,
            address = transfer.wallet.accountId,
            balance = (Coins.ONE + Coins.ONE).toLong(),
            safeModeEnabled = settingsRepository.isSafeModeEnabled(api)
        )
        val extra = emulated?.event?.extra ?: 0

        lastRawExtra.set(extra)

        sendTransferType = SendTransferType.Default

        return Pair(getFee(), isSupportsGasless)
    }

    private suspend fun eventFee(
        transfer: TransferEntity,
        tokens: List<AccountTokenEntity>,
        fee: Fee,
        isSupportGasless: Boolean,
        transferType: Type
    ): SendEvent.Fee? {
        return try {
            val withRelayer = sendTransferType is SendTransferType.Gasless || sendTransferType is SendTransferType.Battery
            val isDirectTransfer = transferType == SendScreen.Companion.Type.Direct
            val feeToken = if (sendTransferType is SendTransferType.Gasless) {
                transfer.token.token
            } else {
                TokenEntity.TON
            }

            val gaslessFee = (sendTransferType as? SendTransferType.Gasless)?.gaslessFee ?: Coins.ZERO
            val tonBalance = getTONBalance()

            val rates = ratesRepository.getRates(currency, feeToken.address)
            val converted = rates.convert(feeToken.address, fee.value)
            var insufficientFunds = transfer.token.value.isZero || transfer.amount > transfer.token.value
            if (!insufficientFunds && feeToken.isTon && !withRelayer) {
                insufficientFunds = fee.fee > tonBalance
            }
            if (!insufficientFunds && !feeToken.isTon) {
                insufficientFunds = gaslessFee > transfer.token.value
            }
            if (!insufficientFunds && transfer.token.isTon && transfer.amount != transfer.token.value) {
                insufficientFunds = (fee.fee + transfer.amount) > transfer.token.value
            }
            /*if (!insufficientFunds && isDirectTransfer && !feeToken.isTon) {
                insufficientFunds = (gaslessFee + transfer.amount) > transfer.token.value
            }*/


            val ton = tokens.find {
                it.isTon
            } ?: throw IllegalStateException("Can't find TON token")

            val hasEnoughTonBalance = ton.balance.value >= TransferEntity.BASE_FORWARD_AMOUNT

            SendEvent.Fee(
                balance = ton.balance.value,
                amount = transfer.amount,
                fee = fee,
                format = CurrencyFormatter.format(feeToken.symbol, fee.value, feeToken.decimals),
                convertedFormat = CurrencyFormatter.format(
                    currency.code, converted, currency.decimals
                ),
                isBattery = sendTransferType is SendTransferType.Battery,
                isGasless = sendTransferType is SendTransferType.Gasless,
                showGaslessToggle = isSupportGasless && hasEnoughTonBalance,
                tokenSymbol = transfer.token.token.symbol,
                insufficientFunds = insufficientFunds
            )
        } catch (e: Throwable) {
            null
        }
    }

    fun toggleGasless() {
        combine(
            transferFlow.take(1),
            tokensFlow.take(1),
            userInputFlow.map { it.type }.take(1)
        ) { transfer, tokens, transferType ->
            val isPreferGasless = !batteryRepository.getPreferGasless(transfer.testnet)
            batteryRepository.setPreferGasless(transfer.testnet, isPreferGasless)
            val (coins, isSupportGasless) = calculateFee(transfer)
            _feeFlow.tryEmit(coins)
            eventFee(transfer, tokens, coins, isSupportGasless, transferType)
        }.filterNotNull().onEach {
            _uiEventFlow.tryEmit(it)
        }.flowOn(Dispatchers.IO).launchIn(viewModelScope)
    }

    fun userInputBin(bin: Cell?) {
        _userInputFlow.update {
            it.copy(bin = bin)
        }
    }

    fun userInputEncryptedComment(encrypted: Boolean) {
        _userInputFlow.update {
            it.copy(encryptedComment = encrypted)
        }
    }

    fun userInputAmount(amount: Coins) {
        _userInputFlow.update {
            it.copy(amount = amount)
        }
    }

    fun userInputToken(token: TokenEntity) {
        _userInputFlow.update {
            it.copy(token = token)
        }
    }

    private fun userInputNft(nft: NftEntity) {
        _userInputFlow.update {
            it.copy(nft = nft)
        }
    }

    private fun userInputTokenByAddress(tokenAddress: String) {
        tokensFlow.take(1).filter {
            it.isNotEmpty()
        }.filterList {
            it.address.equalsAddress(tokenAddress)
        }.map { it.firstOrNull()?.balance?.token }.map { token ->
            token ?: tokenRepository.getToken(tokenAddress, wallet.testnet) ?: TokenEntity.TON
        }.flowOn(Dispatchers.IO).onEach { token ->
            userInputToken(token)
        }.launchIn(viewModelScope)
    }

    fun userInputAddress(address: String) {
        _userInputFlow.update {
            it.copy(address = address)
        }
    }

    fun userInputComment(comment: String?) {
        _userInputFlow.update {
            it.copy(comment = comment?.trim())
        }
    }

    fun swap() {
        val balance = uiBalanceFlow.value.copy()
        val amountCurrency = _userInputFlow.updateAndGet {
            it.copy(amountCurrency = !it.amountCurrency)
        }.amountCurrency

        if (amountCurrency != balance.amountCurrency) {
            _uiInputAmountFlow.tryEmit(balance.converted)
        }
    }

    fun setMax() {
        collectFlow(uiInputAmountCurrency.take(1)) { amountCurrency ->
            val token = selectedTokenFlow.value
            val coins = if (amountCurrency) {
                token.fiat
            } else {
                token.balance.value
            }
            _uiInputAmountFlow.tryEmit(coins)
        }
    }

    private suspend fun getSendParams(
        wallet: WalletEntity,
    ): SendMetadataEntity = withContext(Dispatchers.IO) {
        val seqnoDeferred = async { accountRepository.getSeqno(wallet) }
        val validUntilDeferred = async { accountRepository.getValidUntil(wallet.testnet) }

        val seqno = seqnoDeferred.await()
        val validUntil = validUntilDeferred.await()

        SendMetadataEntity(
            seqno = seqno,
            validUntil = validUntil,
        )
    }

    fun sign() = transferFlow.take(1).map { transfer ->
        _uiEventFlow.tryEmit(SendEvent.Loading)
        lastTransferEntity = transfer
        val excessesAddress = if (sendTransferType is SendTransferType.WithExcessesAddress) {
            (sendTransferType as SendTransferType.WithExcessesAddress).excessesAddress
        } else {
            null
        }

        val additionalGifts = if (sendTransferType is SendTransferType.Gasless) {
            listOf(
                transfer.gaslessInternalGift(
                    jettonAmount = (sendTransferType as SendTransferType.Gasless).gaslessFee,
                    batteryAddress = (sendTransferType as SendTransferType.Gasless).excessesAddress
                )
            )
        } else {
            emptyList()
        }

        val privateKey = if (transfer.commentEncrypted) {
            accountRepository.getPrivateKey(wallet.id)
        } else {
            null
        }

        val internalMessage = excessesAddress != null

        val fee = getFee()

        val token = selectedTokenFlow.value

        val jettonTransferAmount = when {
            sendTransferType is SendTransferType.Gasless || fee.refund.isPositive -> TransferEntity.BASE_FORWARD_AMOUNT
            fee.fee.isZero -> TransferEntity.POINT_ONE_TON
            token.isRequestMinting || token.customPayloadApiUri != null -> TransferEntity.POINT_ONE_TON
            else -> fee.fee + TransferEntity.BASE_FORWARD_AMOUNT
        }

        val boc = signUseCase(
            context = context,
            wallet = wallet,
            unsignedBody = transfer.getUnsignedBody(
                privateKey = privateKey,
                internalMessage = internalMessage,
                additionalGifts = additionalGifts,
                excessesAddress = excessesAddress,
                jettonAmount = if (transfer.max && sendTransferType is SendTransferType.Gasless) {
                    transfer.amount - (sendTransferType as SendTransferType.Gasless).gaslessFee
                } else {
                    null
                },
                jettonTransferAmount = jettonTransferAmount,
            ),
            seqNo = transfer.seqno,
            ledgerTransaction = transfer.getLedgerTransaction(jettonTransferAmount)
        )
        _uiEventFlow.tryEmit(SendEvent.Loading)
        Triple(boc, transfer.wallet, internalMessage)
    }.catch {
        if (it is CancellationException) {
            _uiEventFlow.tryEmit(SendEvent.Canceled)
        } else {
            FirebaseCrashlytics.getInstance().recordException(Throwable("SendViewModel sign failed", it))
            _uiEventFlow.tryEmit(SendEvent.Failed(it))
        }
    }.sendTransfer()

    private suspend fun send(
        message: Cell,
        wallet: WalletEntity,
        withBattery: Boolean,
    ) {
        val state = transactionManager.send(
            wallet = wallet,
            boc = message,
            withBattery = withBattery,
            source = "",
            confirmationTime = 0.0,
        )
        if (state != SendBlockchainState.SUCCESS) {
            throw SendBlockchainException.fromState(state)
        }
    }

    private fun Flow<Triple<Cell, WalletEntity, Boolean>>.sendTransfer() {
        this.map { (boc, wallet, withBattery) ->
            send(boc, wallet, withBattery)
            AnalyticsHelper.trackEvent("send_success", settingsRepository.installId)
        }.catch {
            FirebaseCrashlytics.getInstance().recordException(it)
            _uiEventFlow.tryEmit(SendEvent.Failed(it))
        }.flowOn(Dispatchers.IO).onEach {
            _uiEventFlow.tryEmit(SendEvent.Success)
        }.launchIn(viewModelScope)
    }

    private fun getTokenCustomPayload(
        token: TokenEntity
    ): TokenEntity.TransferPayload {
        if (token.isTon) {
            return TokenEntity.TransferPayload.empty("TON")
        } else if (!token.isRequestMinting) {
            return TokenEntity.TransferPayload.empty(token.address)
        }
        if (tokenCustomPayload != null && tokenCustomPayload!!.tokenAddress.equalsAddress(token.address)) {
            return tokenCustomPayload!!
        }

        if (tokenCustomPayload == null) {
            tokenCustomPayload =
                api.getJettonCustomPayload(wallet.accountId, wallet.testnet, token.address)
        }
        return tokenCustomPayload ?: TokenEntity.TransferPayload.empty(token.address)
    }
}
