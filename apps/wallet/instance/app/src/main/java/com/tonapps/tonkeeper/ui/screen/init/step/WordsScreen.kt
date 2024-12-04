package com.tonapps.tonkeeper.ui.screen.init.step

import android.os.Bundle
import android.text.Editable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.tonapps.blockchain.ton.TonMnemonic
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.ui.component.WordEditText
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import com.tonapps.tonkeeperx.BuildConfig
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.backgroundContentTintColor
import com.tonapps.uikit.color.iconPrimaryColor
import com.tonapps.uikit.color.textPrimaryColor
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.ton.mnemonic.Mnemonic
import uikit.base.BaseFragment
import uikit.drawable.FooterDrawable
import uikit.extensions.clear
import uikit.extensions.collectFlow
import uikit.extensions.doKeyboardAnimation
import uikit.extensions.dp
import uikit.extensions.getCurrentFocusEditText
import uikit.extensions.getViews
import uikit.extensions.hideKeyboard
import uikit.extensions.scrollDown
import uikit.extensions.scrollView
import uikit.extensions.withAlpha
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.ColumnLayout
import uikit.widget.LoaderView
import uikit.widget.RowLayout
import uikit.widget.TextHeaderView

// TODO Need to refactor this screen
class WordsScreen: BaseFragment(R.layout.fragment_init_words) {

    private val initViewModel: InitViewModel by viewModel(ownerProducer = { requireParentFragment() })

    override val secure: Boolean = !BuildConfig.DEBUG

    private var wordsCount = WORDS24

    private lateinit var scrollView: NestedScrollView
    private lateinit var contentView: ColumnLayout
    private lateinit var button: Button
    private lateinit var loaderView: LoaderView
    private lateinit var suggestionsView: RowLayout
    private lateinit var words24View: AppCompatTextView
    private lateinit var words12View: AppCompatTextView
    private lateinit var titleView: TextHeaderView

    private val isVisibleSuggestions: Boolean
        get() = suggestionsView.visibility == View.VISIBLE && suggestionsView.alpha > 0f

    private val wordInputs: List<WordEditText> by lazy {
        contentView.getViews().filterIsInstance<WordEditText>()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        titleView = view.findViewById(R.id.header_title)

        words24View = view.findViewById(R.id.words_24)
        words24View.setOnClickListener { setWordsCount(WORDS24) }

        words12View = view.findViewById(R.id.words_12)
        words12View.setOnClickListener { setWordsCount(WORDS12) }

        scrollView = view.findViewById(R.id.scroll)

        contentView = view.findViewById(R.id.content)

        button = view.findViewById(R.id.button)
        button.setOnClickListener { next() }

        loaderView = view.findViewById(R.id.loader)
        loaderView.setTrackColor(requireContext().iconPrimaryColor.withAlpha(.32f))
        loaderView.setColor(requireContext().iconPrimaryColor)

        for ((index, wordInput) in wordInputs.withIndex()) {
            wordInput.doOnFocusChanged = { onFocusChange(index, it) }
            wordInput.doOnTextChanged = { onTextChanged(index, it) }
            wordInput.imeOptions = if (isLastIndex(index)) EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NEXT
            wordInput.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_NEXT && isVisibleSuggestions && suggestionsView.childCount == 1) {
                    autoSetWord(index)
                }
                if (isLastIndex(index) && actionId == EditorInfo.IME_ACTION_DONE) {
                    next()
                    true
                } else if (isLastIndex(index) && actionId == EditorInfo.IME_ACTION_NEXT) {
                    nextInput(index)
                    true
                } else {
                    false
                }
            }
        }

        suggestionsView = view.findViewById(R.id.suggestions)
        suggestionsView.background = FooterDrawable(requireContext()).apply {
            setDivider(true)
            setColor(requireContext().backgroundContentTintColor)
        }

        collectFlow(initViewModel.uiTopOffset) {
            contentView.updatePadding(top = it)
        }

        scrollView.doKeyboardAnimation { offset, progress, isShowing ->
            scrollView.updatePadding(bottom = offset)
            suggestionsView.translationY = -offset.toFloat()
            suggestionsView.alpha = progress
        }
    }

    private fun isLastIndex(index: Int): Boolean {
        return wordsCount - 1 == index
    }

    private fun setWordsCount(count: Int) {
        if (wordsCount == count || count != WORDS24 && count != WORDS12) {
            return
        }
        if (count == WORDS24) {
            words24View.setBackgroundResource(uikit.R.drawable.bg_content_tint_16)
            words12View.background = null
            wordsCount = count
            titleView.desciption = getString(Localization.import_wallet_description)
            wordInputs[11].imeOptions = EditorInfo.IME_ACTION_NEXT
        } else {
            words12View.setBackgroundResource(uikit.R.drawable.bg_content_tint_16)
            words24View.background = null
            titleView.desciption = getString(Localization.import_wallet_description).replace("24", "12")
            wordInputs[23].imeOptions = EditorInfo.IME_ACTION_NEXT
        }
        wordsCount = count
        wordInputs[count - 1].imeOptions = EditorInfo.IME_ACTION_DONE
        updateVisibleInputs()
    }

    private fun updateVisibleInputs() {
        wordInputs.forEachIndexed { index, wordInput ->
            wordInput.visibility = if (index < wordsCount) View.VISIBLE else View.GONE
        }
        postOnAnimation { checkWords() }
    }

    private fun nextInput(index: Int) {
        val nextView = wordInputs.getOrNull(index + 1) ?: return
        nextView.requestFocus()
        scrollView.scrollView(nextView, false)
    }

    private fun autoSetWord(index: Int) {
        val inputView = wordInputs.getOrNull(index) ?: return
        val text = inputView.text.toString()
        lifecycleScope.launch(Dispatchers.IO) {
            val word = TonMnemonic.findWord(text)
            if (!word.isNullOrBlank()) {
                withContext(Dispatchers.Main) {
                    inputView.setText(word)
                }
            }
        }
    }

    private fun next() {
        lifecycleScope.launch {
            val words = getMnemonic()
            if (words.isEmpty() || (wordsCount == WORDS24 && !Mnemonic.isValid(words))) {
                if (TonMnemonic.isValidTONKeychain(words)) {
                    navigation?.toast(Localization.multi_account_secret_wrong)
                } else {
                    navigation?.toast(Localization.incorrect_phrase)
                }
                return@launch
            }
            setLoading()
            try {
                initViewModel.setMnemonic(words)
            } catch (e: Throwable) {
                setDefault()
            }
        }
    }

    private fun setLoading() {
        button.text = ""
        button.isEnabled = false
        loaderView.visibility = View.VISIBLE
        wordInputs.forEach { it.isEnabled = false }
    }

    private fun setDefault() {
        button.setText(Localization.continue_action)
        button.isEnabled = false
        loaderView.visibility = View.GONE
        wordInputs.forEach { it.isEnabled = true }
    }

    private fun onFocusChange(index: Int, hasFocus: Boolean) {
        if (hasFocus) {
            checkSuggestions(index, wordInputs[index].text.toString())
        } else {
            suggestionsView.visibility = View.GONE
        }
    }

    private fun onTextChanged(index: Int, editable: Editable) {
        if (index == 0) {
            val words = TonMnemonic.parseMnemonic(editable.toString())
            postOnAnimation {
                if (words.isNotEmpty()) {
                    applyWords(words)
                } else {
                    checkSuggestions(index, editable.toString())
                }
            }
        } else {
            postOnAnimation {
                checkWords()
                checkSuggestions(index, editable.toString())
            }
        }
    }

    private fun checkSuggestions(index: Int, text: String) {
        if (!wordInputs[index].isFocused || text.isEmpty()) {
            suggestionsView.visibility = View.GONE
        } else {
            lifecycleScope.launch(Dispatchers.IO) {
                val words = TonMnemonic.findWords(text).take(3)
                if (words.size == 1 && words.first().equals(text, true)) {
                    setSuggestions(index, emptyList())
                } else {
                    setSuggestions(index, words)
                }
            }
        }
    }

    private suspend fun setSuggestions(
        index: Int,
        words: List<String>
    ) = withContext(Dispatchers.Main) {
        if (words.isEmpty()) {
            suggestionsView.visibility = View.GONE
        } else {
            suggestionsView.removeAllViews()
            for (word in words) {
                val textView = AppCompatTextView(requireContext()).apply {
                    setTextAppearance(uikit.R.style.TextAppearance_Label2)
                    setTextColor(requireContext().textPrimaryColor)
                    text = word
                    gravity = Gravity.CENTER
                    setOnClickListener { setWord(index, word) }
                }
                suggestionsView.addView(textView, LinearLayoutCompat.LayoutParams(LinearLayoutCompat.LayoutParams.MATCH_PARENT, LinearLayoutCompat.LayoutParams.MATCH_PARENT, 1f))
            }
            suggestionsView.visibility = View.VISIBLE
        }
    }

    private fun setWord(index: Int, value: String) {
        if (suggestionsView.alpha == 0f || suggestionsView.visibility != View.VISIBLE) {
            return
        }
        suggestionsView.visibility = View.GONE
        val inputView = wordInputs.getOrNull(index) ?: return
        inputView.setText(value)

        val nextFocusInput = inputView.focusSearch(View.FOCUS_DOWN) as? WordEditText

        if (nextFocusInput != null && nextFocusInput.visibility == View.VISIBLE) {
            nextFocusInput.requestFocus()
            if (index > (wordsCount - 4)) {
                scrollView.scrollDown(true)
            } else {
                scrollView.scrollView(
                    view = nextFocusInput,
                    smooth = true,
                    top = (-128).dp
                )
            }
        } else {
            next()
        }
    }

    private fun applyWords(words: List<String>) {
        if (words.size > 1) {
            wordInputs.first().clear()
            setWords(words)
        } else {
            checkWords()
        }
    }

    private fun checkWords(delay: Long = 0) {
        lifecycleScope.launch {
            if (delay > 0) {
                delay(delay)
            }
            button.isEnabled = getMnemonic().size == wordsCount
        }
    }

    private suspend fun getMnemonic(): List<String> = withContext(Dispatchers.IO) {
        val words = wordInputs
            .map { it.text?.toString() }
            .filter { TonMnemonic.isValid(it) }
            .filterNotNull()
            .take(wordsCount)

        if (words.size == wordInputs.count { it.visibility == View.VISIBLE }) {
            words
        } else {
            emptyList()
        }
    }

    private fun setWords(list: List<String>) {
        for (i in list.indices) {
            val wordInput = wordInputs.getOrNull(i) ?: break
            wordInput.setText(list[i])
        }
        if (list.size == wordInputs.size) {
            context?.getCurrentFocusEditText()?.hideKeyboard()
            scrollView.scrollDown(true)
            checkWords(500)
        }
    }

    companion object {

        private const val WORDS24 = 24
        private const val WORDS12 = 12

        private const val ARG_TESTNET = "testnet"

        fun newInstance(testnet: Boolean): WordsScreen {
            val fragment = WordsScreen()
            fragment.arguments = Bundle().apply {
                putBoolean(ARG_TESTNET, testnet)
            }
            return fragment
        }
    }
}