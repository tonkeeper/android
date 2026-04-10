package com.tonapps.tonkeeper.ui.screen.onramp.main.view

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import com.tonapps.log.L
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.setPadding
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.ui.component.coin.CoinEditText
import com.tonapps.tonkeeper.ui.component.token.CurrencyPickerView
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.resolveColor
import com.tonapps.uikit.flag.getFlagDrawable
import com.tonapps.blockchain.model.legacy.BalanceEntity
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.wallet.localization.Localization
import uikit.extensions.focusWithKeyboard
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.hideKeyboard
import uikit.extensions.useAttributes
import uikit.widget.ColumnLayout
import java.math.BigDecimal
import androidx.core.view.isVisible
import com.tonapps.uikit.color.fieldBackgroundColor
import uikit.extensions.viewMoveTo
import uikit.widget.RowLayout

class CurrencyInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : ColumnLayout(context, attrs, defStyle) {

    companion object {
        const val EQUALS_SIGN_PREFIX = "≈ "
    }

    private val offsetMedium = context.getDimensionPixelSize(uikit.R.dimen.offsetMedium)

    private val titleView: AppCompatTextView
    private val valueView: CoinEditText
    private val prefixView: AppCompatTextView
    private val currencyPickerView: CurrencyPickerView
    private val tokenBalanceView: AppCompatTextView
    private val tokenBalanceMaxView: View
    private val currencyEmptyView: View
    private val inputContainerView: RowLayout
    private val placeholderValueView: AppCompatTextView

    private var prefix: String? = null

    var doOnValueChange: ((value: Double, byUser: Boolean) -> Unit)?
        get() = valueView.doOnValueChange
        set(value) { valueView.doOnValueChange = value }

    var doOnTextChange: ((text: String) -> Unit)?
        get() = valueView.doOnTextChange
        set(value) { valueView.doOnTextChange = value }

    var doOnFocusChange: ((hasFocus: Boolean) -> Unit)? = null

    var doOnCurrencyClick: (() -> Unit)? = null

    var doOnEditorAction: ((actionId: Int) -> Boolean)? = null
        set(value) {
            valueView.setOnEditorActionListener { _, actionId, _ ->
                value?.invoke(actionId) == true
            }
        }

    val isFocusActive: Boolean
        get() = valueView.isFocused

    val isEmpty: Boolean
        get() = valueView.getValue() == 0.0

    private var placeholderValue: String? = null

    init {
        inflate(context, R.layout.view_currency_input, this)
        setBackgroundColor(context.fieldBackgroundColor)
        setPadding(offsetMedium)
        setDefault()
        setOnClickListener {
            focusWithKeyboard()
        }

        titleView = findViewById(R.id.input_title)
        tokenBalanceView = findViewById(R.id.input_token_balance)
        tokenBalanceMaxView = findViewById(R.id.input_token_max)
        placeholderValueView = findViewById(R.id.placeholder_value)
        placeholderValueView.setBackgroundColor(Color.TRANSPARENT)

        inputContainerView = findViewById(R.id.input_container)

        currencyEmptyView = findViewById(R.id.input_currency_empty)
        currencyEmptyView.setOnClickListener { doOnCurrencyClick?.invoke() }

        valueView = findViewById(R.id.input_value)
        valueView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) setActive() else setDefault()
            doOnFocusChange?.invoke(hasFocus)
            updatePlaceholder()
            checkPlaceholderValue()
        }

        prefixView = findViewById(R.id.input_prefix)

        valueView.onTextSizeChange = { unit, textSize ->
            prefixView.setTextSize(unit, textSize)
        }

        currencyPickerView = findViewById(R.id.input_currency)
        currencyPickerView.setOnClickListener { doOnCurrencyClick?.invoke() }

        context.useAttributes(attrs, R.styleable.CurrencyInputView) {
            titleView.text = it.getString(R.styleable.CurrencyInputView_android_title)
            if (it.getBoolean(R.styleable.CurrencyInputView_disableInput, false)) {
                valueView.isEnabled = false
            }

            applyGravity(it.getInt(R.styleable.CurrencyInputView_android_gravity, Gravity.RIGHT))


            val valueScale = it.getInt(R.styleable.CurrencyInputView_valueScale, 0)
            if (valueScale > 0) {
                valueView.valueScale = valueScale
            }
        }
    }

    private fun updatePlaceholder() {
        if (!isEmpty || placeholderValue.isNullOrBlank() || isFocusActive) {
            placeholderValueView.visibility = GONE
            valueView.hint = "0"
        } else if (!isFocusActive) {
            placeholderValueView.visibility = VISIBLE
            placeholderValueView.text = EQUALS_SIGN_PREFIX + placeholderValue
            valueView.hint = ""
        }

        checkPrefix()
    }

    private fun checkPlaceholderValue() {
        if (isEmpty) {
            valueView.setValue(placeholderValue)
        }
    }

    fun setPlaceholder(value: String?) {
        placeholderValue = if (value.isNullOrBlank() || value == "0" || value == "0.0") {
            null
        } else {
            value
        }
        updatePlaceholder()
    }

    private fun applyGravity(gravity: Int) {
        if (gravity == Gravity.LEFT) {
            inputContainerView.viewMoveTo(currencyPickerView, 0)
            inputContainerView.viewMoveTo(currencyEmptyView, 1)
            valueView.gravity = Gravity.RIGHT
        }
    }

    fun setInsufficientBalance() {
        tokenBalanceView.visibility = View.VISIBLE
        tokenBalanceMaxView.visibility = View.GONE
        tokenBalanceView.setTextColor(context.resolveColor(com.tonapps.ui.uikit.color.R.attr.accentRedColor))
        tokenBalanceView.setText(Localization.insufficient_balance)
    }

    fun setTokenBalance(
        tokenBalance: BalanceEntity?,
        remainingFormat: CharSequence?,
        symbol: Boolean = true
    ) {
        if (tokenBalance == null) {
            tokenBalanceView.visibility = View.GONE
            tokenBalanceMaxView.visibility = View.GONE
        } else if (remainingFormat != null) {
            tokenBalanceView.text = context.getString(Localization.remaining_balance, remainingFormat)
            showTokenBalance(tokenBalance.value)
        } else {
            val format = CurrencyFormatter.format(if (symbol) tokenBalance.token.symbol else "", tokenBalance.value)
            tokenBalanceView.text = context.getString(Localization.balance_prefix, format)
            showTokenBalance(tokenBalance.value)
        }
    }

    private fun showTokenBalance(value: Coins) {
        tokenBalanceView.setTextColor(context.resolveColor(com.tonapps.ui.uikit.color.R.attr.textSecondaryColor))
        tokenBalanceView.visibility = View.VISIBLE
        tokenBalanceMaxView.visibility = View.VISIBLE
        tokenBalanceMaxView.setOnClickListener {
            setValue(value.value, true)
        }
    }

    fun setPrefix(value: String?) {
        prefix = value
        updatePlaceholder()
    }

    private fun checkPrefix() {
        if (placeholderValueView.isVisible || prefix.isNullOrBlank() || valueView.getValue() == 0.0 || valueView.isFocused) {
            prefixView.visibility = View.GONE
        } else {
            prefixView.visibility = View.VISIBLE
            prefixView.text = prefix
        }
    }

    fun setValueScale(scale: Int) {
        valueView.valueScale = scale
    }

    fun setHint(hint: String) {
        valueView.hint = hint
    }

    fun setDecimals(decimals: Int) {
        valueView.setDecimals(decimals)
    }

    fun setValue(value: BigDecimal, notifyByUser: Boolean = false) {
        valueView.setValue(value, notifyByUser)
        updatePlaceholder()
    }

    fun setValue(coins: Coins = Coins.ZERO, notifyByUser: Boolean = false) {
        setValue(coins.value, notifyByUser)
    }

    fun getTextValue() = valueView.text

    fun getValue(): Double {
        return valueView.getValue()
    }

    fun setValue(value: Double) {
        valueView.setValue(value)
        updatePlaceholder()
    }

    fun setToken(token: TokenEntity) {
        setCurrency(CurrencyPickerView.Value(token))
    }

    fun setCurrency(currency: WalletCurrency) {
        val value = CurrencyPickerView.Value(currency)
        setCurrency(value)
    }

    fun isTokenEmpty() = currencyEmptyView.isVisible

    fun setEmptyCurrency() {
        currencyEmptyView.visibility = View.VISIBLE
        currencyPickerView.visibility = View.GONE
    }

    fun setCurrency(value: CurrencyPickerView.Value) {
        currencyPickerView.value = value
        currencyEmptyView.visibility = View.GONE
        currencyPickerView.visibility = View.VISIBLE
    }

    private fun setActive() {
        prefixView.visibility = View.GONE
        setBackgroundResource(uikit.R.drawable.bg_field_focused)
    }

    private fun setDefault() {
        setBackgroundResource(uikit.R.drawable.bg_field)
    }

    fun focusWithKeyboard() {
        valueView.focusWithKeyboard()
    }

    fun hideKeyboard() {
        valueView.hideKeyboard()
    }
}