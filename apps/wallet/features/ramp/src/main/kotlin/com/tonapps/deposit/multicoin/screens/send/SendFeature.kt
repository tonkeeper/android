package com.tonapps.deposit.multicoin.screens.send

import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmInitiator
import com.tonapps.blockchain.model.ConfirmOperations
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.core.contract.TonAddressTags
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events.WithdrawFlow.WithdrawFlowFrom
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.blockchain.ton.extensions.isValidTonAddress
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.blockchain.utils.isWeb3DomainName
import com.tonapps.chainkit.chain.ton.utils.TonMessageEncryptor
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.chainkit.core.chain.model.num.BaseUnit
import com.tonapps.core.components.AmountInputConverter
import com.tonapps.core.deeplink.DeepLinkBuilder
import com.tonapps.core.deeplink.DeepLinkRoute
import com.tonapps.deposit.multicoin.analytics.WithdrawAnalytics
import com.tonapps.extensions.toUriOrNull
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.mvi.MviRelay
import com.tonapps.mvi.flow.mapLatestCatching
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import io.tonapi.models.AccountStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FORMAT_DECIMALS = 3

class SendData(
    val initAssetId: String,
    val presetAddress: String? = null,
    val analyticsFrom: WithdrawFlowFrom,
    val utm: UtmParameters = UtmParameters.Empty,
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
        val publicKey: String? = null,
    ) : AddressStatus
    data object Invalid : AddressStatus
    data object Scam : AddressStatus
}

private sealed interface AddressCandidate {
    val addr: String
    data class Final(override val addr: String, val status: AddressStatus) : AddressCandidate
    data class Resolve(override val addr: String, val fetchPublicKey: Boolean) : AddressCandidate
}

sealed interface AmountError {
    data object Invalid : AmountError
    data object InsufficientBalance : AmountError
}

sealed interface SendEvent {
    data class Continue(val request: ConfirmRequest, val txInfo: SendTxInfo) : SendEvent
    data class ShowError(val message: String) : SendEvent
    data object ExpiredLink : SendEvent
    data object UnsupportedLink : SendEvent
    data class UpdateAmount(val displayString: String) : SendEvent
}

@OptIn(FlowPreview::class)
class SendFeature(
    data: SendData,
    private val oldAccount: AccountRepository,
    private val accountRepo: McAccountRepository,
    private val api: API,
    private val settingsRepository: SettingsRepository,
) : AsyncViewModel() {

    private val relay = MviRelay<SendEvent>()
    val events = relay.events

    // --- Wallet & Asset ---
    private val selectedWallet = MutableStateFlow<McWalletEntity?>(null)
    val selectedAccount: StateFlow<AccountWithDetails?> field = MutableStateFlow(null)
    val accountNotFound: StateFlow<Boolean> field = MutableStateFlow(false)

    val balanceText: StateFlow<String?> = selectedAccount
        .map { account ->
            when {
                account == null -> null
                settingsRepository.hiddenBalances -> HIDDEN_BALANCE
                else -> "${account.displayBalance.fmt()} ${account.asset.symbol}"
            }
        }
        .cacheState()

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
                is AddressCandidate.Resolve -> candidate.addr to resolveTonAccount(candidate.addr, candidate.fetchPublicKey)
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

    val commentAvailable: StateFlow<Boolean> = selectedAccount
        .map { account ->
            val chain = account?.asset?.value?.chain
            chain != null && (chain.isTvm || chain is Chain.Tron)
        }
        .cacheState(initialValue = false)

    private val normalizedComment: StateFlow<String?> = combine(comment, commentAvailable) { value, available ->
        value.trim().ifBlank { null }?.takeIf { available }
    }
        .cacheState()

    val commentEncrypted: StateFlow<Boolean> field = MutableStateFlow(false)

    val encryptionAvailable: StateFlow<Boolean> = addressStatus
        .map { (it as? AddressStatus.Valid)?.publicKey != null }
        .cacheState(initialValue = false)

    val commentTooLong: StateFlow<Boolean> = combine(commentEncrypted, encryptionAvailable, normalizedComment)
        { on, available, memo ->
            on && available && memo != null && memo.encodeToByteArray().size > TonMessageEncryptor.MAX_MEMO_BYTES
        }
        .cacheState(initialValue = false)

    // --- Max flag ---
    private val isMaxFlow = MutableStateFlow(false)

    // --- Derived ---
    val continueEnabled: StateFlow<Boolean> = combine(addressStatus, validatedAmount, amountError, addressLoading, normalizedComment)
        { status, amount, error, loading, memo ->
            status is AddressStatus.Valid && amount != null && error == null && !loading &&
                !(status.memoRequired && memo == null)
        }
        .combine(commentTooLong) { enabled, tooLong -> enabled && !tooLong }
        .cacheState(initialValue = false)

    private val utm = data.utm

    private val analytics = WithdrawAnalytics(data.analyticsFrom, utm = utm)

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
            setAmountFromBaseUnits(account, rawAmount)
        }
    }

    private suspend fun validateLocally(account: AccountWithDetails, addr: String): AddressCandidate {
        return runCatching {
            val asset = account.asset.value
            when {
                asset.chain is Chain.Ton ->
                    if (asset.isValidRecipientAddress(addr) || addr.isWeb3DomainName()) {
                        AddressCandidate.Resolve(addr, fetchPublicKey = asset is Asset.Coin)
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

    private suspend fun resolveTonAccount(addr: String, fetchPublicKey: Boolean): AddressStatus {
        return runCatching {
            val isAddressInput = addr.isValidTonAddress()
            val resolved = api.resolveAccount(addr, TonNetwork.MAINNET)
            val canEncrypt = resolved != null && fetchPublicKey && resolved.isScam != true &&
                resolved.memoRequired != true &&
                (resolved.status == AccountStatus.active || resolved.status == AccountStatus.frozen)
            val publicKey = if (canEncrypt) {
                runCatching {
                    withContext(Dispatchers.IO) { api.getPublicKey(resolved.address, TonNetwork.MAINNET) }?.hex()
                }.getOrElse {
                    verifyError(it)
                    null
                }
            } else {
                null
            }
            when {
                resolved == null -> AddressStatus.Invalid
                resolved.isScam == true -> AddressStatus.Scam
                isAddressInput -> AddressStatus.Valid(
                    to = addr,
                    memoRequired = resolved.memoRequired == true,
                    publicKey = publicKey,
                )
                else -> AddressStatus.Valid(
                    to = resolved.address.toUserFriendly(
                        wallet = false,
                        testnet = false,
                        bounceable = resolved.status == AccountStatus.active && !resolved.isWallet,
                    ),
                    domain = addr.trim().lowercase(),
                    memoRequired = resolved.memoRequired == true,
                    publicKey = publicKey,
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

    fun applyScannedValue(value: String) {
        val route = value.toUriOrNull()?.let { DeepLinkRoute.resolve(it) }
        when (route) {
            is DeepLinkRoute.Transfer -> applyTransferLink(route)
            is DeepLinkRoute.EvmTransfer -> applyEvmTransferLink(route)
            is DeepLinkRoute.SolanaTransfer -> applySolanaTransferLink(route)
            else -> setAddress(DeepLinkBuilder.plainAddressOrNull(value) ?: value)
        }
    }

    private fun applyTransferLink(route: DeepLinkRoute.Transfer) {
        if (route.isExpired) {
            relay.emit(SendEvent.ExpiredLink)
            return
        }
        if (route.bin != null || route.initStateBase64 != null) {
            relay.emit(SendEvent.UnsupportedLink)
            return
        }

        setAddress(route.address)
        route.text?.let { setComment(it) }

        val account = selectedAccount.value ?: return
        val amount = route.amount ?: return
        if (route.targetAssetId(account.isTestnet) == account.asset.id) {
            setAmountFromBaseUnits(account, amount.toString())
        }
    }

    private fun applyEvmTransferLink(route: DeepLinkRoute.EvmTransfer) {
        setAddress(route.recipient)

        val account = selectedAccount.value ?: return
        val amount = route.amount ?: return
        val chain = route.chain ?: account.asset.value.chain as? Chain.Evm ?: return
        if (route.assetId(chain) == account.asset.id) {
            setAmountFromBaseUnits(account, amount.toString())
        }
    }

    private fun applySolanaTransferLink(route: DeepLinkRoute.SolanaTransfer) {
        setAddress(route.recipient)

        val account = selectedAccount.value ?: return
        if (route.assetId != account.asset.id) {
            return
        }

        val amount = route.baseUnits(account.asset.value.decimals.value) ?: return
        setAmountFromBaseUnits(account, amount.toString())
    }

    private fun setAmountFromBaseUnits(account: AccountWithDetails, baseUnits: String) {
        val parsed = runCatching { account.asset.value.decimals.toDisplayUnit(baseUnits) }.getOrNull()
        if (parsed == null || !parsed.isPositive) {
            return
        }

        val text = parsed.fmt()
        inputInFiat.tryEmit(false)
        isMaxFlow.tryEmit(false)
        amount.tryEmit(text)
        relay.emit(SendEvent.UpdateAmount(text))
    }

    fun setAmount(value: String) {
        isMaxFlow.tryEmit(false)
        amount.tryEmit(value)
    }

    fun setComment(value: String) {
        comment.tryEmit(value)
    }

    fun setCommentEncrypted(value: Boolean) {
        commentEncrypted.tryEmit(value)
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
            if ((status.memoRequired && memo == null) || commentTooLong.value) {
                return@launch
            }
            val encryptionKey = status.publicKey?.takeIf { commentEncrypted.value && memo != null }

            val wallet = selectedWallet.value ?: return@launch
            val amt = validatedAmount.value ?: return@launch
            val account = selectedAccount.value ?: return@launch

            val request = ConfirmRequest(
                data = CommonTransactionData(
                    assetId = account.asset.id,
                    walletId = wallet.id,
                ),
                type = ConfirmType.Transfer(
                    operation = ConfirmOperations.transfer(
                        chain = account.asset.value.chain,
                        to = status.to,
                        amount = amt.value,
                        comment = memo,
                    ),
                    domain = status.domain,
                    isMax = isMaxFlow.value,
                    commentEncryptionKey = encryptionKey,
                ),
                initiator = confirmInitiator,
                utm = utm,
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

private val AccountWithDetails.isTestnet: Boolean
    get() = asset.value.chain.network.mode == Network.Mode.Testnet

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
