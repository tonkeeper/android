package com.tonapps.singer.screen.create.child

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.tonapps.singer.R
import com.tonapps.singer.extensions.fromClipboard
import com.tonapps.singer.screen.create.CreateViewModel
import com.tonapps.singer.screen.create.pager.PageType
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.getViewModel
import uikit.base.BaseFragment
import uikit.extensions.pinToBottomInsets
import uikit.extensions.setPaddingTop
import uikit.widget.WordFormView

class CreatePhraseFragment: BaseFragment(R.layout.fragment_create_phrase) {

    companion object {
        fun newInstance() = CreatePhraseFragment()
    }

    private val createViewModel: CreateViewModel by lazy {
        requireParentFragment().getViewModel()
    }

    private lateinit var scrollView: NestedScrollView
    private lateinit var wordFormView: WordFormView
    private lateinit var nextButton: Button
    private lateinit var pasteButton: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scrollView = view.findViewById(R.id.scroll)

        wordFormView = view.findViewById(R.id.word_form)
        wordFormView.doOnComplete = {
            wordFormView.hideKeyboard()
            nextButton.isEnabled = true
        }

        wordFormView.doOnChange = { fill, _ ->
            pasteButton.visibility = if (fill == 0) {
                View.VISIBLE
            } else {
                View.GONE
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

    private fun paste() {
        val text = context?.fromClipboard() ?: return

        wordFormView.setWord(text)
        wordFormView.hideKeyboard()
    }
}