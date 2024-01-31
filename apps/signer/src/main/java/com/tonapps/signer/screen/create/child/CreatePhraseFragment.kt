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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.ton.mnemonic.Mnemonic
import uikit.base.BaseFragment
import uikit.extensions.doOnBottomInsetsChanged
import uikit.extensions.parseWords
import uikit.extensions.pinToBottomInsets
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
        scrollView.doOnBottomInsetsChanged { offset, _ ->
            scrollView.setPaddingBottom(offset)
            updateScroll(false)
        }

        wordFormView = view.findViewById(R.id.word_form)
        wordFormView.isValidValue = mnemonicWords::contains

        wordFormView.doOnFocusInput = { _, _ ->
            updateScroll(true)
        }

        wordFormView.doOnChange = { fill, empty ->
            visibleParse(fill == 0)
            nextButton.isEnabled = empty == 0
            if (empty == 0) {
                scrollView.scrollDown(true)
            }
        }

        nextButton = view.findViewById(R.id.next)
        nextButton.setOnClickListener {
            createViewModel.setMnemonic(wordFormView.getWords())
        }

        pasteButton = view.findViewById(R.id.paste)
        pasteButton.setOnClickListener { paste() }
        pasteButton.pinToBottomInsets()

        createViewModel.page(PageType.Phrase).onEach {
            wordFormView.focus()
        }.launchIn(lifecycleScope)

        createViewModel.uiTopOffset.onEach {
            scrollView.setPaddingTop(it)
        }.launchIn(lifecycleScope)
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

        wordFormView.setWords(text.parseWords())
    }

    private fun visibleParse(visible: Boolean) {
        withAnimation {
            pasteButton.visibility = if (visible) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }
}