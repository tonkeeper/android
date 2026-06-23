package com.tonapps.tonkeeper.helper

import com.tonapps.log.L
import com.tonapps.icu.Coins
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.wallet.data.rates.RateData
import com.tonapps.wallet.data.rates.entity.RatesEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class TwinInput(
    private val scope: CoroutineScope
) {

    companion object {
        val Type.opposite get() = when (this) {
            Type.Send -> Type.Receive
            Type.Receive -> Type.Send
        }
    }

    enum class Type {
        Send, Receive
    }

    data class Value(
        val currency: WalletCurrency = WalletCurrency.TON,
        val value: String = "",
    ) {

        val coins: Coins by lazy {
            Coins.of(value, currency.decimals)
        }

        val fiat: Boolean
            get() = currency.fiat

        val address: String
            get() = currency.address

        val symbol: String
            get() = currency.symbol

        val decimals: Int
            get() = currency.decimals
    }

    data class State(
        val send: Value = Value(),
        val receive: Value = Value(),
        val focus: Type = Type.Send
    ) {

        val sendCurrency: WalletCurrency
            get() = send.currency

        val receiveCurrency: WalletCurrency
            get() = receive.currency

        val isEmpty: Boolean
            get() {
                if (send.value.isEmpty()) {
                    return true
                }
                return receive.currency.isTONChain && receive.value.isEmpty()
            }

        val hasTonChain: Boolean
            get() = send.currency.isTONChain || receive.currency.isTONChain

        val hasTronChain: Boolean
            get() = send.currency.isTronChain || receive.currency.isTronChain

        fun getCurrency(type: Type = focus): WalletCurrency {
            return if (type == Type.Send) {
                send.currency
            } else {
                receive.currency
            }
        }

        fun hasCurrency(currency: WalletCurrency) = send.currency == currency || receive.currency == currency

        fun convert(
            rates: RatesEntity,
            fromType: Type = focus
        ): Coins {
            val fromValue = if (fromType == Type.Send) send.coins else receive.coins
            return convert(rates, fromType, fromValue)
        }

        fun convert(
            rates: RatesEntity,
            fromType: Type = focus,
            value: Coins
        ) = if (fromType == Type.Send) {
            rates.convert(
                from = send.currency,
                value = value,
                to = receive.currency,
            )
        } else {
            rates.convert(
                from = receive.currency,
                value = value,
                to = send.currency,
            )
        }
    }

    data class CurrenciesState(
        val send: WalletCurrency = WalletCurrency.TON,
        val receive: WalletCurrency = WalletCurrency.TON
    ) {

        val fiat: WalletCurrency?
            get() {
                if (send.fiat) {
                    return send
                } else if (receive.fiat) {
                    return receive
                }
                return null
            }

        val cryptoTokens: List<WalletCurrency> by lazy {
            listOf(send, receive).filter { !it.fiat }
        }
    }

    private val _stateFlow = MutableStateFlow(State())
    val stateFlow = _stateFlow.asStateFlow()

    val currenciesStateFlow = stateFlow.map {
        CurrenciesState(it.send.currency, it.receive.currency)
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, CurrenciesState())

    val state: State
        get() = _stateFlow.value

    fun createConvertFlow(ratesFlow: Flow<RatesEntity>, forType: Type, reductionFactor: Float = 1f) = combine(
        ratesFlow,
        stateFlow.filter { it.focus != forType }.distinctUntilChanged()
    ) { rates, inputsState ->
        val converted = inputsState.convert(rates)
        if (reductionFactor == 0f || reductionFactor == 1f || !converted.isPositive) {
            converted
        } else {
            converted / reductionFactor
        }
    }.distinctUntilChanged()

    fun getCurrency(forType: Type = state.focus) = if (forType == Type.Send) {
        state.send.currency
    } else {
        state.receive.currency
    }

    fun getValue(forType: Type = state.focus) = if (forType == Type.Send) {
        state.send.value
    } else {
        state.receive.value
    }

    fun switch() {
        _stateFlow.update {
            it.copy(
                send = it.receive,
                receive = it.send,
                focus = it.focus.opposite
            )
        }

        _stateFlow.update {
            it.copy(
                focus = it.focus.opposite
            )
        }
    }

    fun updateValue() {
        updateValue(state.focus, getValue(state.focus.opposite))
    }

    fun updateFocus(type: Type) {
        _stateFlow.update {
            it.copy(focus = type)
        }
    }

    fun updateCurrency(type: Type, currency: WalletCurrency) {
        _stateFlow.update {
            if (type == Type.Send) {
                it.copy(send = it.send.copy(currency = currency))
            } else {
                it.copy(receive = it.receive.copy(currency = currency))
            }
        }
    }

    fun updateValue(type: Type, value: String) {
        _stateFlow.update {
            var normalizedValue = value.trim()
            if (normalizedValue == "0") {
                normalizedValue = ""
            }
            if (type == Type.Send) {
                it.copy(send = it.send.copy(value = normalizedValue))
            } else {
                it.copy(receive = it.receive.copy(value = normalizedValue))
            }
        }
    }

}