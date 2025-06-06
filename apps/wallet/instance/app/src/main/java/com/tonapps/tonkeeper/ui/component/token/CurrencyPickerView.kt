package com.tonapps.tonkeeper.ui.component.token

import android.content.Context
import android.net.Uri
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.util.Log
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.setPadding
import com.tonapps.extensions.isLocal
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.core.currency.WalletCurrency
import com.tonapps.wallet.localization.Localization
import uikit.extensions.circle
import uikit.extensions.dp
import uikit.widget.FrescoView
import uikit.widget.RowLayout

open class CurrencyPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : RowLayout(context, attrs, defStyle) {

    data class Value(
        val id: String,
        val icon: Uri,
        val title: String,
        val subtitle: String?,
        val decimals: Int,
        val token: TokenEntity?,
    ) {

        constructor(token: TokenEntity): this(
            id = token.address,
            icon = token.imageUri,
            title = token.symbol,
            subtitle = if (token.isTrc20) {
                "TRC20"
            } else if (token.isUsdt) {
                "TON"
            } else {
                null
            },
            decimals = token.decimals,
            token = token
        )

        constructor(currency: WalletCurrency): this(
            id = currency.chain.name,
            icon = currency.iconUri ?: Uri.EMPTY,
            title = currency.code,
            subtitle = currency.chainName,
            decimals = currency.decimals,
            token = null
        )
    }

    private val iconView: FrescoView
    private val titleView: AppCompatTextView

    open var value = Value(TokenEntity.TON)
        set(value) {
            if (field != value) {
                field = value
                applyValue(value)
            }
        }

    var doOnValueChanged: ((Value) -> Unit)? = null

    init {
        super.setBackgroundResource(uikit.R.drawable.bg_button_tertiary)
        setPadding(8.dp)
        inflate(context, R.layout.view_currency_picker, this)
        iconView = findViewById(R.id.token_icon)
        iconView.setCircular()
        titleView = findViewById(R.id.token_title)
        applyValue(value)
    }

    private fun applyValue(value: Value) {
        iconView.setImageURI(value.icon, null)
        titleView.text = if (value.subtitle.isNullOrBlank()) {
            value.title
        } else {
            val builder = SpannableStringBuilder(value.title)
            builder.append(" ")
            val start = builder.length
            builder.append(value.subtitle)
            builder.setSpan(
                ForegroundColorSpan(context.textSecondaryColor),
                start,
                builder.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder
        }
        doOnValueChanged?.invoke(value)
    }
}