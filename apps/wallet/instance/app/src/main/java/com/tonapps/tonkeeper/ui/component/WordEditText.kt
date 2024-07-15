package com.tonapps.tonkeeper.ui.component

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.Editable
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.tonapps.uikit.color.textSecondaryColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.mnemonic.Mnemonic
import uikit.base.BaseDrawable
import uikit.drawable.InputDrawable
import uikit.extensions.dp
import uikit.extensions.setStartDrawable

class WordEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : AppCompatEditText(context, attrs, defStyle) {

    var doOnTextChanged: ((Editable) -> Unit)? = null

    private val scope: CoroutineScope?
        get() = findViewTreeLifecycleOwner()?.lifecycleScope

    private val inputDrawable = InputDrawable(context)

    init {
        background = inputDrawable
        onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
            inputDrawable.active = hasFocus
            if (hasFocus) {
                inputDrawable.error = false
            } else {
                checkValue()
            }
        }
        setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_DEL || text?.isNotBlank() == true || event.action != KeyEvent.ACTION_UP) {
                return@setOnKeyListener false
            }
            val upFocus = rootView.findViewById<View>(nextFocusUpId) ?: return@setOnKeyListener false
            upFocus.requestFocus()
            return@setOnKeyListener true
        }

        setStartDrawable(IndexDrawable(context, tag))
    }

    private fun checkValue() {
        val value = text.toString()
        if (value.isEmpty()) {
            return
        }
        scope?.launch {
            if (!isValidWord(value)) {
                inputDrawable.error = true
            }
        }
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        if (text is Editable) {
            doOnTextChanged?.invoke(text)
        }
    }

    private class IndexDrawable(context: Context, index: Any): BaseDrawable() {

        private var x: Float = 0f
        private var y: Float = 0f
        private val text = "${index}:"
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = ResourcesCompat.getFont(context, uikit.R.font.montserrat_medium)
            color = context.textSecondaryColor
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, context.resources.displayMetrics)
            textAlign = Paint.Align.CENTER
        }

        override fun draw(canvas: Canvas) {
            canvas.drawText(text, x, y, textPaint)
        }

        override fun onBoundsChange(bounds: Rect) {
            super.onBoundsChange(bounds)
            x = bounds.width() / 2f
            y = bounds.height() / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        }

        override fun getIntrinsicWidth(): Int {
            return 28.dp + (8.dp * 2)
        }

        override fun getIntrinsicHeight(): Int {
            return 24.dp
        }

        override fun getMinimumWidth(): Int {
            return intrinsicWidth
        }

        override fun getMinimumHeight(): Int {
            return intrinsicHeight
        }
    }

    private companion object {

        private suspend fun isValidWord(
            word: String
        ): Boolean = withContext(Dispatchers.IO) {
            Mnemonic.mnemonicWords().contains(word)
        }
    }
}