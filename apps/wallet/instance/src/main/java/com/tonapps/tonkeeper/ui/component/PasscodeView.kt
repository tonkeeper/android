package com.tonapps.tonkeeper.ui.component

import android.content.Context
import android.util.AttributeSet
import com.tonapps.tonkeeperx.R
import uikit.widget.ColumnLayout
import uikit.widget.NumPadView
import uikit.widget.PinInputView

class PasscodeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ColumnLayout(context, attrs, defStyle) {

    var doOnCheck: (code: String) -> Unit = {}

    private val pinInputView: PinInputView
    private val numPadView: NumPadView

    init {
        inflate(context, R.layout.view_passcode, this)

        pinInputView = findViewById(R.id.pin_input)

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

    fun setSuccess() {
        pinInputView.setSuccess()
    }

    private fun updateValues() {
        numPadView.backspace = pinInputView.count > 0
        if (4 == pinInputView.count) {
            doOnCheck(pinInputView.code)
        }
    }
}