package com.tonapps.tonkeeper.ui.screen.swap.omniston

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.tonapps.async.Async
import com.tonapps.blockchain.model.legacy.MessageBodyEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.errors.InsufficientBalanceType
import com.tonapps.blockchain.model.legacy.errors.InsufficientFundsException
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.ton.extensions.cellFromHex
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.opTerminal
import com.tonapps.deposit.screens.send.state.SendFee
import com.tonapps.deposit.usecase.emulation.Emulated.Companion.buildFee
import com.tonapps.deposit.usecase.emulation.EmulationUseCase
import com.tonapps.deposit.usecase.sign.SignUseCase
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.currentTimeMillis
import com.tonapps.extensions.currentTimeSecondsInt
import com.tonapps.extensions.generateUuid
import com.tonapps.extensions.mapList
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.ledger.ton.Transaction
import com.tonapps.tonkeeper.extensions.getTransfers
import com.tonapps.tonkeeper.extensions.method
import com.tonapps.tonkeeper.helper.BatteryHelper
import com.tonapps.tonkeeper.helper.TwinInput
import com.tonapps.tonkeeper.helper.TwinInput.Companion.opposite
import com.tonapps.tonkeeper.manager.assets.AssetsManager
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.component.coin.CoinEditText.Companion.asString2
import com.tonapps.tonkeeper.ui.screen.swap.omniston.state.OmnistonStep
import com.tonapps.tonkeeper.ui.screen.swap.omniston.state.SwapQuoteState
import com.tonapps.tonkeeper.ui.screen.swap.omniston.state.SwapRequest
import com.tonapps.tonkeeper.ui.screen.swap.omniston.state.SwapTokenState
import com.tonapps.tonkeeper.ui.screen.swap.picker.SwapPickerScreen
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.SwapEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.core.entity.RawMessageEntity
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.BatteryTransaction
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.entities.PreferredFeeMethod
import com.tonapps.wallet.data.swap.SwapRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.data.tx.TransactionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.ton.cell.Cell
import org.ton.contract.wallet.WalletTransfer
import uikit.UiButtonState
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@Suppress("LargeClass")
class OmnistonViewModel(
    app: Application,
    args: OmnistonArgs,
    private val wallet: WalletEntity,
    private val swapRepository: SwapRepository,
    private val tokenRepository: TokenRepository,
    private val assetsManager: AssetsManager,
    private val api: API,
    private val signUseCase: SignUseCase,
    private val transactionManager: TransactionManager,
    private val accountRepository: AccountRepository,
    private val batteryRepository: BatteryRepository,
    private val ratesRepository: RatesRepository,
    private val settingsRepository: SettingsRepository,
    private val emulationUseCase: EmulationUseCase,
): BaseWalletVM(app) {

    private companion object {
        private val defaultFromCurrency = WalletCurrency.TON
        private val defaultToCurrency = WalletCurrency.USDT_TON
    }

    val installId: String
        get() = settingsRepository.installId

    val swapUri: Uri
        get() = api.getConfig(wallet.network).swapUri

    private var swapStreamJob: Job? = null

    private val twinInput = TwinInput(viewModelScope)

    private var countDownJob: Job? = null
    private val _countDownFlow = MutableStateFlow(0f)
    val countDownFlow = _countDownFlow.asStateFlow()

    private val _requestFocusFlow = MutableEffectFlow<TwinInput.Type?>()
    val requestFocusFlow = _requestFocusFlow.asSharedFlow().filterNotNull()

    private val lastSeqNo = AtomicInteger(0)
    private val walletsCountRef = AtomicInteger(0)

    private val _amountFlow = MutableEffectFlow<Coins>()
    @OptIn(FlowPreview::class)
    private val amountFlow = _amountFlow.asSharedFlow().debounce(1000)

    private val _quoteStateFlow = MutableStateFlow(SwapQuoteState())
    @OptIn(FlowPreview::class)
    val quoteStateFlow = _quoteStateFlow.asStateFlow().debounce(100)

    private val _stepFlow = MutableStateFlow(OmnistonStep.Input)
    val stepFlow = _stepFlow.asStateFlow()

    private val ratesFlow = swapRepository.assetsFlow
        .mapList { it.address }
        .map { ratesRepository.getRates(wallet.network, settingsRepository.currency, it) }

    val sendPlaceholderValueFlow = twinInput.createConvertFlow(ratesFlow, TwinInput.Type.Send).map {
        it.value.asString2(3)
    }.stateIn(viewModelScope, SharingStarted.Lazily, "")

    val receivePlaceholderValueFlow = twinInput.createConvertFlow(ratesFlow, TwinInput.Type.Receive).map {
        it.value.asString2(3)
    }.stateIn(viewModelScope, SharingStarted.Lazily, "")

    private val _sendOutputValueFlow = MutableStateFlow(Coins.ZERO)
    val sendOutputValueFlow = _sendOutputValueFlow.asStateFlow()

    private val _receiveOutputValueFlow = MutableStateFlow(Coins.ZERO)
    val receiveOutputValueFlow = _receiveOutputValueFlow.asStateFlow()

    val sendOutputCurrencyFlow = twinInput.stateFlow.map { it.sendCurrency }.distinctUntilChanged()
    val receiveOutputCurrencyFlow = twinInput.stateFlow.map { it.receiveCurrency }.distinctUntilChanged()

    val inputPrefixFlow = twinInput.stateFlow.map { it.focus.opposite }.distinctUntilChanged()

    val priceFlow = combine(ratesFlow, twinInput.stateFlow) { rates, inputsState ->
        val coins = Coins.ONE
        val value = inputsState.convert(
            rates = rates,
            fromType = TwinInput.Type.Send,
            value = coins
        )
        val formatFrom = CurrencyFormatter.format(inputsState.sendCurrency.code, coins)
        val formatTo = CurrencyFormatter.format(inputsState.receiveCurrency.code, value)

        val valueReversed = inputsState.convert(
            rates = rates,
            fromType = TwinInput.Type.Receive,
            value = coins
        )

        val formatFromReversed = CurrencyFormatter.format(inputsState.receiveCurrency.code, coins)
        val formatToReversed = CurrencyFormatter.format(inputsState.sendCurrency.code, valueReversed)

        Pair("$formatFrom ≈ $formatTo", "$formatFromReversed ≈ $formatToReversed")
    }

    private val tokenBalanceFlow = twinInput.stateFlow
        .map { it.sendCurrency }
        .distinctUntilChanged()
        .map { send ->
            assetsManager.getToken(wallet, send.address)
        }

    val uiStateToken = combine(
        tokenBalanceFlow,
        twinInput.stateFlow.map { it.send.coins }.distinctUntilChanged()
    ) { token, sendAmount ->
        if (token == null) {
            SwapTokenState()
        } else {
            val remaining = token.token.balance.value - sendAmount
            SwapTokenState(
                fromToken = token,
                remaining = remaining
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SwapTokenState())

    private val _uiButtonStateFlow = MutableStateFlow<UiButtonState>(UiButtonState.Default(false))
    val uiButtonStateFlow = _uiButtonStateFlow.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val swapRequestFlow = combine(
        twinInput.stateFlow.map { it.send }.distinctUntilChanged(),
        twinInput.stateFlow.map { it.receive }.distinctUntilChanged(),
    ) { send, receive ->
        val focus = twinInput.state.focus
        val amount = if (focus == TwinInput.Type.Send) send.coins else receive.coins
        SwapRequest(focus, amount, send.currency, receive.currency)
    }.distinctUntilChanged()

    val jettonSymbolFrom: String
        get() = twinInput.state.getCurrency(TwinInput.Type.Send).symbol + "_ton"

    val jettonSymbolTo: String
        get() = twinInput.state.getCurrency(TwinInput.Type.Receive).symbol + "_ton"

    val providerName: String
        get() = _quoteStateFlow.value.provider.ifEmpty { "unknown" }

    val providerUrl: String
        get() = "unknown"

    private val lastMessages = AtomicReference<SwapEntity.Messages?>(null)

    init {
        applyInputsObserver()
        applyDefaultCurrencies()
        applyCurrenciesFromArgs(args)

        twinInput.stateFlow.collectFlow {
            checkButtonState()
        }
    }

    private fun applyCurrenciesFromArgs(args: OmnistonArgs) {
        swapRepository.assetsFlow.filterNotNull().take(1).onEach { currencies ->
            val from = currencies.firstOrNull { it.containsQuery(args.fromToken) } ?: defaultFromCurrency
            val to = args.toToken?.let { raw ->
                currencies.firstOrNull { it.containsQuery(raw) }
            } ?: defaultToCurrency

            if (from == to) {
                applyDefaultCurrencies()
            } else {
                updateSendCurrency(from)
                updateReceiveCurrency(to)
            }
        }.launch()
    }

    @OptIn(FlowPreview::class)
    private fun applyInputsObserver() {
        swapRequestFlow.filterNotNull()
            .onEach {
                cancelSwapStream()
                if (it.isEmpty) {
                    checkButtonState()
                } else {
                    setButtonState(UiButtonState.Loading)
                }
            }
            .debounce(600)
            .onEach(::startSwapStream)
            .launch()

        /*collectFlow(twinInput.stateFlow.filter { it.focus == TwinInput.Type.Send }.map { it.send.coins }.distinctUntilChanged()) {
            _receiveOutputValueFlow.value = Coins.ZERO
        }

        collectFlow(twinInput.stateFlow.filter { it.focus == TwinInput.Type.Receive }.map { it.receive.coins }.distinctUntilChanged()) {
            _sendOutputValueFlow.value = Coins.ZERO
        }*/
    }

    private fun setMessages(messages: SwapEntity.Messages) {
        if (messages.isEmpty) {
            lastMessages.set(null)
            checkButtonState()
            return
        }

        lastMessages.set(messages)
        if (twinInput.state.focus == TwinInput.Type.Send) {
            val amount = Coins.ofNano(messages.askUnits, twinInput.state.receive.decimals)
            _receiveOutputValueFlow.value = amount
        } else if (twinInput.state.focus == TwinInput.Type.Receive) {
            val amount = Coins.ofNano(messages.bidUnits, twinInput.state.send.decimals)
            _sendOutputValueFlow.value = amount
        }

        checkButtonState()
    }

    private fun checkButtonState() {
        if (twinInput.state.isEmpty || lastMessages.get() == null) {
            setButtonState(UiButtonState.Default(false))
            return
        }
        val insufficientBalance = uiStateToken.value.insufficientBalance
        setButtonState(UiButtonState.Default(!insufficientBalance))
    }

    private fun setOutputValue(type: TwinInput.Type, value: Coins) {
        if (type == TwinInput.Type.Send) {
            _sendOutputValueFlow.value = value
            twinInput.updateValue(type, value.value.asString2())
        } else {
            _receiveOutputValueFlow.value = value
            twinInput.updateValue(type, value.value.asString2())
        }
    }

    private fun setButtonState(state: UiButtonState) {
        _uiButtonStateFlow.value = state
    }

    fun updateFocusInput(type: TwinInput.Type) {
        twinInput.updateFocus(type)
    }

    fun updateSendCurrency(currency: WalletCurrency) {
        twinInput.updateCurrency(TwinInput.Type.Send, currency)
    }

    fun updateSendInput(amount: String) {
        if (twinInput.state.focus == TwinInput.Type.Send) {
            _sendOutputValueFlow.value = Coins.of(amount, twinInput.state.send.decimals)
            _receiveOutputValueFlow.value = Coins.ZERO
        }
        twinInput.updateValue(TwinInput.Type.Send, amount)
    }

    fun updateReceiveInput(amount: String) {
        if (twinInput.state.focus == TwinInput.Type.Receive) {
            _receiveOutputValueFlow.value = Coins.of(amount, twinInput.state.receive.decimals)
            _sendOutputValueFlow.value = Coins.ZERO
        }
        twinInput.updateValue(TwinInput.Type.Receive, amount)
    }

    fun updateReceiveCurrency(currency: WalletCurrency) {
        twinInput.updateCurrency(TwinInput.Type.Receive, currency)
    }

    private fun applyDefaultCurrencies() {
        updateSendCurrency(defaultFromCurrency)
        updateReceiveCurrency(defaultToCurrency)
    }

    fun switch() {
        viewModelScope.launch(Dispatchers.IO) {
            val oldSendValue = twinInput.state.send.value.trim()
            val oldReceiveValue = twinInput.state.receive.value.trim()
            val oldSendPlaceholder = sendPlaceholderValueFlow.value
            val oldReceivePlaceholder = receivePlaceholderValueFlow.value

            twinInput.switch()

            if (oldSendValue.isEmpty() && oldReceiveValue.isEmpty()) {
                cancelSwapStream()
                setButtonState(UiButtonState.Default(false))
            } else {
                val newSendDec = twinInput.state.send.decimals
                val newReceiveDec = twinInput.state.receive.decimals
                val sendValue = oldSendValue.ifEmpty { oldSendPlaceholder }
                val receiveValue = oldReceiveValue.ifEmpty { oldReceivePlaceholder }

                _sendOutputValueFlow.value = Coins.of(receiveValue, newSendDec)

                _receiveOutputValueFlow.value = Coins.of(sendValue, newReceiveDec)
            }
        }
    }

    fun pickCurrency(forType: TwinInput.Type) = viewModelScope.launch {
        runCatching {
            val selectedCurrency = twinInput.getCurrency(forType)
            val ignoreCurrency = twinInput.getCurrency(forType.opposite)
            SwapPickerScreen.run(context, wallet, selectedCurrency, ignoreCurrency, forType == TwinInput.Type.Send)
        }.onSuccess { currency ->
            if (currency == twinInput.state.getCurrency(forType)) {
                return@onSuccess
            } else if (twinInput.state.hasCurrency(currency)) {
                _requestFocusFlow.tryEmit(forType)
                switch()
            } else {
                _requestFocusFlow.tryEmit(twinInput.state.focus)
                twinInput.updateCurrency(forType, currency)
            }
            if (twinInput.state.focus.opposite == TwinInput.Type.Send) {
                _sendOutputValueFlow.value = Coins.ZERO
            } else {
                _receiveOutputValueFlow.value = Coins.ZERO
            }

            cancelSwapStream()
            checkButtonState()
        }
    }

    private suspend fun requestTONToken(): AccountTokenEntity? {
        return tokenRepository.getTON(
            currency = settingsRepository.currency,
            accountId = wallet.accountId,
            network = wallet.network,
        )
    }

    private suspend fun isSingleWallet(): Boolean {
        if (walletsCountRef.get() == 0) {
            walletsCountRef.set(accountRepository.getWallets().size)
        }
        return walletsCountRef.get() == 1
    }

    suspend fun next() {
        withContext(Async.ioContext()) {
            swapStreamJob?.cancel()
            swapStreamJob = null

            val stateMessages = lastMessages.getAndSet(null) ?: return@withContext
            cancelSwapStream()

            val tonBalance = requestTONToken() ?: return@withContext
            val signRequest = createMessages(stateMessages.messages) ?: return@withContext

            val batteryEnabled = isBatteryIsEnabledTx()
            val stateToken = uiStateToken.value
            val fromCurrency = twinInput.state.send.currency
            val toCurrency = twinInput.state.receive.currency
            val bidUnits = Coins.ofNano(stateMessages.bidUnits, fromCurrency.decimals)
            val askUnits = Coins.ofNano(stateMessages.askUnits, toCurrency.decimals)
            val isMaxTon = fromCurrency == WalletCurrency.TON && bidUnits.compareTo(stateToken.balance) == 0
            if (bidUnits > stateToken.balance) {
                throw InsufficientFundsException(
                    currency = fromCurrency,
                    required = bidUnits,
                    available = stateToken.balance,
                    type = if (stateToken.isTon) InsufficientBalanceType.InsufficientTONBalance else InsufficientBalanceType.InsufficientJettonBalance,
                    withRechargeBattery = false,
                    singleWallet = isSingleWallet()
                )
            }

            val tx = createEmulationTx(signRequest, batteryEnabled)
            var preferredFeeMethod = settingsRepository.getPreferredFeeMethod(wallet.id)
            var canEditFeeMethod = true
            val gasBudget = Coins.ofNano(stateMessages.gasBudget)
            val estimatedGasConsumption = Coins.ofNano(stateMessages.estimatedGasConsumption)
            val totalTonFee = tx.tonEmulated?.totalFees ?: Coins.ZERO
            val maxRequiredFee = listOf(gasBudget, estimatedGasConsumption, totalTonFee).max()
            if (fromCurrency == WalletCurrency.TON && !isMaxTon && (bidUnits + maxRequiredFee) > tonBalance.balance.value) {
                val requiredTONBalance = bidUnits + maxRequiredFee
                if (requiredTONBalance >= tonBalance.balance.value) {
                    throw InsufficientFundsException(
                        currency = WalletCurrency.TON,
                        required = requiredTONBalance,
                        available = tonBalance.balance.value,
                        type = InsufficientBalanceType.InsufficientBalanceForFee,
                        withRechargeBattery = false,
                        singleWallet = isSingleWallet()
                    )
                }
            } else if (fromCurrency != WalletCurrency.TON) {
                if (tx.batteryEmulated == null && maxRequiredFee > tonBalance.balance.value) {
                    throw InsufficientFundsException(
                        currency = WalletCurrency.TON,
                        required = maxRequiredFee,
                        available = tonBalance.balance.value,
                        type = InsufficientBalanceType.InsufficientBalanceForFee,
                        withRechargeBattery = true,
                        singleWallet = isSingleWallet()
                    )
                } else if (maxRequiredFee > tonBalance.balance.value) {
                    preferredFeeMethod = PreferredFeeMethod.BATTERY
                    canEditFeeMethod = false
                }
            }

            _quoteStateFlow.value = SwapQuoteState(
                toUnits = askUnits,
                provider = stateMessages.resolverName,
                fromCurrency = fromCurrency,
                toCurrency = toCurrency,
                signRequest = signRequest,
                fromUnits = bidUnits,
                gasBudget = gasBudget,
                estimatedGasConsumption = estimatedGasConsumption,
                tx = tx,
                selectedFee = tx.getFeeByMethod(preferredFeeMethod),
                canEditFeeMethod = canEditFeeMethod,
                slippage = stateMessages.slippage
            )

            _stepFlow.value = OmnistonStep.Review
            startResetTimer(stateMessages.tradeStartDeadline.toLong())
        }
    }

    private fun cancelResetTimer() {
        countDownJob?.cancel()
        countDownJob = null
    }

    private fun startResetTimer(tradeStartDeadline: Long) {
        cancelResetTimer()

        val deadlineMs = TimeUnit.SECONDS.toMillis(tradeStartDeadline)
        val nowMs = System.currentTimeMillis()
        val totalMs = (deadlineMs - nowMs).coerceAtLeast(0L)
        if (totalMs == 0L) {
            _countDownFlow.value = 1f
            return
        }

        countDownJob = viewModelScope.launch {
            while (isActive) {
                val remaining = (deadlineMs - System.currentTimeMillis()).coerceAtLeast(0L)
                val progress = 1f - (remaining.toFloat() / totalMs.toFloat())
                _countDownFlow.value = progress
                if (progress >= 1f) {
                    break
                }
                delay(80)
            }
        }
    }

    private fun getLedgerTransaction(
        message: MessageBodyEntity
    ): List<Transaction> {
        if (!message.wallet.isLedger) {
            return emptyList()
        }
        val transactions = mutableListOf<Transaction>()
        for ((index, transfer) in message.transfers.withIndex()) {
            transactions.add(
                Transaction.fromWalletTransfer(
                    walletTransfer = transfer,
                    seqno = message.seqNo + index,
                    timeout = message.validUntil
                )
            )
        }

        return transactions.toList()
    }

    fun setFeeMethod(fee: SendFee) {
        fee.method?.let {
            settingsRepository.setPreferredFeeMethod(wallet.id, it)
        }
        _quoteStateFlow.update { state ->
            state.copy(selectedFee = fee)
        }
    }

    fun sign(onSuccess: () -> Unit, onError: (Throwable) -> Unit) {
        val state = _quoteStateFlow.value
        val signRequest = state.signRequest ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val operationId = generateUuid()
            val startedAtMs = currentTimeMillis()
            AnalyticsHelper.Default.events.redOperations.opAttempt(
                operationId = operationId,
                flow = Events.RedOperations.RedOperationsFlow.Swap,
                operation = Events.RedOperations.RedOperationsOperation.Send,
                attemptSource = null,
                startedAtMs = currentTimeSecondsInt(),
                otherMetadata = null,
            )
            try {
                val isBattery = state.isPreferredFeeMethodBattery
                val transfers = transfers(signRequest,false, isBattery)
                val validUntil = accountRepository.getValidUntil(wallet.network)
                val message = accountRepository.messageBody(wallet, validUntil, transfers)
                val unsignedBody = message.createUnsignedBody(isBattery)
                val ledgerTransactions = getLedgerTransaction(message)

                val cells = mutableListOf<Cell>()
                if (ledgerTransactions.size > 1) {
                    for ((index, transaction) in ledgerTransactions.withIndex()) {
                        val cell = signUseCase(
                            context = context,
                            wallet = wallet,
                            seqNo = transaction.seqno,
                            ledgerTransaction = transaction,
                            transactionIndex = index,
                            transactionCount = ledgerTransactions.size
                        )
                        cells.add(cell)
                    }
                } else {
                    val cell = signUseCase(
                        context = context,
                        wallet = wallet,
                        unsignedBody = unsignedBody,
                        ledgerTransaction = ledgerTransactions.firstOrNull(),
                        seqNo = getSeqNo()
                    )
                    cells.add(cell)
                }
                val confirmationTimeMillis = state.timestamp - System.currentTimeMillis()
                for (cell in cells) {
                    transactionManager.send(
                        wallet = wallet,
                        boc = cell,
                        withBattery = isBattery,
                        source = "swap",
                        confirmationTime = confirmationTimeMillis / 1000.0
                    )
                }

                val finishedAtMs = currentTimeMillis()
                AnalyticsHelper.Default.events.redOperations.opTerminal(
                    operationId = operationId,
                    flow = Events.RedOperations.RedOperationsFlow.Swap,
                    operation = Events.RedOperations.RedOperationsOperation.Send,
                    outcome = Events.RedOperations.RedOperationsOutcome.Success,
                    durationMs = (finishedAtMs - startedAtMs).toDouble(),
                    finishedAtMs = currentTimeSecondsInt(),
                    errorCode = null,
                    errorMessage = null,
                    errorType = null,
                    stage = null,
                    otherMetadata = null,
                )
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Throwable) {
                val finishedAtMs = currentTimeMillis()
                AnalyticsHelper.Default.events.redOperations.opTerminal(
                    operationId = operationId,
                    flow = Events.RedOperations.RedOperationsFlow.Swap,
                    operation = Events.RedOperations.RedOperationsOperation.Send,
                    durationMs = (finishedAtMs - startedAtMs).toDouble(),
                    finishedAtMs = currentTimeSecondsInt(),
                    error = e,
                )
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }

    private fun createMessages(messages: List<SwapEntity.Message>): SignRequestEntity? {
        if (messages.isEmpty()) {
            return null
        }
        val builder = SignRequestEntity.Builder().setTestnet(wallet.testnet)
        messages.forEach { omnistionTonMessage ->
            val payload = omnistionTonMessage.payload?.cellFromHex()
            builder.addMessage(RawMessageEntity.of(
                address = omnistionTonMessage.targetAddress,
                amount = Coins.ofNano(omnistionTonMessage.sendAmount).toBigInteger(),
                payload = payload?.base64()
            ))
        }
        return builder.build(Uri.EMPTY)
    }

    private suspend fun getSeqNo(): Int {
        var seqNo = lastSeqNo.get()
        if (seqNo == 0) {
            seqNo = accountRepository.getSeqno(wallet)
            lastSeqNo.set(seqNo)
        }
        return seqNo
    }

    private suspend fun isBatteryIsEnabledTx(): Boolean {
        if (twinInput.state.sendCurrency == WalletCurrency.TON) {
            return false
        }
        return BatteryHelper.isBatteryIsEnabledTx(wallet, BatteryTransaction.SWAP, settingsRepository, accountRepository, batteryRepository)
    }

    private suspend fun batteryEmulated(message: MessageBodyEntity) = BatteryHelper.emulation(
        wallet = wallet,
        message = message,
        emulationUseCase = emulationUseCase,
        accountRepository = accountRepository,
        batteryRepository = batteryRepository,
        params = true
    )

    private suspend fun getTonBalance() = tokenRepository.getTonBalance(settingsRepository.currency, wallet.accountId, wallet.network)

    private suspend fun transfers(
        request: SignRequestEntity,
        forEmulation: Boolean,
        batteryEnabled: Boolean
    ): List<WalletTransfer> {
        val excessesAddress = if (false) { // !forEmulation && batteryEnabled
            batteryRepository.getConfig(wallet.network).excessesAddress
        } else null

        return request.getTransfers(
            wallet = wallet,
            api = api,
            batteryEnabled = batteryEnabled,
            compressedTokens = emptyList(),
            excessesAddress = excessesAddress,
            tonBalance = getTonBalance()
        )
    }

    private suspend fun createEmulationTx(
        signRequest: SignRequestEntity,
        batteryEnabled: Boolean
    ): SwapQuoteState.Tx = withContext(Dispatchers.IO) {
        val validUntil = accountRepository.getValidUntil(wallet.network)
        val messageBody = MessageBodyEntity(
            wallet = wallet,
            seqNo = getSeqNo(),
            validUntil = validUntil,
            transfers = transfers(signRequest,true, batteryEnabled)
        )

        val tonDeferred = async {
            emulationUseCase(
                message = messageBody,
                useBattery = false,
                forceRelayer = false,
                params = true
            )
        }

        val batteryDeferred = async {
            if (batteryEnabled) {
                batteryEmulated(messageBody)
            } else {
                null
            }
        }

        val tonEmulated = tonDeferred.await()
        val batteryEmulated = batteryDeferred.await()

        val batteryFee = if (batteryEmulated != null && !batteryEmulated.failed) {
            batteryEmulated.buildFee(wallet, api, accountRepository, batteryRepository, ratesRepository)
        } else null

        val tonFee = tonEmulated.buildFee(wallet, api, accountRepository, batteryRepository, ratesRepository)

        SwapQuoteState.Tx(
            sendTonFee = tonFee,
            tonEmulated = tonEmulated,
            sendBatteryFee = batteryFee,
            batteryEmulated = batteryEmulated,
            messageBody = messageBody
        )
    }

    @OptIn(FlowPreview::class)
    private fun startSwapStream(request: SwapRequest) {
        cancelSwapStream()
        if (request.isEmpty) {
            checkButtonState()
            return
        }
        val insufficientBalance = uiStateToken.value.insufficientBalance
        if (insufficientBalance) {
            setButtonState(UiButtonState.Default(false))
            return
        }

        setButtonState(UiButtonState.Loading)
        val operationId = generateUuid()
        val startedAtMs = currentTimeMillis()
        AnalyticsHelper.Default.events.redOperations.opAttempt(
            operationId = operationId,
            flow = Events.RedOperations.RedOperationsFlow.Swap,
            operation = Events.RedOperations.RedOperationsOperation.Quote,
            attemptSource = null,
            startedAtMs = currentTimeSecondsInt(),
            otherMetadata = null,
        )
        var quoteReceived = false
        swapStreamJob = api.swapStream(
            from = request.fromParam,
            to = request.toParam,
            userAddress = wallet.address.toRawAddress()
        ).filterNotNull().debounce(800).onEach { messages ->
            if (!quoteReceived) {
                quoteReceived = true
                val finishedAtMs = currentTimeMillis()
                AnalyticsHelper.Default.events.redOperations.opTerminal(
                    operationId = operationId,
                    flow = Events.RedOperations.RedOperationsFlow.Swap,
                    operation = Events.RedOperations.RedOperationsOperation.Quote,
                    durationMs = (finishedAtMs - startedAtMs).toDouble(),
                    finishedAtMs = currentTimeSecondsInt(),
                    error = null,
                )
            }
            setMessages(messages)
        }.launch()
    }

    private fun cancelSwapStream() {
        lastMessages.set(null)
        cancelResetTimer()
        swapStreamJob?.cancel()
        swapStreamJob = null
    }

    // TODO rewrite later
    fun restoreSwapStream() {
        viewModelScope.launch {
            try {
                withTimeout(1000) {
                    val last = swapRequestFlow.firstOrNull() ?: throw Exception("Swap request is empty")
                    withContext(Dispatchers.Main) {
                        startSwapStream(last)
                        setButtonState(UiButtonState.Loading)
                    }
                }
            } catch (e: Throwable) {
                if (e !is CancellationException) {
                    finish()
                }
            }
        }
    }

    fun reset() {
        _stepFlow.value = OmnistonStep.Input
        restoreSwapStream()
    }
}