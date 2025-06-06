package com.tonapps.tonkeeper.ui.screen.onramp.main.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.setPadding
import com.facebook.common.util.UriUtil
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.ui.component.coin.CoinEditText
import com.tonapps.tonkeeper.ui.component.token.CurrencyPickerView
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.resolveColor
import com.tonapps.uikit.flag.getFlagDrawable
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.core.currency.WalletCurrency
import com.tonapps.wallet.localization.Localization
import uikit.extensions.focusWithKeyboard
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.hideKeyboard
import uikit.extensions.useAttributes
import uikit.widget.ColumnLayout
import java.math.BigDecimal

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

    private var prefix: String? = null

    var doOnValueChange: ((value: Double, byUser: Boolean) -> Unit)?
        get() = valueView.doOnValueChange
        set(value) { valueView.doOnValueChange = value }

    var doOnCurrencyClick: (() -> Unit)? = null

    val isFocusActive: Boolean
        get() = valueView.isFocused

    val isEmpty: Boolean
        get() = valueView.getValue() == 0.0

    init {
        inflate(context, R.layout.view_currency_input, this)
        setPadding(offsetMedium)
        setDefault()

        titleView = findViewById(R.id.input_title)
        tokenBalanceView = findViewById(R.id.input_token_balance)
        tokenBalanceMaxView = findViewById(R.id.input_token_max)

        valueView = findViewById(R.id.input_value)
        valueView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) setActive() else setDefault()
        }

        prefixView = findViewById(R.id.input_prefix)

        currencyPickerView = findViewById(R.id.input_currency)
        currencyPickerView.setOnClickListener { doOnCurrencyClick?.invoke() }

        context.useAttributes(attrs, R.styleable.CurrencyInputView) {
            titleView.text = it.getString(R.styleable.CurrencyInputView_android_title)
        }
    }

    fun setInsufficientBalance() {
        tokenBalanceView.visibility = View.VISIBLE
        tokenBalanceMaxView.visibility = View.GONE
        tokenBalanceView.setTextColor(context.resolveColor(com.tonapps.uikit.color.R.attr.accentRedColor))
        tokenBalanceView.setText(Localization.insufficient_balance)
    }

    fun setTokenBalance(tokenBalance: BalanceEntity?, remainingFormat: CharSequence?) {
        if (tokenBalance == null) {
            tokenBalanceView.visibility = View.GONE
            tokenBalanceMaxView.visibility = View.GONE
        } else if (remainingFormat != null) {
            tokenBalanceView.text = context.getString(Localization.remaining_balance, remainingFormat)
            showTokenBalance(tokenBalance.value)
        } else {
            val format = CurrencyFormatter.format(tokenBalance.token.symbol, tokenBalance.value)
            tokenBalanceView.text = context.getString(Localization.balance_prefix, format)
            showTokenBalance(tokenBalance.value)
        }
    }

    private fun showTokenBalance(value: Coins) {
        tokenBalanceView.setTextColor(context.resolveColor(com.tonapps.uikit.color.R.attr.textSecondaryColor))
        tokenBalanceView.visibility = View.VISIBLE
        tokenBalanceMaxView.visibility = View.VISIBLE
        tokenBalanceMaxView.setOnClickListener {
            setValue(value.value, true)
        }
    }

    fun setPrefix(value: String?) {
        prefix = value
        checkPrefix()
    }

    private fun checkPrefix() {
        if (prefix.isNullOrBlank() || valueView.getValue() == 0.0 || valueView.isFocused) {
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
        checkPrefix()
    }

    fun setValue(coins: Coins = Coins.ZERO, notifyByUser: Boolean = false) {
        setValue(coins.value, notifyByUser)
    }

    fun getValue(): Double {
        return valueView.getValue()
    }

    fun setToken(token: TokenEntity) {
        setCurrency(CurrencyPickerView.Value(token))
    }

    fun setCurrency(currency: WalletCurrency) {
        val value = CurrencyPickerView.Value(currency)
        setCurrency(value)
    }

    fun setCurrency(value: CurrencyPickerView.Value) {
        currencyPickerView.value = value
    }

    private fun setActive() {
        prefixView.visibility = View.GONE
        setBackgroundResource(uikit.R.drawable.bg_content_focused)
    }

    private fun setDefault() {
        setBackgroundResource(uikit.R.drawable.bg_content)
    }

    fun focusWithKeyboard() {
        valueView.focusWithKeyboard()
    }

    fun hideKeyboard() {
        valueView.hideKeyboard()
    }
}