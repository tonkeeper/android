package com.tonapps.wallet.data.passcode.ui

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.tonapps.apps.wallet.data.passcode.R
import com.tonapps.wallet.data.passcode.PasscodeReason
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.suspendCancellableCoroutine
import uikit.widget.ColumnLayout
import uikit.widget.LoaderView
import uikit.widget.NumPadView
import uikit.widget.PinInputView
import kotlin.coroutines.resume

class PasscodeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ColumnLayout(context, attrs, defStyle) {

    var doOnCheck: (code: String) -> Unit = {}

    private val pinTitleView: AppCompatTextView
    private val pinReasonView: AppCompatTextView
    private val pinInputView: PinInputView
    private val pinLoaderView: LoaderView
    private var loading = false
    private var titleBeforeLoading: CharSequence? = null
    private var reasonShownBeforeLoading = false
    private val numPadView: NumPadView

    init {
        inflate(context, R.layout.view_passcode, this)

        pinTitleView = findViewById(R.id.pin_title)
        pinReasonView = findViewById(R.id.pin_reason)

        pinInputView = findViewById(R.id.pin_input)
        pinLoaderView = findViewById(R.id.pin_loader)

        numPadView = findViewById(R.id.num_pad)
        numPadView.doOnBackspaceClick = {
            pinInputView.removeLastNumber(true)
            updateValues()
        }

        numPadView.doOnNumberClick = {
            pinInputView.appendNumber(it)
            updateValues()
        }
    }

    fun setTitle(title: CharSequence) {
        pinTitleView.text = title
    }

    fun setTitle(resId: Int){
        setTitle(context.getString(resId))
    }

    /**
     * The passcode was accepted and the app is busy with it: a loader takes the place of the dots, the
     * title says so and the numpad is off. The default title comes back with the dots.
     */
    fun setLoading(loading: Boolean) {
        if (loading == this.loading) {
            return
        }
        this.loading = loading
        if (loading) {
            titleBeforeLoading = pinTitleView.text
            reasonShownBeforeLoading = pinReasonView.isVisible
            setTitle(Localization.passcode_preparing_wallet)
            pinReasonView.isVisible = false
        } else {
            titleBeforeLoading?.let(::setTitle)
            pinReasonView.isVisible = reasonShownBeforeLoading
        }
        pinInputView.isInvisible = loading
        pinLoaderView.isVisible = loading
        isEnabled = !loading
    }

    fun setReason(reason: PasscodeReason?) {
        pinReasonView.text = reason?.let { context.getString(it.text) }
        pinReasonView.isVisible = reason != null
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        numPadView.isEnabled = enabled
        pinInputView.isEnabled = enabled
    }

    fun setError() {
        numPadView.isEnabled = true
        numPadView.backspace = false
        pinInputView.setError()
    }

    suspend fun setSuccess() = suspendCancellableCoroutine { continuation ->
        pinInputView.setSuccess {
            if (continuation.isActive) {
                continuation.resume(Unit)
            }
        }
    }

    fun clear() {
        pinInputView.clear()
    }

    private fun updateValues() {
        numPadView.backspace = pinInputView.count > 0
        if (4 == pinInputView.count) {
            doOnCheck(pinInputView.code)
        }
    }
}