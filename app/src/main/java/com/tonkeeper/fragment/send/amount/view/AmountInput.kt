package com.tonkeeper.fragment.send.amount.view

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.R
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.doOnLayout
import uikit.extensions.dp
import uikit.extensions.sp

class AmountInput @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = R.attr.editTextStyle,
) : AppCompatEditText(context, attrs, defStyle), TextWatcher {

    private companion object {
        private const val maxTextSize = 40f
    }

    init {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, maxTextSize)
        addTextChangedListener(this)
    }

    private fun checkTextSize(s: CharSequence) {
        val maxWidth = measuredWidth
        val textWidth = paint.measureText(s, 0, s.length)
        if (textWidth >= maxWidth) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize - 2)
        }
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

    }

    override fun onTextChanged(
        text: CharSequence,
        start: Int,
        lengthBefore: Int,
        lengthAfter: Int
    ) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
    }

    override fun afterTextChanged(s: Editable?) {
        // s?.let { checkTextSize(it) }
    }
}