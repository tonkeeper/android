package com.tonkeeper.widget

import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import com.tonkeeper.R
import uikit.extensions.getDimensionPixelSize

class NumPadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayoutCompat(context, attrs, defStyle), View.OnClickListener {

    private companion object {
        const val EMPTY_CELL = -1
        const val BACKSPACE_CELL = -2
    }

    var doOnNumberClick: ((number: Int) -> Unit)? = null
    var doOnBackspaceClick: (() -> Unit)? = null

    private val backspaceView = AppCompatImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setImageResource(uikit.R.drawable.ic_delete_36)
        alpha = 0f
        setOnClickListener {
            doOnBackspaceClick?.invoke()
        }
    }

    private val cellThemeContext = ContextThemeWrapper(context, R.style.Widget_NumPad_Cell)
    private val cellRowSize = context.getDimensionPixelSize(R.dimen.numPadRowSize)
    private val cellViews = mutableListOf<View>()

    var backspace: Boolean = false
        set(value) {
            field = value
            backspaceView.alpha = if (value) 1f else 0f
        }

    init {
        orientation = VERTICAL
        addView(createRow(1, 2, 3))
        addView(createRow(4, 5, 6))
        addView(createRow(7, 8, 9))
        addView(createRow(EMPTY_CELL, 0, BACKSPACE_CELL))
    }

    private fun createRow(vararg numbers: Int): LinearLayoutCompat {
        val row = LinearLayoutCompat(context)
        row.orientation = HORIZONTAL
        for (number in numbers) {
            row.addView(createCell(number), LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        row.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, cellRowSize)
        return row
    }

    private fun createCell(number: Int): View {
        if (number == EMPTY_CELL) {
            return View(cellThemeContext)
        } else if (number == BACKSPACE_CELL) {
            // backspaceView.setOnClickListener(this)
           //  backspaceView.tag = number
            // cellViews.add(backspaceView)
            return backspaceView
        }
        val textView = AppCompatTextView(cellThemeContext, null, 0)
        textView.text = number.toString()
        textView.setOnClickListener(this)
        textView.tag = number
        cellViews.add(textView)
        return textView
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        cellViews.forEach { it.isEnabled = enabled }
    }

    override fun onClick(v: View) {
        val number = v.tag as Int
        doOnNumberClick?.invoke(number)

        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}