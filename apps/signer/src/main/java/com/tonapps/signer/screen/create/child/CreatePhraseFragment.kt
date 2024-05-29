package com.tonapps.signer.screen.create.child

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.tonapps.signer.R
import com.tonapps.signer.extensions.fromClipboard
import com.tonapps.signer.screen.create.CreateViewModel
import com.tonapps.signer.screen.create.pager.PageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.ton.mnemonic.Mnemonic
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.doKeyboardAnimation
import uikit.extensions.parseWords
import uikit.extensions.scrollDown
import uikit.extensions.scrollView
import uikit.extensions.setPaddingBottom
import uikit.extensions.setPaddingTop
import uikit.extensions.withAnimation
import uikit.widget.WordFormView

class CreatePhraseFragment: BaseFragment(R.layout.fragment_create_phrase) {

    companion object {
        fun newInstance() = CreatePhraseFragment()
    }

    private val createViewModel: CreateViewModel by lazy {
        requireParentFragment().getViewModel()
    }

    private val mnemonicWords = Mnemonic.mnemonicWords()

    private lateinit var scrollView: NestedScrollView
    private lateinit var wordFormView: WordFormView
    private lateinit var nextButton: Button
    private lateinit var pasteButton: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scrollView = view.findViewById(R.id.scroll)
        scrollView.doKeyboardAnimation { offset, _, _ ->
            scrollView.setPaddingBottom(offset)
            pasteButton.translationY = -offset.toFloat()
            updateScroll(false)
        }

        wordFormView = view.findViewById(R.id.word_form)
        wordFormView.isValidValue = mnemonicWords::contains
        wordFormView.doOnFocusInput = { _, _ ->
            updateScroll(true)
        }

        wordFormView.doOnChange = { fill, empty ->
            visibleParse(fill == 0)
            checkWords(fill, empty)
            if (empty == 0) {
                scrollView.scrollDown(true)
            }
        }

        nextButton = view.findViewById(R.id.next)
        nextButton.setOnClickListener { saveMnemonic() }

        pasteButton = view.findViewById(R.id.paste)
        pasteButton.setOnClickListener { paste() }

        collectFlow(createViewModel.page(PageType.Phrase)) {
            wordFormView.focus()
        }

        collectFlow(createViewModel.uiTopOffset) {
            scrollView.setPaddingTop(it)
        }
    }

    private fun checkWords(fill: Int, empty: Int) {
        if (empty != 0) {
            nextButton.isEnabled = false
            return
        }

        lifecycleScope.launch(Dispatchers.Main) {
            val words = wordFormView.getWords()
            val isValid = withContext(Dispatchers.IO) {
                Mnemonic.isValid(words)
            }
            nextButton.isEnabled = isValid
        }
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

    private fun paste() {
        val text = context?.fromClipboard() ?: return

        wordFormView.putWords(text.parseWords())
    }

    private fun saveMnemonic() {
        lifecycleScope.launch {
            createViewModel.setMnemonic(wordFormView.getWords())
        }
    }

    private fun visibleParse(visible: Boolean) {
        val currentVisible = pasteButton.visibility == View.VISIBLE
        if (currentVisible != visible) {
            withAnimation {
                pasteButton.visibility = if (visible) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
        }
    }
}