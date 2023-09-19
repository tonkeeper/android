package com.tonkeeper.uikit.widget

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.LinearLayoutCompat
import com.tonkeeper.uikit.R
import com.tonkeeper.uikit.extensions.getDimensionPixelSize

class WordForm @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayoutCompat(context, attrs, defStyle) {

    private val count = 24
    private val inputLayoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).also {
        it.bottomMargin = context.getDimensionPixelSize(R.dimen.offsetMedium)
    }

    var doOnTextChanged: ((String) -> Unit)? = null
        set(value) {
            field = value
            for (i in 0 until count) {
                getInput(i).doOnTextChanged = value
            }
        }

    init {
        orientation = VERTICAL
        for (i in 0 until count) {
            val view = WordInput(context)
            view.doOnNext = { nextFocus(i) }
            view.doOnPrev = { prevFocus(i) }
            view.setIndex(i + 1)
            addView(view, inputLayoutParams)
        }
    }

    private fun nextFocus(index: Int) {
        if (index >= count - 1) return
        val input = getInput(index)
        input.focus()
    }

    private fun prevFocus(index: Int) {
        if (index <= 0) return
        getInput(index - 1).focus()
    }

    private fun getInput(index: Int): WordInput {
        return getChildAt(index) as WordInput
    }

    private fun getFocusedInput(): WordInput? {
        for (i in 0 until count) {
            val input = getInput(i)
            if (input.isFocused) {
                return input
            }
        }
        return null
    }

    private fun getLastEmptyInput(): WordInput? {
        for (i in count - 1 downTo 0) {
            val input = getInput(i)
            if (input.text.isEmpty()) {
                return input
            }
        }
        return null
    }

    private fun getSupposedInput(): WordInput? {
        return getFocusedInput() ?: getLastEmptyInput()
    }

    fun setWord(word: String) {
        val input = getSupposedInput() ?: return
        input.text = word
        nextFocus(indexOfChild(input))
    }

    fun getWords(): List<String> {
        val words = mutableListOf<String>()
        for (i in 0 until count) {
            words.add(getInput(i).text)
        }
        return words
    }
}