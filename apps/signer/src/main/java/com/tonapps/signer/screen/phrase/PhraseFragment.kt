package com.tonapps.signer.screen.phrase

import android.os.Bundle
import android.view.View
import android.widget.Button
import com.tonapps.signer.R
import com.tonapps.signer.extensions.copyToClipboard
import uikit.base.BaseFragment
import uikit.widget.HeaderView
import uikit.widget.PhraseWords

class PhraseFragment: BaseFragment(R.layout.fragment_phrase), BaseFragment.SwipeBack {

    companion object {

        private const val MNEMONIC_KEY = "mnemonic"

        fun newInstance(mnemonic: Array<String>): PhraseFragment {
            val fragment = PhraseFragment()
            fragment.arguments = Bundle().apply {
                putStringArray(MNEMONIC_KEY, mnemonic)
            }
            return fragment
        }
    }

    private val mnemonic: Array<String> by lazy {
        requireArguments().getStringArray(MNEMONIC_KEY)!!
    }

    private lateinit var headerView: HeaderView
    private lateinit var phraseWordsView: PhraseWords
    private lateinit var copyButton: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }

        phraseWordsView = view.findViewById(R.id.phrase_words)
        phraseWordsView.setWords(mnemonic)

        copyButton = view.findViewById(R.id.copy)
        copyButton.setOnClickListener {
            val content = mnemonic.joinToString(" ")
            requireContext().copyToClipboard(content, true)
        }
    }
}