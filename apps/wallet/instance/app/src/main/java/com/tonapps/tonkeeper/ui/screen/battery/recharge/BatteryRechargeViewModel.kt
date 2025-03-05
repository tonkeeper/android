package com.tonapps.tonkeeper.ui.screen.battery.recharge

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.TonTransferHelper
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.blockchain.ton.extensions.toAccountId
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.filterList
import com.tonapps.extensions.state
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.core.entities.TransferEntity
import com.tonapps.tonkeeper.extensions.toGrams
import com.tonapps.tonkeeper.koin.installId
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.battery.recharge.entity.BatteryRechargeEvent
import com.tonapps.tonkeeper.ui.screen.battery.recharge.entity.RechargePackEntity
import com.tonapps.tonkeeper.ui.screen.battery.recharge.entity.RechargePackType
import com.tonapps.tonkeeper.ui.screen.battery.recharge.list.Item
import com.tonapps.tonkeeper.ui.screen.battery.refill.entity.PromoState
import com.tonapps.tonkeeper.ui.screen.send.main.state.SendDestination
import com.tonapps.tonkeeper.ui.screen.send.transaction.SendTransactionScreen
import com.tonapps.tonkeeperx.BuildConfig
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.battery.BatteryMapper
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.battery.entity.BatteryBalanceEntity
import com.tonapps.wallet.data.battery.entity.BatteryConfigEntity
import com.tonapps.wallet.data.battery.entity.RechargeMethodEntity
import com.tonapps.wallet.data.battery.entity.RechargeMethodType
import com.tonapps.wallet.data.core.entity.RawMessageEntity
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import com.tonapps.wallet.data.rates.RatesRepository
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.block.AddrStd
import uikit.extensions.collectFlow
import java.math.BigDecimal

class BatteryRechargeViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val args: RechargeArgs,
    private val accountRepository: AccountRepository,
    private val batteryRepository: BatteryRepository,
    private val tokenRepository: TokenRepository,
    private val settingsRepository: SettingsRepository,
    private val ratesRepository: RatesRepository,
    private val api: API,
) : BaseWalletVM(app) {

    private val _tokenFlow = MutableStateFlow<AccountTokenEntity?>(null)
    val tokenFlow = _tokenFlow.asStateFlow().filterNotNull()

    private val promoStateFlow = MutableStateFlow<PromoState>(PromoState.Default)

    private val _amountFlow = MutableStateFlow(0.0)
    private val amountFlow = combine(_amountFlow, tokenFlow) { amount, token -> Coins.of(amount, token.decimals) }

    private val _addressFlow = MutableStateFlow("")

    @OptIn(FlowPreview::class)
    private val addressDebounceFlow = _addressFlow.debounce { if (it.isEmpty()) 0 else 600 }

    private val _destinationLoadingFlow = MutableStateFlow(false)

    private val destinationFlow = addressDebounceFlow.map { address ->
        if (address.isEmpty()) {
            SendDestination.Empty
        } else {
            _destinationLoadingFlow.tryEmit(true)
            val destination = getDestinationAccount(address, wallet.testnet)
            _destinationLoadingFlow.tryEmit(false)
            destination
        }
    }.flowOn(Dispatchers.IO).state(viewModelScope)

    private val _eventFlow = MutableEffectFlow<BatteryRechargeEvent>()
    val eventFlow = _eventFlow.asSharedFlow().filterNotNull()

    private val _selectedPackTypeFlow = MutableStateFlow<RechargePackType?>(null)
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
        val ton = tokenRepository.get(settingsRepository.currency, wallet.accountId, wallet.testnet)
            ?.find { it.isTon } ?: return@combine emptyList()
        val hasEnoughTonBalance = ton.balance.value >= Coins.of(0.1)
        val hasBatteryBalance = batteryBalance.balance > Coins.ZERO
        val rechargeMethod = getRechargeMethod(wallet, token)
        val shouldMinusReservedAmount = batteryBalance.reservedBalance.value == BigDecimal.ZERO || args.isGift

        val batteryReservedAmount = rechargeMethod.fromTon(api.config.batteryReservedAmount)

        if (args.isGift) {
            val addressState = if (destinationLoading) {
                Item.Address.State.Loading
            } else if (destination is SendDestination.NotFound) {
                Item.Address.State.Error
            } else if (destination is SendDestination.Account) {
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

        val packs = getPacks(
            rechargeMethod,
            token,
            hasEnoughTonBalance || hasBatteryBalance,
            shouldMinusReservedAmount
        )

        uiItems.addAll(uiItemsPacks(packs, selectedPackType, customAmount))

        if (BuildConfig.DEBUG || !api.config.batteryPromoDisable) {
            uiItems.add(Item.Space)
            uiItems.add(Item.Promo(promoState))
        }

        val remainingBalance = token.balance.value - amount
        val minAmount: Coins = when {
            !hasBatteryBalance && !hasEnoughTonBalance && rechargeMethod.minBootstrapValue != null -> {
                Coins.of(rechargeMethod.minBootstrapValue!!)
            }

            shouldMinusReservedAmount -> batteryReservedAmount
            else -> Coins.ZERO
        }
        val isLessThanMin = amount > Coins.ZERO && amount < minAmount

        val calculateFiatFrom = if (shouldMinusReservedAmount) {
            batteryReservedAmount
        } else {
            Coins.ZERO
        }

        if (customAmount) {
            val charges = BatteryMapper.calculateCryptoCharges(
                getRechargeMethod(wallet, token),
                api.config.batteryMeanFees,
                amount.minus(calculateFiatFrom).coerceAtLeast(Coins.ZERO)
            )
            uiItems.add(Item.Space)
            uiItems.add(
                Item.Amount(
                    symbol = token.symbol,
                    decimals = token.decimals,
                    formattedRemaining = CurrencyFormatter.format(
                        currency = token.symbol, value = remainingBalance
                    ),
                    formattedMinAmount = CurrencyFormatter.format(
                        currency = token.symbol, value = minAmount,
                        customScale = token.decimals,
                    ),
                    isInsufficientBalance = remainingBalance.isNegative,
                    isLessThanMin = isLessThanMin,
                    formattedCharges = charges.toString(),
                    value = _amountFlow.value,
                )
            )
        }

        val isValidGiftAddress = if (args.isGift) {
            destination is SendDestination.Account
        } else {
            true
        }

        val isValidAmount = if (customAmount) {
            amount.isPositive && !isLessThanMin && !remainingBalance.isNegative
        } else {
            selectedPackType != null
        }

        uiItems.add(Item.Space)
        uiItems.add(Item.Button(isValidGiftAddress && isValidAmount))

        uiItems.toList()
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val appliedPromo = batteryRepository.getAppliedPromo(wallet.testnet)

            if (appliedPromo.isNullOrBlank()) {
                promoStateFlow.tryEmit(PromoState.Default)
            } else {
                promoStateFlow.tryEmit(PromoState.Applied(appliedPromo))
            }

            val batteryConfig = getBatteryConfig(wallet)
            _supportedTokensFlow.value = getSupportedTokens(wallet, batteryConfig.rechargeMethods)
        }

        if (args.token != null) {
            _tokenFlow.tryEmit(args.token)
        } else {
            collectFlow(supportedTokensFlow.take(1)) { supportedTokens ->
                _tokenFlow.tryEmit(supportedTokens.first())
            }
        }

        combine(
            promoStateFlow,
            tokenFlow.map { it.token.symbol },
            _customAmountFlow,
            _selectedPackTypeFlow
        ) { promoState, tokenSymbol, amount, rechargePackType ->
            val promoCode = (promoState as? PromoState.Applied)?.appliedPromo ?: "null"
            val size = if (amount) "custom" else rechargePackType?.name?.lowercase() ?: "null"

            AnalyticsHelper.simpleTrackEvent("battery_select", settingsRepository.installId, hashMapOf(
                "size" to size,
                "promo" to promoCode,
                "jetton" to tokenSymbol,
                "type" to "crypto"
            ))
        }.launch()
    }

    fun setToken(token: TokenEntity) {
        supportedTokensFlow.take(1).filterList {
            it.address.equalsAddress(token.address)
        }.map { it.first() }.onEach { selectedToken ->
            _tokenFlow.tryEmit(selectedToken)
            _customAmountFlow.tryEmit(false)
            _selectedPackTypeFlow.tryEmit(null)
        }.launchIn(viewModelScope)
    }

    fun updateAddress(address: String) {
        _addressFlow.value = address
    }

    fun updateAmount(amount: Double) {
        _amountFlow.value = amount
    }

    fun setSelectedPack(packType: RechargePackType) {
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
        val rechargeMethod = getRechargeMethod(wallet, token)
        val batteryBalance = getBatteryBalance(wallet)
        val config = getBatteryConfig(wallet)
        val batteryMaxInputAmount = rechargeMethod.fromTon(api.config.batteryMaxInputAmount)

        val amount = _selectedPackTypeFlow.value?.let { packType ->
            rechargeMethod.fromTon(
                RechargePackEntity.getTonAmount(
                    api.config.batteryMeanFees,
                    packType
                )
            )
        } ?: Coins.of(_amountFlow.value, token.decimals)

        if (amount > batteryMaxInputAmount) {
            _eventFlow.tryEmit(
                BatteryRechargeEvent.MaxAmountError(
                    currency = token.symbol,
                    maxAmount = batteryMaxInputAmount
                )
            )
            return@combine
        }

        val fundReceiver = config.fundReceiver ?: return@combine
        val recipientAddress = if (destination is SendDestination.Account) {
            destination.address
        } else null
        val payload = wallet.contract.createBatteryBody(
            recipientAddress,
            appliedPromo = batteryRepository.getAppliedPromo(wallet.testnet)
        )
        val validUntil = accountRepository.getValidUntil(wallet.testnet)
        val network = when (wallet.testnet) {
            true -> TonNetwork.TESTNET
            false -> TonNetwork.MAINNET
        }

        val forceRelayer = when {
            token.isTon -> false
            batteryBalance.balance.value > BigDecimal.ZERO -> true
            rechargeMethod.minBootstrapValue != null -> {
                amount.value >= rechargeMethod.minBootstrapValue!!.toBigDecimal()
            }
            else -> false
        }

        if (token.isTon) {
            val request = SignRequestEntity.Builder()
                .setFrom(wallet.contract.address)
                .setValidUntil(validUntil)
                .addMessage(RawMessageEntity(
                    addressValue = fundReceiver,
                    amount = amount.toLong(),
                    stateInitValue = null,
                    payloadValue = payload.base64()
                ))
                .setNetwork(network)
                .build(Uri.parse("https://battery.tonkeeper.com/"))

            _eventFlow.tryEmit(BatteryRechargeEvent.Sign(request, forceRelayer))
        } else {
            val queryId = TransferEntity.newWalletQueryId()
            val customPayload = if (token.isRequestMinting) {
                api.getJettonCustomPayload(wallet.accountId, wallet.testnet, token.address)
            } else {
                null
            }

            val jettonPayload = TonTransferHelper.jetton(
                coins = amount.toGrams(),
                toAddress = AddrStd.parse(fundReceiver),
                responseAddress = wallet.contract.address,
                queryId = queryId,
                forwardPayload = payload,
                customPayload = customPayload?.customPayload
            )

            val request = SignRequestEntity.Builder()
                .setFrom(wallet.contract.address)
                .setValidUntil(validUntil)
                .addMessage(RawMessageEntity(
                    addressValue = token.balance.walletAddress,
                    amount = Coins.of(0.1).toLong(),
                    stateInitValue = null,
                    payloadValue = jettonPayload.base64()
                ))
                .setNetwork(network)
                .build(Uri.parse("https://battery.tonkeeper.com/"))

            _eventFlow.tryEmit(BatteryRechargeEvent.Sign(request, forceRelayer))
        }
    }.catch {
        _eventFlow.tryEmit(BatteryRechargeEvent.Error)
    }.take(1).flowOn(Dispatchers.IO).launchIn(viewModelScope)

    private fun uiItemsPacks(
        packs: List<RechargePackEntity>,
        selectedPackType: RechargePackType?,
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
        uiItems.add(Item.CustomAmount(position = ListCell.Position.LAST, selected = isCustomAmount))
        return uiItems.toList()
    }

    private suspend fun getBatteryConfig(
        wallet: WalletEntity
    ): BatteryConfigEntity {
        return batteryRepository.getConfig(wallet.testnet)
    }

    private suspend fun getBatteryBalance(
        wallet: WalletEntity
    ): BatteryBalanceEntity {
        val tonProofToken =
            accountRepository.requestTonProofToken(wallet) ?: return BatteryBalanceEntity.Empty
        return batteryRepository.getBalance(
            tonProofToken = tonProofToken, publicKey = wallet.publicKey, testnet = wallet.testnet
        )
    }

    private suspend fun getTokens(wallet: WalletEntity): List<AccountTokenEntity> {
        return tokenRepository.get(
            currency = settingsRepository.currency,
            accountId = wallet.accountId,
            testnet = wallet.testnet
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

    private suspend fun getRechargeMethod(
        wallet: WalletEntity, token: AccountTokenEntity
    ): RechargeMethodEntity {
        val rechargeMethods = getBatteryConfig(wallet).rechargeMethods
        return rechargeMethods.first {
            if (it.type == RechargeMethodType.TON) {
                token.isTon
            } else {
                it.jettonMaster == token.address
            }
        }
    }

    private suspend fun getPacks(
        rechargeMethod: RechargeMethodEntity,
        token: AccountTokenEntity,
        willBePaidManually: Boolean,
        shouldMinusReservedAmount: Boolean
    ): List<RechargePackEntity> {
        val fiatRate = ratesRepository.getRates(settingsRepository.currency, token.address)
            .getRate(token.address)
        val config = api.config

        return arrayOf(
            RechargePackType.LARGE, RechargePackType.MEDIUM, RechargePackType.SMALL
        ).map { type ->
            RechargePackEntity(
                type = type,
                rechargeMethod = rechargeMethod,
                fiatRate = fiatRate,
                token = token,
                config = config,
                shouldMinusReservedAmount = shouldMinusReservedAmount,
                willBePaidManually = willBePaidManually,
                currency = settingsRepository.currency,
            )
        }
    }

    private suspend fun getDestinationAccount(
        address: String, testnet: Boolean
    ) = withContext(Dispatchers.IO) {
        val accountDeferred = async { api.resolveAccount(address, testnet) }
        val publicKeyDeferred = async { api.safeGetPublicKey(address, testnet) }

        val account = accountDeferred.await() ?: return@withContext SendDestination.NotFound
        val publicKey = publicKeyDeferred.await()

        SendDestination.Account(address, publicKey, account)
    }

    fun applyPromo(promo: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (promo.isEmpty()) {
                batteryRepository.setAppliedPromo(wallet.testnet, null)
                promoStateFlow.tryEmit(PromoState.Default)
                return@launch
            }
            promoStateFlow.tryEmit(PromoState.Loading)
            try {
                if (api.batteryVerifyPurchasePromo(wallet.testnet, promo)) {
                    batteryRepository.setAppliedPromo(wallet.testnet, promo)
                    promoStateFlow.tryEmit(PromoState.Applied(promo))
                } else {
                    throw IllegalStateException("promo code is invalid")
                }

            } catch (_: Exception) {
                batteryRepository.setAppliedPromo(wallet.testnet, null)
                promoStateFlow.tryEmit(PromoState.Error)
            }
        }
    }

    fun sign(request: SignRequestEntity, forceRelayer: Boolean) = flow {
        val promoCode = (promoStateFlow.value as? PromoState.Applied)?.appliedPromo ?: "null"
        val tokenSymbol = _tokenFlow.value?.token?.symbol ?: "null"
        AnalyticsHelper.batterySuccess(settingsRepository.installId, "crypto", promoCode, tokenSymbol)
        val boc = SendTransactionScreen.run(context, wallet, request, forceRelayer = forceRelayer)
        emit(boc)
    }.flowOn(Dispatchers.IO)
}