package com.tonapps.tonkeeper.ui.component

import android.content.Context
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.util.Log
import android.widget.EditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.setPadding
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.tonkeeper.ui.screen.token.picker.TokenPickerScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.localization.Localization
import uikit.extensions.badgeDefault
import uikit.extensions.dp
import uikit.extensions.getCurrentFocus
import uikit.extensions.hideKeyboard
import uikit.extensions.withDefaultBadge
import uikit.navigation.Navigation
import uikit.widget.FrescoView
import uikit.widget.RowLayout

class TokenPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : RowLayout(context, attrs, defStyle) {

    private val navigation: Navigation?
        get() = Navigation.from(context)

    private val iconView: FrescoView
    private val titleView: AppCompatTextView
    private val pickerRequestKey = "@android:view:$id"
    private val pickerCallback: (Bundle) -> Unit = { bundle ->
        bundle.getParcelableCompat<TokenEntity>(TokenPickerScreen.TOKEN)?.let {
            token = it
        }
    }

    var token: TokenEntity = TokenEntity.TON
        set(value) {
            if (field != value) {
                field = value
                applyToken(value)
            }
        }

    var doOnTokenChanged: ((TokenEntity) -> Unit)? = null

    init {
        setBackgroundResource(uikit.R.drawable.bg_button_tertiary)
        setPadding(8.dp)
        inflate(context, R.layout.view_token_picker, this)
        iconView = findViewById(R.id.token_icon)
        titleView = findViewById(R.id.token_title)
        applyToken(token)
    }

    fun setWallet(wallet: WalletEntity) {
        setOnClickListener { openPicker(wallet) }
    }

    private fun openPicker(wallet: WalletEntity) {
        navigation?.add(TokenPickerScreen.newInstance(wallet, pickerRequestKey, token))
        context.hideKeyboard()
    }

    private fun applyToken(value: TokenEntity) {
        iconView.setImageURI(value.imageUri, null)
        titleView.text = if (value.isTrc20) {
            val builder = SpannableStringBuilder(value.symbol)
            builder.append(" ")
            val start = builder.length
            builder.append(context.getString(Localization.trc20))

            builder.setSpan(
                ForegroundColorSpan(context.textSecondaryColor),
                start,
                builder.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder
        } else {
            value.symbol
        }
        doOnTokenChanged?.invoke(value)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        navigation?.setFragmentResultListener(pickerRequestKey, pickerCallback)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        navigation?.setFragmentResultListener(pickerRequestKey) { }
    }
}