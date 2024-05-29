package uikit.widget

import android.content.Context
import android.util.AttributeSet
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uikit.R
import uikit.extensions.isVisibleForUser
import uikit.extensions.isWords
import uikit.extensions.parseWords

class WordFormView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : ColumnLayout(context, attrs, defStyle) {

    private companion object {
        private const val count = 24
    }

    private val lifecycleScope: LifecycleCoroutineScope?
        get() = findViewTreeLifecycleOwner()?.lifecycleScope

    var isValidValue: ((String) -> Boolean)? = null
    var doOnTextChanged: ((String) -> Unit)? = null
    var doOnFocusInput: ((WordInput, Int) -> Unit)? = null
    var doOnChange: ((fillInputs: Int, emptyInputs: Int) -> Unit)? = null

    private var fillInputs = 0

    init {
        inflate(context, R.layout.view_words_form, this)
        for (i in 0 until count) {
            val view = getInput(i) ?: continue
            view.doOnNext = { nextFocus(i) }
            view.doOnPrev = { prevFocus(i) }
            view.doOnFocus = { focused -> doOnFocus(view, i, focused) }
            view.doOnTextChanged = { text -> doOnTextChanged(i, text) }
            view.setIndex(i + 1)
        }
    }

    private fun doOnTextChanged(index: Int, text: String) {
        if (index == 0 && text.isWords()) {
            lifecycleScope?.launch { checkWords(text) }
        } else {
            doOnTextChanged?.invoke(text)
            lifecycleScope?.launch { checkOnChanged() }
        }
    }

    private fun doOnFocus(view: WordInput, index: Int, focused: Boolean) {
        lifecycleScope?.launch {
            if (view.text.isEmpty()) {
                view.setError(false)
            } else if (!focused && !isValidAndNotRepeat(index, view.text)) {
                view.setError(true)
            }
        }
    }

    private suspend fun isValidValue(word: String): Boolean = withContext(Dispatchers.IO) {
        if (word.isBlank()) {
            return@withContext false
        }
        isValidValue?.invoke(word) ?: true
    }

    private suspend fun isValidAndNotRepeat(index: Int, word: String): Boolean {
        if (!isValidValue(word)) {
            return false
        }
        val repeatCount = getWords().count { it == word }
        return 3 >= repeatCount
        /*for (i in 0 until count) {
            if (i == index) {
                continue
            }
            val input = getInput(i)
            if (input?.text == word) {
                return false
            }
        }
        return true*/
    }

    private suspend fun checkWords(text: String) {
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

    fun focus(input: WordInput? = getInput(0)) {
        input ?: return
        input.focus()
        doOnFocusInput?.invoke(input, indexOfChild(input))
        lifecycleScope?.launch {
            checkOnChanged()
        }
    }

    fun hideKeyboard() {
        getFocusedInput()?.hideKeyboard()
    }

    private fun getInput(index: Int): WordInput? {
        return getChildAt(index) as? WordInput
    }

    fun getFocusedInput(): WordInput? {
        for (i in 0 until count) {
            val input = getInput(i) ?: continue
            if (input.isFocused) {
                return input
            }
        }
        return null
    }

    fun getLastEmptyInput(): WordInput? {
        for (i in 0 until count) {
            val input = getInput(i) ?: continue
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

    fun putWords(word: List<String>) {
        lifecycleScope?.launch {
            setWords(word)
        }
    }

    private suspend fun setWords(words: List<String>) {
        if (words.isEmpty()) {
            return
        }

        for (i in 0 until count) {
            val input = getInput(i) ?: continue
            if (i < words.size) {
                input.text = words[i]
                input.setError(!isValidValue(words[i]))
            }
        }

        if (checkOnChanged()) {
            getInput(count - 1)?.focus(false)
        } else {
            getLastEmptyInput()?.let {
                focus(it)
            }
        }
    }

    suspend fun getWords(): List<String> = withContext(Dispatchers.IO) {
        val words = mutableListOf<String>()
        for (i in 0 until count) {
            val input = getInput(i) ?: continue
            val text = input.text
            if (isValidValue(text)) {
                words.add(text)
                withContext(Dispatchers.Main) {
                    input.setError(false)
                }
            }
        }
        words.toList()
    }

    private suspend fun checkOnChanged(): Boolean {
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