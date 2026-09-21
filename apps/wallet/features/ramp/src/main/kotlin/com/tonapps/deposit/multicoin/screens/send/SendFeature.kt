package com.tonapps.deposit.multicoin.screens.send

import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmInitiator
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.core.contract.TonAddressTags
import com.tonapps.bus.generated.Events.WithdrawFlow.WithdrawFlowFrom
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.isValidTonAddress
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.blockchain.utils.isWeb3DomainName
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.chainkit.core.chain.model.num.BaseUnit
import com.tonapps.core.components.AmountInputConverter
import com.tonapps.deposit.multicoin.analytics.WithdrawAnalytics
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.mvi.MviRelay
import com.tonapps.mvi.flow.mapLatestCatching
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import io.tonapi.models.AccountStatus
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private const val FORMAT_DECIMALS = 3

class SendData(
    val initAssetId: String,
    val presetAddress: String? = null,
    val analyticsFrom: WithdrawFlowFrom = WithdrawFlowFrom.WalletScreen,
    val presetAmount: String? = null,
    val presetComment: String? = null,
)

data class SendTxInfo(
    val assetId: String,
    val symbol: String,
    val amount: Double,
)

sealed interface AddressStatus {
    data class Valid(
        val to: String,
        val domain: String? = null,
        val memoRequired: Boolean = false,
    ) : AddressStatus
    data object Invalid : AddressStatus
    data object Scam : AddressStatus
}

private sealed interface AddressCandidate {
    val addr: String
    data class Final(override val addr: String, val status: AddressStatus) : AddressCandidate
    data class Resolve(override val addr: String) : AddressCandidate
}

sealed interface AmountError {
    data object Invalid : AmountError
    data object InsufficientBalance : AmountError
}

sealed interface SendEvent {
    data class Continue(val request: ConfirmRequest, val txInfo: SendTxInfo) : SendEvent
    data class ShowError(val message: String) : SendEvent
    data class UpdateAmount(val displayString: String) : SendEvent
}

@OptIn(FlowPreview::class)
class SendFeature(
    data: SendData,
    private val oldAccount: AccountRepository,
    private val accountRepo: McAccountRepository,
    private val api: API,
) : AsyncViewModel() {

    private val relay = MviRelay<SendEvent>()
    val events = relay.events

    // --- Wallet & Asset ---
    private val selectedWallet = MutableStateFlow<McWalletEntity?>(null)
    val selectedAccount: StateFlow<AccountWithDetails?> field = MutableStateFlow(null)
    val accountNotFound: StateFlow<Boolean> field = MutableStateFlow(false)

    // --- Address ---
    val isAddressLocked: Boolean = data.presetAddress != null
    val address: StateFlow<String> field = MutableStateFlow(data.presetAddress ?: "")
    private val addressValidation: StateFlow<Pair<String, AddressStatus>?> = combine(selectedAccount, address)
        { account, addr ->
            if (account == null || addr.isBlank()) {
                null
            } else {
                account to addr
            }
        }
        .debounce(300L)
        .map { data -> data?.let { (account, addr) -> validateLocally(account, addr) } }
        .mapLatestCatching { candidate ->
            when (candidate) {
                null -> null
                is AddressCandidate.Final -> candidate.addr to candidate.status
                is AddressCandidate.Resolve -> candidate.addr to resolveTonAccount(candidate.addr)
            }
        }
        .cacheState()

    val addressStatus: StateFlow<AddressStatus?> = combine(addressValidation, address)
        { result, addr -> result?.takeIf { it.first == addr }?.second }
        .cacheState()

    val addressLoading: StateFlow<Boolean> = combine(addressValidation, address)
        { result, addr -> addr.isNotBlank() && result?.first != addr }
        .cacheState(initialValue = false)


    // --- Amount ---
    val amount: StateFlow<String> field = MutableStateFlow("")
    val amountError: StateFlow<AmountError?> field = MutableStateFlow(null)

    // Whether the amount field is entered in fiat (true) or in the token (false).
    val inputInFiat: StateFlow<Boolean> field = MutableStateFlow(false)

    /** Entered amount as a token unit, kept even when it exceeds the balance so the preview stays in sync. */
    val enteredAmount: StateFlow<BaseUnit?> = combine(amount, selectedAccount, inputInFiat)
        { input, account, inFiat ->
            if (input.isBlank() || account == null) {
                null
            } else {
                Triple(input, account, inFiat)
            }
        }
        .onEach { data ->
            if (data == null) {
                amountError.emit(null)
            }
        }
        .debounce(200L)
        .mapLatestCatching(
            onError = { amountError.emit(AmountError.Invalid) },
        ) { data ->
            data?.let { (input, account, inFiat) ->
                val parsed = AmountInputConverter(account, FORMAT_DECIMALS).tokenUnit(input, inFiat)
                if (parsed == null) {
                    amountError.emit(null)
                }
                parsed
            }
        }
        .cacheState()

    val validatedAmount: StateFlow<BaseUnit?> = combine(enteredAmount, selectedAccount)
        { parsed, account ->
            when {
                parsed == null || account == null -> {
                    null
                }
                parsed > account.unitBalance -> {
                    amountError.emit(AmountError.InsufficientBalance)
                    null
                }
                else -> {
                    amountError.emit(null)
                    parsed
                }
            }
        }
        .cacheState()

    // --- Comment ---
    val comment: StateFlow<String> field = MutableStateFlow(data.presetComment ?: "")
    private val normalizedComment: StateFlow<String?> = comment
        .map { it.trim().ifBlank { null } }
        .cacheState()

    // --- Max flag ---
    private val isMaxFlow = MutableStateFlow(false)

    // --- Derived ---
    val continueEnabled: StateFlow<Boolean> = combine(addressStatus, validatedAmount, amountError, addressLoading, normalizedComment)
        { status, amount, error, loading, memo ->
            status is AddressStatus.Valid && amount != null && error == null && !loading &&
                !(status.memoRequired && memo == null)
        }
        .cacheState(initialValue = false)

    private val analytics = WithdrawAnalytics(data.analyticsFrom)

    private val confirmInitiator = when (data.analyticsFrom) {
        WithdrawFlowFrom.DeepLink -> ConfirmInitiator.DeepLink
        WithdrawFlowFrom.QrCode -> ConfirmInitiator.QrCode
        else -> ConfirmInitiator.User
    }

    // --- Init ---
    init {
        loadAccounts(assetId = data.initAssetId, presetAmount = data.presetAmount)
    }

    private fun loadAccounts(assetId: String, presetAmount: String?) {
        bgScope.launch {
            val wallet = oldAccount.getSelectedWalletId()?.let { accountRepo.getWallet(it) }
            val account = wallet?.let {
                runCatching { accountRepo.findAccount(it.id, assetId) }.getOrNull()
            }
            if (wallet == null || account == null) {
                accountNotFound.emit(true)
                return@launch
            }
            selectedWallet.emit(wallet)
            selectedAccount.emit(account)
            analytics.viewInsertAmount(sellAsset = account.asset.id, symbol = account.asset.symbol)

            val rawAmount = presetAmount ?: return@launch
            val preset = runCatching { account.asset.value.decimals.toDisplayUnit(rawAmount) }.getOrNull()
            if (preset == null || !preset.isPositive) {
                return@launch
            }
            val text = preset.fmt()
            amount.tryEmit(text)
            relay.emit(SendEvent.UpdateAmount(text))
        }
    }

    private suspend fun validateLocally(account: AccountWithDetails, addr: String): AddressCandidate {
        return runCatching {
            val asset = account.asset.value
            when {
                asset.chain is Chain.Ton ->
                    if (asset.isValidRecipientAddress(addr) || addr.isWeb3DomainName()) {
                        AddressCandidate.Resolve(addr)
                    } else {
                        AddressCandidate.Final(addr, AddressStatus.Invalid)
                    }
                asset.isValidRecipientAddress(addr) -> AddressCandidate.Final(addr, AddressStatus.Valid(to = addr))
                else -> AddressCandidate.Final(addr, AddressStatus.Invalid)
            }
        }.getOrElse {
            verifyError(it)
            AddressCandidate.Final(addr, AddressStatus.Invalid)
        }
    }

    private suspend fun resolveTonAccount(addr: String): AddressStatus {
        return runCatching {
            val isAddressInput = addr.isValidTonAddress()
            val resolved = api.resolveAccount(addr, TonNetwork.MAINNET)
            when {
                resolved == null -> AddressStatus.Invalid
                resolved.isScam == true -> AddressStatus.Scam
                isAddressInput -> AddressStatus.Valid(
                    to = addr,
                    memoRequired = resolved.memoRequired == true,
                )
                else -> AddressStatus.Valid(
                    to = resolved.address.toUserFriendly(
                        wallet = false,
                        testnet = false,
                        bounceable = resolved.status == AccountStatus.active && !resolved.isWallet,
                    ),
                    domain = addr.trim().lowercase(),
                    memoRequired = resolved.memoRequired == true,
                )
            }
        }.getOrElse {
            verifyError(it)
            AddressStatus.Invalid
        }
    }

    // --- Public methods ---
    fun setAddress(value: String) {
        if (isAddressLocked) {
            return
        }

        address.tryEmit(value)
    }

    fun setAmount(value: String) {
        isMaxFlow.tryEmit(false)
        amount.tryEmit(value)
    }

    fun setComment(value: String) {
        comment.tryEmit(value)
    }

    fun setMax() {
        val account = selectedAccount.value ?: return
        val converter = AmountInputConverter(account, FORMAT_DECIMALS)

        var inFiat = inputInFiat.value
        var formatted = converter.maxText(account.unitBalance, inFiat) ?: return

        if (inFiat && converter.tokenUnit(formatted, true)?.isPositive != true) {
            inFiat = false
            formatted = converter.maxText(account.unitBalance, false) ?: return
        }

        inputInFiat.tryEmit(inFiat)
        amount.tryEmit(formatted)
        isMaxFlow.tryEmit(true)

        relay.emit(SendEvent.UpdateAmount(formatted))
    }

    /** Toggle the amount input between the token and its fiat equivalent. */
    fun swap() {
        val account = selectedAccount.value ?: return
        val converter = AmountInputConverter(account, FORMAT_DECIMALS)
        if (!converter.hasRate) {
            return
        }

        val toFiat = !inputInFiat.value
        val converted = converter.swapText(amount.value, toFiat)

        inputInFiat.tryEmit(toFiat)
        isMaxFlow.tryEmit(false)
        amount.tryEmit(converted)

        relay.emit(SendEvent.UpdateAmount(converted))
    }

    fun onContinue() {
        if (!continueEnabled.value) {
            return
        }

        bgScope.launch {
            // Derived flows may lag a just-changed input; reject a stale snapshot.
            val (validatedAddr, resolved) = addressValidation.value ?: return@launch
            if (validatedAddr != address.value) {
                return@launch
            }
            val status = resolved as? AddressStatus.Valid ?: return@launch
            val memo = normalizedComment.value
            if (status.memoRequired && memo == null) {
                return@launch
            }

            val wallet = selectedWallet.value ?: return@launch
            val amt = validatedAmount.value ?: return@launch
            val account = selectedAccount.value ?: return@launch

            val request = ConfirmRequest(
                data = CommonTransactionData(
                    assetId = account.asset.id,
                    walletId = wallet.id,
                ),
                type = ConfirmType.Transfer(
                    amount = amt.value,
                    to = status.to,
                    domain = status.domain,
                    isMax = isMaxFlow.value,
                    meta = memo,
                ),
                initiator = confirmInitiator,
            )

            val txInfo = SendTxInfo(
                assetId = account.asset.id,
                symbol = account.asset.symbol,
                amount = amt.toDisplayUnit().fmt().toDoubleOrNull() ?: 0.0,
            )
            analytics.clickInsertAmountContinue(
                sellAsset = txInfo.assetId,
                symbol = txInfo.symbol,
                amount = txInfo.amount,
            )

            relay.emit(SendEvent.Continue(request, txInfo))
        }
    }
}

private fun Asset.isValidRecipientAddress(address: String): Boolean {
    val addressNetworkMode = address.tonNetworkModeOrNull()
    if (addressNetworkMode != null && addressNetworkMode != chain.network.mode) {
        return false
    }
    return Address.isValid(address, coin)
}

private fun String.tonNetworkModeOrNull(): Network.Mode? {
    return TonAddressTags.of(this).isTestnet?.let { testnet ->
        if (testnet) {
            Network.Mode.Testnet
        } else {
            Network.Mode.Mainnet
        }
    }
}
