package com.tonapps.tonkeeper.ui.screen.battery.recharge

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.bus.core.contract.TonAddressTags
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.equalsRawAddress
import com.tonapps.blockchain.ton.extensions.isValidTonAddress
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeSize
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeType
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.state
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.battery.recharge.asset.formatMinAmount
import com.tonapps.tonkeeper.ui.screen.battery.recharge.entity.BatteryRechargeEvent
import com.tonapps.tonkeeper.ui.screen.battery.recharge.entity.RechargePackEntity
import com.tonapps.tonkeeper.ui.screen.battery.recharge.list.Item
import com.tonapps.tonkeeper.ui.screen.battery.refill.entity.PromoState
import com.tonapps.deposit.screens.send.state.SendDestination
import com.tonapps.tonkeeperx.BuildConfig
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.API
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.battery.BatteryMapper
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.battery.entity.BatteryBalanceEntity
import com.tonapps.wallet.data.battery.entity.BatteryConfigEntity
import com.tonapps.wallet.data.battery.entity.RechargeMethodEntity
import com.tonapps.wallet.data.battery.entity.RechargeMethodType
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal

class BatteryRechargeViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val args: RechargeArgs,
    private val accountRepository: AccountRepository,
    private val batteryRepository: BatteryRepository,
    private val tokenRepository: TokenRepository,
    private val settingsRepository: SettingsRepository,
    private val api: API,
    private val analytics: AnalyticsHelper,
    private val tonRecharge: BatteryTonRecharge,
) : BaseWalletVM(app) {

    private val _tokenFlow = MutableStateFlow<AccountTokenEntity?>(null)
    val tokenFlow = _tokenFlow.asStateFlow().filterNotNull()

    private val promoStateFlow = MutableStateFlow<PromoState>(PromoState.Default)

    private val _amountFlow = MutableStateFlow(0.0)
    private val amountFlow =
        combine(_amountFlow, tokenFlow) { amount, token -> Coins.of(amount, token.decimals) }

    private val _addressFlow = MutableStateFlow("")

    @OptIn(FlowPreview::class)
    private val addressDebounceFlow = _addressFlow.debounce {
        if (it.isEmpty()) {
            0
        } else {
            600
        }
    }

    private val _destinationLoadingFlow = MutableStateFlow(false)

    private val destinationFlow = addressDebounceFlow.map { address ->
        if (address.isEmpty()) {
            SendDestination.Empty
        } else {
            _destinationLoadingFlow.tryEmit(true)
            val destination = getDestinationAccount(address, wallet.network)
            _destinationLoadingFlow.tryEmit(false)
            destination
        }
    }.flowOn(Dispatchers.IO).state(viewModelScope)

    private val _eventFlow = MutableEffectFlow<BatteryRechargeEvent>()
    val eventFlow = _eventFlow.asSharedFlow().filterNotNull()

    private val _selectedPackTypeFlow = MutableStateFlow<String?>(null)
    private val _customAmountFlow = MutableStateFlow(false)

    private val _supportedTokensFlow = MutableStateFlow<List<AccountTokenEntity>?>(null)
    val supportedTokensFlow = _supportedTokensFlow.asStateFlow().filterNotNull()

    private val selectedFlow = combine(
        _selectedPackTypeFlow,
        _customAmountFlow,
    ) { selectedPackType, customAmount ->
        selectedPackType to customAmount
    }

    private val stateFlow = combine(
        tokenFlow,
        promoStateFlow
    ) { token, promoState ->
        Triple(wallet, token, promoState)
    }

    val uiItemsFlow = combine(
        stateFlow,
        amountFlow,
        selectedFlow,
        _destinationLoadingFlow,
        destinationFlow,
    ) { state, amount, selected, destinationLoading, destination ->
        val uiItems = mutableListOf<Item>()

        val wallet = state.first
        val token = state.second
        val promoState = state.third
        val selectedPackType = selected.first
        val customAmount = selected.second

        val batteryBalance = getBatteryBalance(wallet)
        val ton = tokenRepository.get(settingsRepository.currency, wallet.accountId, wallet.network)
            ?.find { it.isTon }
            ?: return@combine emptyList()
        val willBePaidManually = tonRecharge.willBePaidManually(ton, batteryBalance)
        val batteryConfig = getBatteryConfig(wallet)
        val rechargeMethod = tonRecharge.rechargeMethod(batteryConfig, token) ?: return@combine emptyList()
        val shouldMinusReservedAmount =
            batteryBalance.reservedBalance.value == BigDecimal.ZERO || args.isGift

        val batteryReservedAmount = rechargeMethod.fromTon(batteryConfig.reservedAmount)

        if (args.isGift) {
            val addressState = if (destinationLoading) {
                Item.Address.State.Loading
            } else if (destination is SendDestination.NotFound) {
                Item.Address.State.Error
            } else if (destination is SendDestination.TonAccount) {
                Item.Address.State.Success
            } else {
                Item.Address.State.Default
            }

            uiItems.add(
                Item.Address(
                    state = addressState,
                    value = _addressFlow.value
                )
            )
            uiItems.add(Item.Space)
        }

        val packs = tonRecharge.packs(wallet, token, batteryConfig, willBePaidManually)

        uiItems.addAll(uiItemsPacks(packs, selectedPackType, customAmount))

        if (BuildConfig.DEBUG || !api.getConfig(wallet.network).batteryPromoDisable) {
            uiItems.add(Item.Space)
            uiItems.add(Item.Promo(promoState))
        }

        val remainingBalance = token.balance.value - amount
        val minAmount: Coins = when {
            !willBePaidManually && rechargeMethod.minBootstrapValue != null -> {
                Coins.of(rechargeMethod.minBootstrapValue!!)
            }

            shouldMinusReservedAmount -> batteryReservedAmount
            else -> Coins.ZERO
        }
        val isLessThanMin = amount > Coins.ZERO && amount < minAmount

        val customAmountCharges = if (customAmount) {
            BatteryMapper.calculateCryptoCharges(
                rechargeMethod,
                batteryConfig.chargeCost,
                amount
            )
        } else {
            0
        }

        if (customAmount) {
            uiItems.add(Item.Space)

            uiItems.add(
                Item.Amount(
                    symbol = token.symbol,
                    decimals = token.decimals,
                    formattedRemaining = CurrencyFormatter.format(
                        currency = token.symbol, value = remainingBalance
                    ),
                    // Rounded up, in the separators of the "Remaining" this hint takes the place of.
                    formattedMinAmount = formatMinAmount(
                        symbol = CurrencyFormatter.symbol(token.symbol),
                        min = minAmount.value,
                        decimals = token.decimals,
                        options = Formatter.defaultOptions.copy(
                            decimalSeparator = CurrencyFormatter.decimalSeparator,
                            groupingSeparator = CurrencyFormatter.groupingSeparator,
                        ),
                    ),
                    isInsufficientBalance = remainingBalance.isNegative,
                    isLessThanMin = isLessThanMin,
                    formattedCharges = customAmountCharges.toString(),
                    value = _amountFlow.value,
                )
            )
        }

        val isValidGiftAddress = if (args.isGift) {
            destination is SendDestination.TonAccount
        } else {
            true
        }

        val isValidAmount = if (customAmount) {
            customAmountCharges > 0 && !isLessThanMin && !remainingBalance.isNegative
        } else {
            packs.any { it.type == selectedPackType && it.isEnabled }
        }

        uiItems.add(Item.Space)
        uiItems.add(Item.Button(isValidGiftAddress && isValidAmount))

        uiItems.toList()
    }.onStart {
        emit(listOf(Item.Loading))
    }

    init {
        if (args.selectsCustomAmount) {
            onCustomAmountSelect()
        }

        viewModelScope.launch(Dispatchers.IO) {
            val appliedPromo = batteryRepository.getAppliedPromo(wallet.network)

            if (appliedPromo.isNullOrBlank()) {
                promoStateFlow.tryEmit(PromoState.Default)
            } else {
                promoStateFlow.tryEmit(PromoState.Applied(appliedPromo))
            }

            val rechargeMethods = getBatteryConfig(wallet).rechargeMethods
            refreshTokenBalances()
            publishTokens(rechargeMethods)
        }

        @OptIn(FlowPreview::class)
        combine(
            promoStateFlow,
            tokenFlow.map { it.token.symbol },
            _customAmountFlow,
            _selectedPackTypeFlow
        ) { promoState, tokenSymbol, customAmount, rechargePackType ->
            val size = if (customAmount) {
                BatteryNativeSize.Custom
            } else {
                rechargePackType?.batterySize() ?: return@combine null
            }

            SelectParams(
                size = size,
                promo = (promoState as? PromoState.Applied)?.appliedPromo,
                jetton = tokenSymbol
            )
        }
            .filterNotNull() // filter out `null` returns from combine
            .distinctUntilChanged()
            .debounce(300L) // wait 300ms before emitting
            .onEach { params ->
                analytics.eventsTaggedWith(args.utm).batteryNative.batterySelect(
                    from = args.from,
                    type = BatteryNativeType.Crypto,
                    size = params.size,
                    promo = params.promo,
                    jetton = params.jetton
                )
            }
            .launch()

    }

    private data class SelectParams(
        val size: BatteryNativeSize,
        val promo: String?,
        val jetton: String,
    )

    fun setToken(token: TokenEntity) {
        supportedTokensFlow.take(1)
            .onEach { data ->
                val result = data.firstOrNull { it.address.equalsRawAddress(token.address) }
                if (result != null) {
                    _tokenFlow.tryEmit(result)
                    _customAmountFlow.tryEmit(false)
                    _selectedPackTypeFlow.tryEmit(null)
                }
            }
            .launchIn(viewModelScope)
    }

    fun updateAddress(address: String) {
        _addressFlow.value = address
    }

    fun updateAmount(amount: Double) {
        _amountFlow.value = amount
    }

    fun setSelectedPack(packType: String) {
        _selectedPackTypeFlow.tryEmit(packType)
        _customAmountFlow.tryEmit(false)
    }

    fun onCustomAmountSelect() {
        _customAmountFlow.tryEmit(true)
        _selectedPackTypeFlow.tryEmit(null)
    }

    fun onContinue() = combine(
        stateFlow,
        destinationFlow
    ) { (wallet, token), destination ->
        val recipientAddress = if (destination is SendDestination.TonAccount) {
            destination.address
        } else {
            null
        }
        val event = tonRecharge.request(
            wallet = wallet,
            token = token,
            packType = _selectedPackTypeFlow.value,
            customAmount = Coins.of(_amountFlow.value, token.decimals),
            recipient = recipientAddress,
        )
        _eventFlow.tryEmit(event)
    }.catch {
        _eventFlow.tryEmit(BatteryRechargeEvent.Error)
    }.take(1).flowOn(Dispatchers.IO).launchIn(viewModelScope)

    private fun uiItemsPacks(
        packs: List<RechargePackEntity>,
        selectedPackType: String?,
        isCustomAmount: Boolean
    ): List<Item> {
        val uiItems = mutableListOf<Item>()
        for ((index, pack) in packs.withIndex()) {
            val position = ListCell.getPosition(packs.size + 1, index)
            uiItems.add(
                Item.RechargePack(
                    position = position,
                    packType = pack.type,
                    charges = pack.charges,
                    formattedAmount = pack.formattedAmount,
                    formattedFiatAmount = pack.formattedFiatAmount,
                    batteryLevel = pack.batteryLevel,
                    isEnabled = pack.isEnabled,
                    selected = pack.type == selectedPackType,
                    transactions = pack.transactions,
                )
            )
        }
        uiItems.add(
            Item.CustomAmount(
                position = if (packs.isNotEmpty()) {
                    ListCell.Position.LAST
                } else {
                    ListCell.Position.SINGLE
                },
                selected = isCustomAmount
            )
        )
        return uiItems.toList()
    }

    private suspend fun getBatteryConfig(
        wallet: WalletEntity
    ): BatteryConfigEntity {
        return batteryRepository.getConfig(wallet.network)
    }

    private suspend fun getBatteryBalance(
        wallet: WalletEntity
    ): BatteryBalanceEntity {
        return batteryRepository.getBalance(wallet)
    }

    private suspend fun getTokens(wallet: WalletEntity): List<AccountTokenEntity> {
        return tokenRepository.get(
            currency = settingsRepository.currency,
            accountId = wallet.accountId,
            network = wallet.network
        ) ?: emptyList()
    }

    private suspend fun getSupportedTokens(
        wallet: WalletEntity, rechargeMethods: List<RechargeMethodEntity>
    ): List<AccountTokenEntity> {
        val tokens = getTokens(wallet)
        val supportTokenAddress = rechargeMethods.filter { it.supportRecharge }.mapNotNull {
            if (it.type == RechargeMethodType.TON) {
                TokenEntity.TON.address
            } else {
                it.jettonMaster
            }
        }

        return tokens.filter { token ->
            supportTokenAddress.contains(token.address)
        }.sortedBy { it.fiat }.reversed()
    }

    private suspend fun refreshTokenBalances() {
        val tronAddress = if (wallet.hasPrivateKey && !wallet.testnet) {
            accountRepository.getTronAddress(wallet.id)
        } else {
            null
        }

        tokenRepository.get(
            currency = settingsRepository.currency,
            accountId = wallet.accountId,
            network = wallet.network,
            refresh = true,
            tronAddress = tronAddress
        )
    }

    private suspend fun publishTokens(rechargeMethods: List<RechargeMethodEntity>) {
        val tokens = getSupportedTokens(wallet, rechargeMethods)
        _supportedTokensFlow.value = tokens

        val argsToken = args.token
        val token = if (argsToken != null) {
            tokens.firstOrNull { it.address.equalsRawAddress(argsToken.address) } ?: argsToken
        } else {
            tokens.firstOrNull()
        }
        if (token != null) {
            _tokenFlow.tryEmit(token)
        }
    }

    private suspend fun getDestinationAccount(
        userInput: String, network: TonNetwork
    ) = withContext(Dispatchers.IO) {
        val addressTags = TonAddressTags.of(userInput)
        if (addressTags.userFriendly && addressTags.isTestnet != wallet.testnet) {
            return@withContext SendDestination.NotFound
        }

        val accountDeferred = async { api.resolveAccount(userInput, network) }
        val publicKeyDeferred = async { api.safeGetPublicKey(userInput, network) }

        val account = accountDeferred.await() ?: return@withContext SendDestination.NotFound
        val publicKey = publicKeyDeferred.await()

        SendDestination.TonAccount(
            userInput = userInput,
            isUserInputAddress = userInput.isValidTonAddress(),
            publicKey = publicKey,
            account = account,
            testnet = wallet.testnet,
            tonAddressTags = addressTags
        )
    }

    fun applyPromo(promo: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (promo.isEmpty()) {
                batteryRepository.setAppliedPromo(wallet.network, null)
                promoStateFlow.tryEmit(PromoState.Default)
                return@launch
            }
            promoStateFlow.tryEmit(PromoState.Loading)
            try {
                if (api.batteryVerifyPurchasePromo(wallet.network, promo)) {
                    batteryRepository.setAppliedPromo(wallet.network, promo)
                    promoStateFlow.tryEmit(PromoState.Applied(promo))
                } else {
                    throw IllegalStateException("promo code is invalid")
                }

            } catch (_: Exception) {
                batteryRepository.setAppliedPromo(wallet.network, null)
                promoStateFlow.tryEmit(PromoState.Error)
            }
        }
    }

    fun sign(request: SignRequestEntity, forceRelayer: Boolean) = flow {
        val boc = tonRecharge.sign(context, wallet, request, forceRelayer, args.utm, args.isGift)

        val size = if (_customAmountFlow.value) {
            BatteryNativeSize.Custom
        } else {
            _selectedPackTypeFlow.value?.batterySize() ?: BatteryNativeSize.Custom
        }
        analytics.eventsTaggedWith(args.utm).batteryNative.batterySuccess(
            from = args.from,
            type = BatteryNativeType.Crypto,
            size = size,
            promo = (promoStateFlow.value as? PromoState.Applied)?.appliedPromo,
            jetton = _tokenFlow.value?.token?.symbol
        )

        emit(boc)
    }.flowOn(Dispatchers.IO)
}

internal fun String.batterySize(): BatteryNativeSize {
    return BatteryNativeSize.entries.firstOrNull { it.key == this } ?: BatteryNativeSize.Custom
}
