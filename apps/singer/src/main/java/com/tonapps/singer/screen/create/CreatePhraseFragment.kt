package com.tonapps.singer.screen.create

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.tonapps.singer.R
import com.tonapps.singer.screen.create.pager.PageType
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.getViewModel
import uikit.base.BaseFragment
import uikit.extensions.doOnOnApplyWindowInsets
import uikit.widget.WordFormView

class CreatePhraseFragment: BaseFragment(R.layout.fragment_create_phrase) {

    companion object {
        fun newInstance() = CreatePhraseFragment()
    }

    private val createViewModel: CreateViewModel by lazy {
        requireParentFragment().getViewModel()
    }

    private lateinit var wordFormView: WordFormView
    private lateinit var nextButton: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        wordFormView = view.findViewById(R.id.word_form)
        wordFormView.doOnComplete = {
            wordFormView.hideKeyboard()
            nextButton.isEnabled = true
        }

        nextButton = view.findViewById(R.id.next)
        nextButton.setOnClickListener {
            createViewModel.setMnemonic(wordFormView.getWords())
        }

        createViewModel.page(PageType.Phrase).onEach {
            wordFormView.focus()
        }.launchIn(lifecycleScope)
    }

}