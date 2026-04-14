package com.tonapps.deposit.screens.send

import com.tonapps.blockchain.contract.Blockchain
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.TonAddressTags
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.blockchain.ton.extensions.isValidTonAddress
import com.tonapps.blockchain.tron.isValidTronAddress
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events
import com.tonapps.deposit.data.ExchangeRepository
import com.tonapps.deposit.screens.send.state.SendDestination
import com.tonapps.icu.Coins
import com.tonapps.log.L
import com.tonapps.mvi.MviFeature
import com.tonapps.mvi.MviRelay
import com.tonapps.mvi.MviSubject
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviState
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.props.MviProperty
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.localization.Localization
import io.exchangeapi.infrastructure.ApiResult
import io.exchangeapi.models.CreateExchangeRequest
import io.exchangeapi.models.ExchangeFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// TODO REMOVE ENTIRE SHITTY EXCHANGE DATA
interface SendModel {
    sealed interface Action : MviAction {
        data object Init : Action
        data object Continue : Action
        data object SetMax : Action
        data class AddressInput(val address: String) : Action
        data class AmountInput(val amount: String) : Action
        data class CommentInput(val comment: String?) : Action
        data class EncryptedCommentToggle(val enabled: Boolean) : Action
        data class SelectToken(val token: TokenEntity) : Action
        data class SelectTokenByAddress(val address: String) : Action
        data object Swap : Action
    }

    data class State(
        val address: String = "",
        val destination: SendDestination = SendDestination.Empty,
        val isResolvingAddress: Boolean = false,
        val isAddressLocked: Boolean = false,

        val amount: Coins = Coins.ZERO,
        val selectedToken: AccountTokenEntity = AccountTokenEntity.EMPTY,
        val isMaxAmount: Boolean = false,
        val remainingTokenBalance: Coins = Coins.ZERO,
        val availableTokens: List<AccountTokenEntity> = emptyList(),
        val hiddenBalance: Boolean = false,
        val insufficientBalance: Boolean = false,

        val currency: WalletCurrency = WalletCurrency.DEFAULT,
        val convertedAmount: Coins = Coins.ZERO,
        val amountCurrency: Boolean = false,

        val comment: String? = null,
        val isCommentAvailable: Boolean = true,
        val isCommentEncrypted: Boolean = false,
        val isMemoRequired: Boolean = false,
        val commentError: Int? = null,
        val encryptedCommentAvailable: Boolean = false,

        val isLedger: Boolean = false,
        val isContinueEnabled: Boolean = false,
        val isProcessing: Boolean = false,
        val exchangeAsset: WalletCurrency? = null,
        val exchangeMinAmount: Coins? = null,
        val exchangeMaxAmount: Coins? = null,
        val isNft: Boolean = false,
        val tronSwapUrl: String? = null,
        val tronSwapTitle: String? = null,
    ) : MviState {
        val isExchangeMode get() = exchangeAsset != null
    }

    
    class ViewState(
        val global: MviProperty<State>,
    ) : MviViewState

    sealed interface Event {
        data class NavigateToConfirm(val params: SendParams) : Event
        data class ShowError(val message: String) : Event
        data class UpdateAmount(val amount: Coins) : Event
        data object ClearAmount : Event
    }
}

data class SendFeatureData(
    val presetCurrency: WalletCurrency? = null,
    val presetAddress: String? = null,
    val sendExchangeData: SendExchangeData? = null,
    val analyticsFrom: Events.SendNative.SendNativeFrom = Events.SendNative.SendNativeFrom.WalletScreen,
)

@OptIn(FlowPreview::class)
class SendFeature(
    private val data: SendFeatureData,
    private val api: API,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val tokenRepository: TokenRepository,
    private val ratesRepository: RatesRepository,
    private val exchangeRepository: ExchangeRepository,
) : MviFeature<SendModel.Action, SendModel.State, SendModel.ViewState>(
    initState = SendModel.State(),
    initAction = SendModel.Action.Init,
) {

    private val relay = MviRelay<SendModel.Event>()
    val events = relay.events

    private val addressSubject = MviSubject<String>()

    // region Internal State
    private lateinit var wallet: WalletEntity
    private var nftAddress: String = ""

    private val isNft: Boolean get() = nftAddress.isNotBlank()
    private val currency: WalletCurrency get() = settingsRepository.currency
    private var tokens: List<AccountTokenEntity> = emptyList()
    private var tronAvailable: Boolean = false

    // endregion

    init {
        addressSubject.events
            .debounce { if (it.isEmpty()) 0L else 600L }
            .distinctUntilChanged()
            .mapLatest { address ->
                stateScope.launch {
                    resolveAddress(address)
                }
            }
            .launchIn(bgScope)
    }

    override fun createViewState(): SendModel.ViewState {
        return buildViewState {
            SendModel.ViewState(mviProperty { it })
        }
    }

    override suspend fun executeAction(action: SendModel.Action) {
        when (action) {
            is SendModel.Action.Init -> handleInit()
            is SendModel.Action.Continue -> handleContinue()
            is SendModel.Action.SetMax -> handleSetMax()
            is SendModel.Action.AddressInput -> handleAddressInput(action.address)
            is SendModel.Action.AmountInput -> handleAmountInput(action.amount)
            is SendModel.Action.CommentInput -> handleCommentInput(action.comment)
            is SendModel.Action.EncryptedCommentToggle -> handleEncryptedCommentToggle(action.enabled)
            is SendModel.Action.SelectToken -> handleSelectToken(action.token)
            is SendModel.Action.SelectTokenByAddress -> handleSelectTokenByAddress(action.address)
            is SendModel.Action.Swap -> handleSwap()
        }
    }

    // region Action Handlers
    private suspend fun handleInit() {
        AnalyticsHelper.Default.events.sendNative.sendOpen(from = data.analyticsFrom)

        val initData = withContext(Dispatchers.IO) {
            wallet = accountRepository.getSelectedWallet() ?: return@withContext null
            val fiatCurrency = settingsRepository.currency

            tokens = tokenRepository.get(fiatCurrency, wallet.accountId, wallet.network)
                ?: emptyList()

            tronAvailable = tokens.any { it.isTrc20 } && settingsRepository.getTronUsdtEnabled(wallet.id)

            val selectedToken = if (data.presetCurrency != null) {
                tokens.firstOrNull { it.address.equalsAddress(data.presetCurrency.address) }
            } else {
                null
            }
                ?: tokens.firstOrNull { it.isTon }
                ?: AccountTokenEntity.EMPTY

            val isExchange = data.sendExchangeData != null
            val config = api.getConfig(wallet.network)
            InitData(selectedToken, fiatCurrency, isExchange, config.tronSwapUrl, config.tronSwapTitle)
        } ?: return

        val (selectedToken, fiatCurrency, isExchange, tronSwapUrl, tronSwapTitle) = initData

        setState {
            copy(
                selectedToken = selectedToken,
                currency = fiatCurrency,
                hiddenBalance = settingsRepository.hiddenBalances,
                isLedger = wallet.isLedger,
                remainingTokenBalance = selectedToken.balance.uiBalance,
                isCommentAvailable = !isExchange && !selectedToken.isTrc20,
                availableTokens = tokens.filter { it.balance.isTransferable && !it.isTrx },
                isAddressLocked = data.presetAddress != null,
                exchangeAsset = data.sendExchangeData?.exchangeTo,
                isNft = nftAddress.isNotBlank(),
                tronSwapUrl = tronSwapUrl,
                tronSwapTitle = tronSwapTitle,
            )
        }

        if (!data.presetAddress.isNullOrEmpty()) {
            setState { copy(address = data.presetAddress, isResolvingAddress = true) }
            resolveAddress(data.presetAddress)
        }

        if (isExchange && data.sendExchangeData != null) {
            setState {
                copy(
                    exchangeMinAmount = data.sendExchangeData.minAmount?.toBigDecimalOrNull()?.let { Coins.of(it) },
                    exchangeMaxAmount = data.sendExchangeData.maxAmount?.toBigDecimalOrNull()?.let { Coins.of(it) },
                )
            }
        }
    }

    private data class InitData(
        val selectedToken: AccountTokenEntity,
        val fiatCurrency: WalletCurrency,
        val isExchange: Boolean,
        val tronSwapUrl: String,
        val tronSwapTitle: String,
    )

    private fun handleAddressInput(address: String) {
        val state = obtainState()
        if (state.isAddressLocked) return

        if (state.isExchangeMode) {
            setState {
                copy(
                    address = address,
                    destination = if (address.isNotEmpty()) {
                        SendDestination.ExternalAccount(address)
                    } else {
                        SendDestination.Empty
                    },
                    isResolvingAddress = false,
                )
            }
            recalculateButtonEnabled()
            return
        }

        setState {
            copy(address = address, isResolvingAddress = address.isNotEmpty())
        }

        addressSubject.emit(address)
    }

    private suspend fun handleAmountInput(amount: String) {
        setState {
            copy(amount = Coins.of(amount, selectedToken.decimals), isMaxAmount = false)
        }

        recalculateBalance()
    }

    private fun handleCommentInput(comment: String?) {
        setState {
            val error = if (wallet.isLedger && !comment.isNullOrEmpty() && !comment.all { it.code in 32..126 }) {
                Localization.ledger_comment_error
            } else {
                null
            }
            copy(comment = comment?.trim(), commentError = error)
        }
        recalculateButtonEnabled()
    }

    private fun handleEncryptedCommentToggle(enabled: Boolean) {
        setState { copy(isCommentEncrypted = enabled) }
    }

    private suspend fun handleContinue() {
        val state = obtainState()
        if (state.isExchangeMode) {
            handleExchangeContinue()
            return
        }

        val input = obtainState()

        AnalyticsHelper.Default.events.sendNative.sendClick(
            from = data.analyticsFrom,
            assetNetwork = input.selectedToken.balance.token.blockchain.id,
            tokenSymbol = input.selectedToken.symbol,
            amount = input.amount.value.toDouble(),
        )

        try {
            val tokenAmount = getTokenAmount(input)

            val params = SendParams(
                walletId = wallet.id,
                destination = input.destination,
                selectedToken = input.selectedToken,
                tokenAmount = tokenAmount,
                isMaxAmount = input.isMaxAmount,
                comment = input.comment?.ifBlank { null },
                encryptedComment = input.isCommentEncrypted,
                currency = currency,
                nftAddress = nftAddress,
                tokens = tokens,
                tronAvailable = tronAvailable,
                exchangeData = data.sendExchangeData?.let {
                    SendParams.Exchange(
                        currency = it.exchangeTo,
                        exchangeAddress = input.address,
                        withdrawalFee = it.withdrawalFee,
                    )
                },
                analyticsFrom = data.analyticsFrom,
            )

            relay.emit(SendModel.Event.NavigateToConfirm(params))
        } catch (e: Throwable) {
            L.e("SendFeature", "Continue error", e)
            relay.emit(SendModel.Event.ShowError(e.message ?: "Unknown error"))
        }
    }

    private suspend fun handleExchangeContinue() {
        val input = obtainState()
        val externalAddress = (input.destination as? SendDestination.ExternalAccount)?.address
            ?: return

        setState { copy(isProcessing = true) }

        try {
            val tokenAmount = getTokenAmount(input)

            val exchangeTo = data.sendExchangeData!!.exchangeTo
            val apiResult = exchangeRepository.createExchange(
                CreateExchangeRequest(
                    from = input.selectedToken.symbol,
                    to = exchangeTo.code,
                    wallet = externalAddress,
                    fromNetwork = data.presetCurrency?.network,
                    toNetwork = exchangeTo.network,
                    flow = ExchangeFlow.withdraw,
                )
            )

            val exchangeResult = when (apiResult) {
                is ApiResult.Success -> apiResult.data
                is ApiResult.Error -> {
                    setState { copy(isProcessing = false) }
                    relay.emit(SendModel.Event.ShowError(apiResult.message))
                    return
                }
            }

            val payinAddress = exchangeResult.payinAddress

            // Resolve the exchange's payin address as a TON/TRON destination
            val destination = withContext(Dispatchers.IO) {
                if (tronAvailable && input.selectedToken.isTrc20 && payinAddress.isValidTronAddress()) {
                    SendDestination.TronAccount(payinAddress)
                } else {
                    getDestinationAccount(payinAddress)
                }
            }

            if (destination !is SendDestination.TonAccount && destination !is SendDestination.TronAccount) {
                throw IllegalStateException("Failed to resolve exchange address")
            }

            val params = SendParams(
                walletId = wallet.id,
                destination = destination,
                selectedToken = input.selectedToken,
                tokenAmount = tokenAmount,
                isMaxAmount = input.isMaxAmount,
                comment = "Cross-chain withdrawal",
                encryptedComment = false,
                currency = currency,
                nftAddress = nftAddress,
                tokens = tokens,
                tronAvailable = tronAvailable,
                exchangeData = SendParams.Exchange(
                    currency = exchangeTo,
                    exchangeAddress = input.address,
                    estimatedDurationSeconds = exchangeResult.estimatedDuration,
                    withdrawalFee = data.sendExchangeData.withdrawalFee,
                ),
                analyticsFrom = data.analyticsFrom,
            )

            setState { copy(isProcessing = false) }
            relay.emit(SendModel.Event.NavigateToConfirm(params))
        } catch (e: Throwable) {
            L.e("SendFeature", "Exchange continue error", e)
            setState { copy(isProcessing = false) }
            relay.emit(SendModel.Event.ShowError(e.message ?: "Unknown error"))
        }
    }

    private suspend fun handleSetMax() {
        val input = obtainState()
        val maxAmount = if (input.amountCurrency) {
            input.selectedToken.fiat
        } else {
            input.selectedToken.balance.uiBalance
        }

        setState { copy(amount = maxAmount, isMaxAmount = true) }
        relay.emit(SendModel.Event.UpdateAmount(maxAmount))
        recalculateBalance()
    }

    private suspend fun handleSelectToken(token: TokenEntity) {
        val accountToken = tokens.find { it.address.equalsAddress(token.address) }
            ?: AccountTokenEntity.createEmpty(token, wallet.address)

        setState {
            copy(
                selectedToken = accountToken,
                amount = Coins.of(0, accountToken.decimals),
                isMaxAmount = false,
                isCommentAvailable = !isExchangeMode && !accountToken.isTrc20,
                remainingTokenBalance = accountToken.balance.uiBalance,
            )
        }
        relay.emit(SendModel.Event.ClearAmount)

        // Re-resolve address for token/blockchain mismatch
        val input = obtainState()
        if (input.address.isNotEmpty()) {
            resolveAddress(input.address)
        }

        recalculateBalance()
    }

    private suspend fun handleSelectTokenByAddress(jettonAddress: String) {
        val token = tokens.firstOrNull { it.address.equalsAddress(jettonAddress) } ?: return
        handleSelectToken(token.token)
    }

    // endregion

    // region Address Resolution

    private suspend fun resolveAddress(address: String) {
        if (address.isEmpty()) {
            setState {
                copy(
                    destination = SendDestination.Empty,
                    isResolvingAddress = false,
                    isMemoRequired = false,
                )
            }
            recalculateButtonEnabled()
            return
        }

        setState { copy(isResolvingAddress = true) }

        val input = obtainState()
        val selectedToken = input.selectedToken

        val destination = withContext(Dispatchers.IO) {
            if (tronAvailable && address.isValidTronAddress()) {
                if (selectedToken.isTrc20) {
                    SendDestination.TronAccount(address)
                } else {
                    SendDestination.TokenError(
                        addressBlockchain = Blockchain.TRON,
                        selectedToken = selectedToken.token,
                    )
                }
            } else {
                val tonDest = getDestinationAccount(address)
                if (tonDest is SendDestination.TonAccount && selectedToken.isTrc20) {
                    SendDestination.TokenError(
                        addressBlockchain = Blockchain.TON,
                        selectedToken = selectedToken.token,
                    )
                } else {
                    tonDest
                }
            }
        }

        val memoRequired = (destination as? SendDestination.TonAccount)?.memoRequired == true
        val existing = (destination as? SendDestination.TonAccount)?.existing == true
        val encryptedAvailable = existing && !memoRequired &&
            (wallet.type == WalletType.Default || wallet.type == WalletType.Testnet || wallet.type == WalletType.Lockup)

        setState {
            copy(
                destination = destination,
                isResolvingAddress = false,
                isMemoRequired = memoRequired,
                encryptedCommentAvailable = encryptedAvailable,
                isCommentAvailable = !isExchangeMode && !selectedToken.isTrc20,
            )
        }

        recalculateButtonEnabled()
    }

    private suspend fun getDestinationAccount(userInput: String): SendDestination = withContext(Dispatchers.IO) {
        val tonAddressTags = TonAddressTags.of(userInput)
        if (tonAddressTags.userFriendly && tonAddressTags.isTestnet != wallet.testnet) {
            return@withContext SendDestination.NotFound
        }

        val accountDeferred = async { api.resolveAccount(userInput, wallet.network) }
        val publicKeyDeferred = async { api.safeGetPublicKey(userInput, wallet.network) }

        val account = accountDeferred.await() ?: return@withContext SendDestination.NotFound
        val publicKey = publicKeyDeferred.await()

        if (account.isScam == true) {
            return@withContext SendDestination.Scam
        }

        SendDestination.TonAccount(
            userInput = userInput,
            isUserInputAddress = userInput.isValidTonAddress(),
            publicKey = publicKey,
            account = account,
            testnet = wallet.testnet,
            tonAddressTags = tonAddressTags,
        )
    }

    // endregion

    // region Balance Recalculation
    private suspend fun recalculateBalance() {
        val input = obtainState()
        val token = input.selectedToken
        val amount = input.amount
        val amountCurrency = input.amountCurrency
        val rates = ratesRepository.getRates(wallet.network, currency, token.address)

        val balance = if (amountCurrency) token.fiat else token.balance.uiBalance
        val remaining = balance - amount

        val converted = if (amountCurrency) {
            rates.convertFromFiat(token.address, amount)
        } else {
            rates.convert(token.address, amount)
        }

        val remainingToken = if (amountCurrency) {
            rates.convertFromFiat(token.address, token.fiat - amount)
        } else {
            token.balance.uiBalance - amount
        }

        val insufficientBalance = if (remaining.isZero) false else remaining.isNegative

        setState {
            copy(
                remainingTokenBalance = remainingToken,
                convertedAmount = converted.stripTrailingZeros(),
                insufficientBalance = insufficientBalance,
            )
        }

        recalculateButtonEnabled()
    }

    private suspend fun handleSwap() {
        val input = obtainState()
        val convertedAmount = input.convertedAmount

        setState {
            copy(
                amountCurrency = !amountCurrency,
                amount = convertedAmount,
            )
        }
        relay.emit(SendModel.Event.UpdateAmount(convertedAmount))
        recalculateBalance()
    }

    private fun recalculateButtonEnabled() {
        val input = obtainState()
        val dest = input.destination
        val amount = input.amount
        val comment = input.comment

        val isValidDestination = dest is SendDestination.TonAccount
            || dest is SendDestination.TronAccount
            || (dest is SendDestination.ExternalAccount && dest.address.isNotEmpty())

        val exchangeOutOfBounds = input.isExchangeMode && amount.isPositive && (
            (input.exchangeMinAmount != null && amount < input.exchangeMinAmount) ||
            (input.exchangeMaxAmount != null && amount > input.exchangeMaxAmount)
        )

        val enabled = when {
            input.isProcessing -> false
            !isValidDestination -> false
            dest is SendDestination.TonAccount && dest.memoRequired && comment.isNullOrEmpty() -> false
            input.commentError != null -> false
            exchangeOutOfBounds -> false
            isNft || (!input.insufficientBalance && amount.isPositive) -> true
            else -> false
        }

        setState { copy(isContinueEnabled = enabled) }
    }

    // endregion

    // region Helpers

    private suspend fun getTokenAmount(input: SendModel.State): Coins {
        if (!input.amountCurrency) return input.amount
        val rates = ratesRepository.getRates(wallet.network, currency, input.selectedToken.address)
        return rates.convertFromFiat(input.selectedToken.address, input.amount)
    }

    // endregion
}
