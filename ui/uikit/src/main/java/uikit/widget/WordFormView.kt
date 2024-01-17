package uikit.widget

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.LinearLayoutCompat
import uikit.R
import uikit.extensions.getDimensionPixelSize

class WordFormView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayoutCompat(context, attrs, defStyle) {

    private val count = 24
    private val inputLayoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).also {
        it.bottomMargin = context.getDimensionPixelSize(R.dimen.offsetMedium)
    }

    var doOnTextChanged: ((String) -> Unit)? = null
    var doOnFocusInput: ((WordInput, Int) -> Unit)? = null
    var doOnComplete: ((words: List<String>) -> Unit)? = null

    init {
        orientation = VERTICAL
        for (i in 0 until count) {
            val view = WordInput(context)
            view.doOnNext = { nextFocus(i) }
            view.doOnPrev = { prevFocus(i) }
            view.doOnTextChanged = { text ->
                if (i == 0) {
                    checkPaste(text)
                }
                checkIsFull()
                doOnTextChanged?.invoke(text)
            }
            view.setIndex(i + 1)
            addView(view, inputLayoutParams)
        }
    }

    private fun checkPaste(text: String) {
        val isFullPhrase = text.contains(",") || text.contains("\n") || text.contains(" ")
        if (!isFullPhrase) {
            return
        }

        val words = text.split(",", "\n", " ").map {
            it.trim()
        }.filter {
            it.isNotEmpty()
        }
        for (i in 0 until count) {
            val input = getInput(i)
            if (i < words.size) {
                input.text = words[i]
            }
            nextFocus(i)
        }
        checkIsFull()
    }

    private fun checkIsFull() {
        val words = getWords()
        if (words.size == count) {
            doOnComplete?.invoke(words)
        }
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
            val text = getInput(i).text.trim()
            if (text.isEmpty()) {
                break
            }
            words.add(text)
        }
        return words
    }
}