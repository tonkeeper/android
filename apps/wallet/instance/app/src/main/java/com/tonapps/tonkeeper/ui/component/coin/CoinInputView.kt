package com.tonapps.tonkeeper.ui.component.coin

import android.content.Context
import android.graphics.Paint
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.doAfterTextChanged
import com.tonapps.icu.Coins
import com.tonapps.tonkeeper.ui.component.TokenPickerView
import com.tonapps.tonkeeper.ui.component.coin.drawable.SuffixDrawable
import com.tonapps.tonkeeper.ui.component.coin.format.CoinFormattingConfig
import com.tonapps.tonkeeper.ui.component.coin.format.CoinFormattingFilter
import com.tonapps.tonkeeper.ui.component.coin.format.CoinFormattingTextWatcher
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.localization.Localization
import uikit.extensions.dp
import uikit.extensions.focusWithKeyboard
import uikit.extensions.hideKeyboard
import uikit.extensions.replaceAll
import uikit.extensions.setRightDrawable
import uikit.widget.input.BaseInputView
import uikit.widget.input.InputTextView
import java.math.BigDecimal

class CoinInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : BaseInputView(context, attrs, defStyle) {

    var doOnValueChanged: ((Coins) -> Unit)? = null
    var doOnTokenChanged: ((TokenEntity) -> Unit)? = null

    private val suffixDrawable = SuffixDrawable(context, TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f.dp
        typeface = ResourcesCompat.getFont(context, uikit.R.font.montserrat_medium)
        textAlign = Paint.Align.RIGHT
        color = context.textSecondaryColor
    })

    private val editText: InputTextView
    private val clearView: View
    private val tokenPickerView: TokenPickerView

    private var formattingConfig = CoinFormattingConfig(decimals = 0)
        set(value) {
            if (field != value) {
                field = value
                editText.setFormattingTextWatcher(CoinFormattingTextWatcher(value))
                editText.setFormattingInputFilter(CoinFormattingFilter(value))
            }
        }

    private val decimals: Int
        get() = formattingConfig.decimals

    var suffix: String? = suffixDrawable.text
        set(value) {
            field = value
            updateSuffix()
        }

    init {
        inflate(context, R.layout.view_coin_input, this)
        setHint(Localization.amount)

        editText = findViewById(R.id.coin_input)
        editText.setMaxLength(24)
        editText.doAfterTextChanged { onTextChanged(it.toString())  }
        editText.setOnFocusChangeListener { _, hasFocus ->
            active = hasFocus
            updateClearViewVisible()
        }
        editText.setRightDrawable(suffixDrawable)
        editText.compoundDrawablePadding = 36.dp

        clearView = findViewById(R.id.coin_input_clear)
        clearView.setOnClickListener { clear() }

        tokenPickerView = findViewById(R.id.coin_input_token)
        tokenPickerView.doOnTokenChanged = ::onTokenChanged
        onTokenChanged(tokenPickerView.token)
        findViewById<View>(R.id.coin_input_container).setOnClickListener { focusWithKeyboard() }
    }

    private fun updateSuffix() {
        if (suffix.isNullOrBlank() || expanded) {
            suffixDrawable.text = null
        } else {
            suffixDrawable.text = suffix
        }
        invalidate()
    }

    fun setOnDoneActionListener(listener: () -> Unit) {
        editText.setOnDoneActionListener(listener)
    }

    private fun onTextChanged(value: String) {
        onEmptyInput(value.isEmpty())
        post {
            doOnValueChanged?.invoke(getValue())
        }
    }

    fun getValue(): Coins {
        val text = editText.text.toString()
        if (text.isEmpty()) {
            return Coins.ZERO
        }
        return Coins.of(text, decimals)
    }

    fun setValue(value: BigDecimal) {
        if (BigDecimal.ZERO == value) {
            clear()
        } else {
            editText.setText(value.stripTrailingZeros().toPlainString().removeSuffix(".0"))
        }
    }

    fun clear() {
        editText.text = null
    }

    fun setToken(token: TokenEntity): Boolean {
        if (tokenPickerView.token.address == token.address) {
            return false
        }
        tokenPickerView.token = token
        return true
    }

    fun focusWithKeyboard() {
        editText.focusWithKeyboard()
    }

    fun hideKeyboard() {
        editText.hideKeyboard()
    }

    private fun onTokenChanged(token: TokenEntity) {
        clear()
        formattingConfig = CoinFormattingConfig(
            decimals = token.decimals
        )

        doOnTokenChanged?.invoke(token)
    }

    private fun onEmptyInput(empty: Boolean) {
        expanded = empty
        updateClearViewVisible()
        updateSuffix()
    }

    private fun updateClearViewVisible() {
        if (expanded || !editText.isFocused) {
            clearView.visibility = View.GONE
        } else {
            clearView.visibility = View.VISIBLE
        }
    }

    override fun getContentView() = editText

}