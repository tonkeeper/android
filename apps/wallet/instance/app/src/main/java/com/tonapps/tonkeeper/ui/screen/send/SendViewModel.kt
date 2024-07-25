package com.tonapps.tonkeeper.ui.screen.send

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.blockchain.ton.extensions.isValidTonAddress
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.state
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.api.totalFees
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.core.entities.SendMetadataEntity
import com.tonapps.tonkeeper.core.entities.TransferEntity
import com.tonapps.tonkeeper.ui.screen.send.state.SendAmountState
import com.tonapps.tonkeeper.ui.screen.send.state.SendFeeState
import com.tonapps.tonkeeper.ui.screen.send.state.SendTransaction
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.collectibles.CollectiblesRepository
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.localization.Localization
import io.tonapi.models.Account
import io.tonapi.models.AccountStatus
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
import kotlinx.coroutines.flow.flatMap
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withContext
import org.ton.bitstring.BitString
import org.ton.cell.Cell
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(FlowPreview::class)
class SendViewModel(
    private val nftAddress: String,
    private val accountRepository: AccountRepository,
    private val api: API,
    private val settingsRepository: SettingsRepository,
    private val tokenRepository: TokenRepository,
    private val ratesRepository: RatesRepository,
    private val passcodeManager: PasscodeManager,
    private val collectiblesRepository: CollectiblesRepository,
): ViewModel() {

    private val isNft: Boolean
        get() = nftAddress.isNotBlank()

    data class UserInput(
        val address: String = "",
        val amount: Double = 0.0,
        val token: TokenEntity = TokenEntity.TON,
        val comment: String? = null,
        val nft: NftEntity? = null,
    )

    sealed class Recipient {
        data class Data(val raw: Account): Recipient()
        data object Empty: Recipient()
        data object NotFound: Recipient()
    }

    private val currency = settingsRepository.currency
    private var amountInputCurrency = false

    private val _userInputFlow = MutableStateFlow(UserInput())
    private val userInputFlow = _userInputFlow.asStateFlow()

    private val userInputAddressFlow = userInputFlow
        .map { it.address }
        .distinctUntilChanged()
        .debounce { if (it.isEmpty()) 0 else 600 }

    val recipientFlow = combine(
        accountRepository.selectedWalletFlow,
        userInputAddressFlow,
    ) { wallet, address ->
        if (address.isEmpty()) {
            return@combine Recipient.Empty
        }
        val account = api.resolveAccount(address, wallet.testnet) ?: return@combine Recipient.NotFound
        Recipient.Data(account)
    }.state(viewModelScope)

    val tokensFlow = accountRepository.selectedWalletFlow.map { wallet ->
        tokenRepository.get(currency, wallet.accountId, wallet.testnet)
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedTokenFlow = combine(
        tokensFlow,
        userInputFlow.map { it.token }.distinctUntilChanged()
    ) { tokens, selectedToken ->
        tokens.find { it.address == selectedToken.address } ?: AccountTokenEntity.EMPTY
    }.distinctUntilChanged().flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.Eagerly, AccountTokenEntity.EMPTY)

    val ratesTokenFlow = selectedTokenFlow.map { token ->
        ratesRepository.getRates(currency, token.address)
    }.state(viewModelScope)

    val uiInputAddressErrorFlow = recipientFlow.map { it is Recipient.NotFound }

    private val _uiInputAmountFlow = MutableEffectFlow<Coins>()
    val uiInputAmountFlow = _uiInputAmountFlow.asSharedFlow()

    val uiInputTokenFlow = userInputFlow.map { it.token }.filter { !isNft }.distinctUntilChanged()

    val uiInputNftFlow = userInputFlow.map { it.nft }.distinctUntilChanged().filterNotNull()

    val inputAmountFlow = userInputFlow.map { it.amount }.distinctUntilChanged()

    private val _uiEventFlow = MutableEffectFlow<SendEvent>()
    val uiEventFlow = _uiEventFlow.asSharedFlow()

    val uiBalanceFlow = combine(
        selectedTokenFlow,
        inputAmountFlow,
        ratesTokenFlow,
    ) { token, value, rates ->
        val (decimals, balance, currencyCode) = if (amountInputCurrency) {
            Triple(currency.decimals, token.fiat, currency.code)
        } else {
            Triple(token.decimals, token.balance.value, token.symbol)
        }

        val amount = Coins.of(value.toBigDecimal().stripTrailingZeros(), decimals)
        val remaining = balance - amount

        val convertedCode = if (amountInputCurrency) token.symbol else currency.code
        val converted = if (amountInputCurrency) {
            rates.convertFromFiat(token.address, amount)
        } else {
            rates.convert(token.address, amount)
        }

        val remainingToken = if (!amountInputCurrency) {
            token.balance.value - amount
        } else {
            rates.convertFromFiat(token.address, token.fiat - amount)
        }

        SendAmountState(
            remainingFormat = CurrencyFormatter.format(token.symbol, remainingToken, token.decimals, RoundingMode.UP, false),
            converted = converted.stripTrailingZeros(),
            convertedFormat = CurrencyFormatter.format(convertedCode, converted, decimals, RoundingMode.UP, false),
            insufficientBalance = !remaining.isPositive,
            currencyCode = if (amountInputCurrency) currencyCode else "",
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SendAmountState())

    val uiButtonEnabledFlow = combine(
        recipientFlow,
        uiBalanceFlow,
        inputAmountFlow,
    ) { recipient, balance, amount ->
        recipient is Recipient.Data && (isNft || (!balance.insufficientBalance && amount > 0))
    }

    private val amountTokenFlow = combine(
        selectedTokenFlow,
        inputAmountFlow,
        ratesTokenFlow
    ) { token, amount, rates ->
        val coins = Coins.of(amount, token.decimals)
        if (amountInputCurrency) {
            rates.convertFromFiat(token.address, coins)
        } else {
            coins
        }
    }

    private val uiTransferAmountFlow = combine(
        amountTokenFlow,
        selectedTokenFlow,
        ratesTokenFlow,
    ) { amount, token, rates ->
        SendTransaction.Amount(
            value = amount,
            converted = rates.convert(token.address, amount),
            format = CurrencyFormatter.format(token.symbol, amount, token.decimals, RoundingMode.UP, false),
            convertedFormat = CurrencyFormatter.format(currency.code, rates.convert(token.address, amount), token.decimals, RoundingMode.UP, false),
        )
    }

    val uiTransactionFlow = combine(
        accountRepository.selectedWalletFlow,
        recipientFlow.filter { it is Recipient.Data }.map { (it as Recipient.Data).raw },
        selectedTokenFlow,
        uiTransferAmountFlow,
        userInputFlow,
    ) { wallet, account, token, amount, userInput ->
        SendTransaction(
            fromWallet = wallet,
            targetAccount = account,
            token = token.balance,
            comment = userInput.comment,
            amount = amount,
        )
    }

    private val transferFlow = combine(
        accountRepository.selectedWalletFlow.distinctUntilChanged(),
        uiTransactionFlow.distinctUntilChanged(),
        userInputFlow.map { it.address }.distinctUntilChanged(),
        userInputFlow.map { it.comment }.distinctUntilChanged(),
        selectedTokenFlow,
    ) { wallet, transaction, userInputAddress, comment, token ->
        val sendMetadata = getSendParams(wallet)
        val builder = TransferEntity.Builder(wallet)
        builder.setToken(transaction.token)
        builder.setDestination(transaction.targetAccount.address)
        builder.setAmount(transaction.amount.value)
        builder.setSeqno(sendMetadata.seqno)
        builder.setComment(comment)
        builder.setValidUntil(sendMetadata.validUntil)
        builder.setBounceable(isBounce(userInputAddress, transaction.targetAccount))
        builder.setMax(transaction.amount.value == token.balance.value)
        if (isNft) {
            builder.setNftAddress(nftAddress)
        }
        builder.build()
    }.flowOn(Dispatchers.IO)

    init {
        if (isNft) {
            loadNft()
        }
    }

    private fun isInsufficientBalance(): Boolean {
        val token = selectedTokenFlow.value
        val amount = Coins.of(userInputFlow.value.amount, token.decimals)
        val balance = if (amountInputCurrency) token.fiat else token.balance.value
        val percentage = amount.value.divide(balance.value, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal("100"))
            .setScale(2, RoundingMode.HALF_UP)
        return percentage > BigDecimal("95.00")
    }

    fun next() {
        if (isInsufficientBalance()) {
            _uiEventFlow.tryEmit(SendEvent.InsufficientBalance)
            return
        }
        _uiEventFlow.tryEmit(SendEvent.Confirm)
        transferFlow.take(1).map { calculateFee(it) }.map { eventFee(it) }.filterNotNull().onEach {
            _uiEventFlow.tryEmit(it)
        }.flowOn(Dispatchers.IO).launchIn(viewModelScope)
    }

    private fun loadNft() {
        accountRepository.selectedWalletFlow.take(1).map { wallet ->
            collectiblesRepository.getNft(wallet.accountId, wallet.testnet, nftAddress)
        }.flowOn(Dispatchers.IO).filterNotNull().onEach(::userInputNft).launchIn(viewModelScope)
    }

    private suspend fun calculateFee(
        transfer: TransferEntity
    ): Coins = withContext(Dispatchers.IO) {
        val message = transfer.toSignedMessage(EmptyPrivateKeyEd25519)
        val fee = api.emulate(message, transfer.wallet.testnet).totalFees
        Coins.of(fee)
    }

    private fun eventFee(
        coins: Coins
    ): SendEvent.Fee? {
        return try {
            val code = TokenEntity.TON.symbol
            val rates = ratesRepository.getRates(currency, code)
            val converted = rates.convert(code, coins)

            SendEvent.Fee(
                value = coins,
                format = CurrencyFormatter.format(code, coins, TokenEntity.TON.decimals),
                convertedFormat = CurrencyFormatter.format(currency.code, converted, currency.decimals),
            )
        } catch (e: Throwable) {
            null
        }
    }

    fun userInputAmount(double: Double) {
        _userInputFlow.value = _userInputFlow.value.copy(amount = double)
    }

    fun userInputToken(token: TokenEntity) {
        _userInputFlow.value = _userInputFlow.value.copy(token = token)
    }

    fun userInputNft(nft: NftEntity) {
        _userInputFlow.value = _userInputFlow.value.copy(nft = nft)
    }

    fun userInputTokenByAddress(tokenAddress: String) {
        tokensFlow.filter { it.isNotEmpty() }.map { list ->
            list.find { it.address == tokenAddress }
        }.filterNotNull().take(1).onEach { token ->
            userInputToken(token.balance.token)
        }.launchIn(viewModelScope)
    }

    fun userInputAddress(address: String) {
        _userInputFlow.value = _userInputFlow.value.copy(address = address)
    }

    fun userInputComment(comment: String?) {
        _userInputFlow.value = _userInputFlow.value.copy(comment = comment)
    }

    fun swap() {
        amountInputCurrency = !amountInputCurrency
        val convertedAmount = uiBalanceFlow.value.converted
        _uiInputAmountFlow.tryEmit(convertedAmount)
    }

    fun setMax() {
        val token = selectedTokenFlow.value
        val coins = if (amountInputCurrency) {
            token.fiat
        } else {
            token.balance.value
        }
        _uiInputAmountFlow.tryEmit(coins)
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

    private fun isBounce(value: String, account: Account): Boolean {
        if (account.status != AccountStatus.active && value.startsWith("EQ")) {
            return false
        }
        val bounce = value.startsWith("EQ") || !value.startsWith("U")
        if (!value.isValidTonAddress()) {
            return !account.isWallet
        }
        return bounce
    }

    fun sendSignedMessage(signature: BitString) {
        launchTransfer(transferFlow.onEach { transfer ->
            send(signature, transfer)
        })
    }

    fun sendLedgerSignedMessage(body: Cell) {
        launchTransfer(transferFlow.onEach { transfer ->
            send(body, transfer)
        })
    }

    fun send(context: Context) {
        launchTransfer(transferFlow.onEach { transfer ->
            val wallet = transfer.wallet
            val unsignedBody = transfer.unsignedBody
            val ledgerTransaction = transfer.ledgerTransaction
            when (wallet.type) {
                Wallet.Type.Signer, Wallet.Type.SignerQR -> _uiEventFlow.tryEmit(SendEvent.Signer(unsignedBody, wallet.publicKey))
                Wallet.Type.Ledger -> _uiEventFlow.tryEmit(SendEvent.Ledger(ledgerTransaction, wallet.id))
                Wallet.Type.Watch -> throw SendException.UnableSendTransaction()
                else -> {
                    val isValidPasscode = passcodeManager.confirmation(context, context.getString(Localization.app_name))
                    if (!isValidPasscode) {
                        throw SendException.WrongPasscode()
                    }
                    val privateKey = accountRepository.getPrivateKey(wallet.id)
                    val signature = transfer.signedHash(privateKey)
                    send(signature, transfer)
                }
            }
        })
    }

    private suspend fun send(
        signature: BitString,
        transfer: TransferEntity
    ) {
        AnalyticsHelper.trackEvent("send_transaction")
        val message = transfer.transferMessage(signature)
        if (!api.sendToBlockchain(message, transfer.wallet.testnet)) {
            throw SendException.FailedToSendTransaction()
        }
        _uiEventFlow.tryEmit(SendEvent.Success)
        AnalyticsHelper.trackEvent("send_success")
    }

    private suspend fun send(
        body: Cell,
        transfer: TransferEntity,
    ) {
        val message = transfer.transferMessage(body)
        if (!api.sendToBlockchain(message, transfer.wallet.testnet)) {
            throw SendException.FailedToSendTransaction()
        }
        _uiEventFlow.tryEmit(SendEvent.Success)
    }

    private fun launchTransfer(flow: Flow<TransferEntity>) {
        flow.catch {
            _uiEventFlow.tryEmit(SendEvent.Failed)
        }.flowOn(Dispatchers.IO).take(1).launchIn(viewModelScope)
    }
}
