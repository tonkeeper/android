package com.tonkeeper.fragment.wallet.phrase

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import com.tonkeeper.App
import com.tonkeeper.R
import com.tonkeeper.uikit.navigation.Navigation.Companion.nav
import com.tonkeeper.uikit.base.fragment.WithBackFragment
import com.tonkeeper.uikit.widget.PhraseWords
import kotlinx.coroutines.launch

class PhraseWalletFragment: WithBackFragment(R.layout.fragment_phrase_wallet) {

    companion object {
        fun newInstance() = PhraseWalletFragment()
    }

    private lateinit var contentView: View
    private lateinit var wordsView: PhraseWords
    private lateinit var nextButton: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        contentView = view.findViewById(R.id.content)
        wordsView = view.findViewById(R.id.words)

        nextButton = view.findViewById(R.id.next)
        nextButton.setOnClickListener {
            nav()?.replace(PhraseWalletCheckFragment.newInstance(), true)
        }

        headerView.bindContentPadding(contentView)
        loadWords()
    }

    private fun loadWords() {
        lifecycleScope.launch {
            val words = App.walletManager.getWalletInfo()?.words
            wordsView.setWords(words ?: emptyList())
        }
    }

}