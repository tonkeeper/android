package com.tonapps.tonkeeper.fragment.wallet.phrase

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import com.tonapps.wallet.localization.Localization
import com.tonapps.tonkeeperx.R
import ton.wallet.WalletManager
import uikit.widget.TextHeaderView
import uikit.widget.WordInput
import kotlinx.coroutines.launch
import uikit.base.BaseFragment
import uikit.widget.HeaderView

class PhraseWalletCheckFragment: BaseFragment(R.layout.fragment_phrase_wallet_check), BaseFragment.SwipeBack {

    companion object {
        fun newInstance() = PhraseWalletCheckFragment()
    }

    private val wordIndexes: List<Int> by lazy {
        val numbers = mutableSetOf<Int>()
        while (numbers.size < 3) {
            numbers.add((1..WalletManager.MNEMONIC_WORD_COUNT).random())
        }
        numbers.sorted()
    }
    private var words = listOf<String>()

    private lateinit var headerView: HeaderView
    private lateinit var textHeaderView: TextHeaderView
    private lateinit var contentView: View
    private lateinit var wordInput1: WordInput
    private lateinit var wordInput2: WordInput
    private lateinit var wordInput3: WordInput
    private lateinit var nextButton: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }

        textHeaderView = view.findViewById(R.id.text_header)
        textHeaderView.desciption = getString(Localization.phrase_check_description, wordIndexes[0], wordIndexes[1], wordIndexes[2])

        contentView = view.findViewById(R.id.content)

        wordInput1 = view.findViewById(R.id.word_input_1)
        wordInput1.setIndex(wordIndexes[0])
        wordInput1.doOnTextChanged = {
            if (it.isEmpty()) {
                wordInput1.setError(false)
            }
            checkWords()
        }
        wordInput1.doOnFocus = { focus ->
            if (!focus) {
                checkWord(0, wordInput1)
            }
        }
        wordInput1.doOnNext = {
            wordInput2.focus()
        }

        wordInput2 = view.findViewById(R.id.word_input_2)
        wordInput2.setIndex(wordIndexes[1])
        wordInput2.doOnTextChanged = {
            if (it.isEmpty()) {
                wordInput2.setError(false)
            }
            checkWords()
        }
        wordInput2.doOnFocus = { focus ->
            if (!focus) {
                checkWord(1, wordInput2)
            }
        }
        wordInput2.doOnNext = {
            wordInput3.focus()
        }
        wordInput2.doOnPrev = {
            wordInput1.focus()
        }

        wordInput3 = view.findViewById(R.id.word_input_3)
        wordInput3.setIndex(wordIndexes[2])
        wordInput3.doOnTextChanged = {
            if (it.isEmpty()) {
                wordInput3.setError(false)
            }
            checkWords()
        }
        wordInput3.doOnFocus = { focus ->
            if (!focus) {
                checkWord(2, wordInput3)
            }
        }
        wordInput3.doOnPrev = {
            wordInput2.focus()
        }

        nextButton = view.findViewById(R.id.next)
        nextButton.setOnClickListener {
            // navigation?.init(false)
        }

        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val wallet = com.tonapps.tonkeeper.App.walletManager.getWalletInfo() ?: return@launch
            val words = com.tonapps.tonkeeper.App.walletManager.getMnemonic(wallet.id)
            setWords(words)
        }
    }

    private fun checkWords() {
        val isOkWord1 = wordInput1.text == words[wordIndexes[0] - 1]
        val isOkWord2 = wordInput2.text == words[wordIndexes[1] - 1]
        val isOkWord3 = wordInput3.text == words[wordIndexes[2] - 1]

        nextButton.isEnabled = isOkWord1 && isOkWord2 && isOkWord3
    }

    private fun setWords(words: List<String>) {
        this.words = words
    }

    private fun checkWord(index: Int, view: WordInput) {

        checkWords()

        val text = view.text
        if (text.isEmpty()) {
            view.setError(false)
            return
        }

        val wordIndex = wordIndexes[index]
        val word = words[wordIndex - 1]
        view.setError(text != word)
    }

    override fun onResume() {
        super.onResume()
        wordInput1.focus()
    }

}