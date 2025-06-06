package com.tonapps.tonkeeper.ui.component.token

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.tonkeeper.ui.screen.token.picker.TokenPickerScreen
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import uikit.extensions.hideKeyboard
import uikit.navigation.Navigation

class TokenPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : CurrencyPickerView(context, attrs, defStyle) {

    private val navigation: Navigation?
        get() = Navigation.from(context)

    private val pickerRequestKey = "@android:view:$id"
    private val pickerCallback: (Bundle) -> Unit = { bundle ->
        bundle.getParcelableCompat<TokenEntity>(TokenPickerScreen.TOKEN)?.let {
            value = Value(it)
        }
    }

    var token: TokenEntity = TokenEntity.TON
        set(value) {
            field = value
            applyToken(value)
        }

    private fun applyToken(token: TokenEntity) {
        this.value = Value(token)
    }

    fun setWallet(wallet: WalletEntity) {
        setOnClickListener { openPicker(wallet) }
    }

    private fun openPicker(wallet: WalletEntity) {
        navigation?.add(TokenPickerScreen.newInstance(wallet, pickerRequestKey, token))
        context.hideKeyboard()
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