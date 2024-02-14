package com.tonapps.tonkeeper.ui.screen.init.pager.child.phrase

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.core.view.isEmpty
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.tonapps.wallet.localization.Localization
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.ton.mnemonic.Mnemonic
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.doKeyboardAnimation
import uikit.extensions.scrollDown
import uikit.extensions.scrollView
import uikit.extensions.setPaddingBottom
import uikit.extensions.setPaddingTop
import uikit.widget.LoaderView
import uikit.widget.WordFormView
import uikit.widget.WordHintView

class PhraseChild: BaseFragment(R.layout.fragment_init_phrase) {

    companion object {

        private const val TESTNET_KEY = "testnet"

        fun newInstance(testnet: Boolean): PhraseChild {
            val fragment = PhraseChild()
            fragment.arguments = Bundle().apply {
                putBoolean(TESTNET_KEY, testnet)
            }
            return fragment
        }
    }

    private val initViewModel: InitViewModel by lazy {
        requireParentFragment().getViewModel()
    }

    private val phraseViewModel: PhraseViewModel by viewModel()

    private val mnemonicWords = Mnemonic.mnemonicWords()

    private lateinit var scrollView: NestedScrollView
    private lateinit var wordFormView: WordFormView
    private lateinit var wordHintView: WordHintView
    private lateinit var nextButton: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scrollView = view.findViewById(R.id.scroll)
        scrollView.doKeyboardAnimation { offset, progress ->
            scrollView.setPaddingBottom(offset)
            updateHintPosition(offset, progress)
            updateScroll(false)
        }

        wordFormView = view.findViewById(R.id.word_form)
        wordFormView.isValidValue = mnemonicWords::contains
        wordFormView.doOnTextChanged = phraseViewModel::hint
        wordFormView.doOnFocusInput = { _, _ ->
            updateScroll(true)
        }
        wordFormView.doOnChange = { _, empty ->
            nextButton.isEnabled = empty == 0
            if (empty == 0) {
                scrollView.scrollDown(true)
            }
        }

        wordHintView = view.findViewById(R.id.word_hint)
        wordHintView.doOnClickText = { word ->
            wordFormView.setWord(word)
            wordHintView.hide()
        }

        nextButton = view.findViewById(R.id.wallet_next)
        nextButton.setOnClickListener {
            val words = wordFormView.getWords()
            initViewModel.setWords(words)
        }

        collectFlow(phraseViewModel.hintWords, ::setHintWords)

        initViewModel.uiTopOffset.onEach {
            scrollView.setPaddingTop(it)
        }.launchIn(lifecycleScope)

        collectFlow(initViewModel.currentPage) {
            if (it == 0) {
                focus()
            } else {
                wordFormView.hideKeyboard()
            }
        }
    }

    private fun setHintWords(words: List<String>) {
        if (words.isEmpty()) {
            wordHintView.hide()
        } else {
            wordHintView.setWords(words)
        }
    }

    private fun updateHintPosition(offset: Int, progress: Float) {
        wordHintView.translationY = -offset.toFloat()
        wordHintView.alpha = progress
    }

    private fun scrollToFocusedInput(smooth: Boolean): Boolean {
        val input = wordFormView.getFocusedInput() ?: return false
        scrollView.scrollView(input, smooth)
        return true
    }

    private fun scrollToEmptyInput(smooth: Boolean): Boolean {
        val input = wordFormView.getLastEmptyInput() ?: return false
        scrollView.scrollView(input, smooth)
        return true
    }

    private fun updateScroll(smooth: Boolean) {
        if (scrollToFocusedInput(smooth)) {
            return
        }
        if (scrollToEmptyInput(smooth)) {
            return
        }

        scrollView.scrollDown(smooth)
    }

    private fun focus() {
        val words = wordFormView.getWords()
        if (words.isEmpty()) {
            wordFormView.focus()
        } else if (words.size != Mnemonic.DEFAULT_WORD_COUNT) {
            val input = wordFormView.getLastEmptyInput() ?: return
            wordFormView.focus(input)
        }
    }

    override fun onPause() {
        super.onPause()
        wordFormView.hideKeyboard()
    }
}