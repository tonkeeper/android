package com.tonapps.singer.screen.phrase

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.tonapps.singer.R
import com.tonapps.singer.extensions.copyToClipboard
import com.tonapps.singer.screen.key.KeyFragment
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.doOnOnApplyWindowInsets
import uikit.widget.HeaderView
import uikit.widget.PhraseWords

class PhraseFragment: BaseFragment(R.layout.fragment_phrase), BaseFragment.SwipeBack {

    companion object {

        private const val ID_KEY = "id"

        fun newInstance(id: Long): PhraseFragment {
            val fragment = PhraseFragment()
            fragment.arguments = Bundle().apply {
                putLong(ID_KEY, id)
            }
            return fragment
        }
    }

    private val id: Long by lazy { requireArguments().getLong(ID_KEY) }
    private val phraseViewModel: PhraseViewModel by viewModel { parametersOf(id) }

    private lateinit var headerView: HeaderView
    private lateinit var phraseWordsView: PhraseWords
    private lateinit var copyButton: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }

        phraseWordsView = view.findViewById(R.id.phrase_words)

        copyButton = view.findViewById(R.id.copy)

        view.doOnOnApplyWindowInsets {
            val insetsNav = it.getInsets(WindowInsetsCompat.Type.navigationBars())
            copyButton.translationY = -insetsNav.bottom.toFloat()
            it
        }

        phraseViewModel.mnemonic.onEach {
            setWords(it)
        }.launchIn(lifecycleScope)
    }

    private fun setWords(list: List<String>) {
        phraseWordsView.setWords(list)
        copyButton.setOnClickListener {
            val content = list.joinToString(" ")
            requireContext().copyToClipboard(content)
        }
    }
}