package uikit.widget

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.LinearLayoutCompat
import uikit.R
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.hideKeyboard
import uikit.extensions.isWords
import uikit.extensions.parseWords

class WordFormView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : ColumnLayout(context, attrs, defStyle) {

    private val count = 24

    private val inputLayoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).also {
        it.bottomMargin = context.getDimensionPixelSize(R.dimen.offsetMedium)
    }

    var isValidValue: ((String) -> Boolean)? = null
    var doOnTextChanged: ((String) -> Unit)? = null
    var doOnFocusInput: ((WordInput, Int) -> Unit)? = null
    var doOnChange: ((fillInputs: Int, emptyInputs: Int) -> Unit)? = null

    private var fillInputs = 0

    init {
        for (i in 0 until count) {
            val view = WordInput(context)
            view.doOnFocus = { focused ->
                if (view.text.isEmpty()) {
                    view.setError(false)
                } else if (!focused && !isValidValue(view.text)) {
                    view.setError(true)
                }
            }
            view.doOnNext = { nextFocus(i) }
            view.doOnPrev = { prevFocus(i) }
            view.doOnTextChanged = { text ->
                if (i == 0 && text.isWords()) {
                    checkWords(text)
                } else {
                    doOnTextChanged?.invoke(text)
                    checkOnChanged()
                }
            }
            view.setIndex(i + 1)
            addView(view, inputLayoutParams)
        }
    }

    private fun isValidValue(word: String): Boolean {
        val value = word.trim()
        if (value.isBlank()) {
            return false
        }
        return isValidValue?.invoke(value) ?: true
    }

    private fun checkWords(text: String) {
        setWords(text.parseWords())
    }

    private fun nextFocus(index: Int) {
        if ((index + 1) > count - 1) return
        val input = getInput(index + 1)
        focus(input)
    }

    private fun prevFocus(index: Int) {
        if (index <= 0) return
        val input = getInput(index - 1)
        focus(input)
    }

    fun focus(input: WordInput = getInput(0)) {
        input.focus()
        doOnFocusInput?.invoke(input, indexOfChild(input))
        checkOnChanged()
    }

    fun hideKeyboard() {
        getFocusedInput()?.hideKeyboard()
    }

    private fun getInput(index: Int): WordInput {
        return getChildAt(index) as WordInput
    }

    fun getFocusedInput(): WordInput? {
        for (i in 0 until count) {
            val input = getInput(i)
            if (input.isFocused) {
                return input
            }
        }
        return null
    }

    fun getLastEmptyInput(): WordInput? {
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

    fun setWords(words: List<String>) {
        if (words.isEmpty()) {
            return
        }

        for (i in 0 until count) {
            val input = getInput(i)
            if (i < words.size) {
                input.text = words[i]
            }
        }

        if (checkOnChanged()) {
            getInput(count - 1).focus(false)
            return
        }

        val lastEmpty = getLastEmptyInput() ?: return
        focus(lastEmpty)
    }

    fun getWords(): List<String> {
        val words = mutableListOf<String>()
        for (i in 0 until count) {
            val input = getInput(i)
            val text = input.text.trim()
            if (isValidValue(text)) {
                words.add(text)
                input.setError(false)
            }
        }
        return words.toList()
    }

    private fun checkOnChanged(): Boolean {
        val words = getWords()
        val newFillInputs = words.size
        if (fillInputs == newFillInputs) {
            return words.size == count
        }
        fillInputs = newFillInputs
        doOnChange?.invoke(newFillInputs, count - newFillInputs)
        return words.size == count
    }
}