package com.tonkeeper.fragment.wallet.imprt

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.tonkeeper.R
import com.tonkeeper.ton.MnemonicHelper
import com.tonkeeper.uikit.base.fragment.WithBackFragment
import com.tonkeeper.uikit.widget.WordFormView
import com.tonkeeper.uikit.widget.WordHintView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImportWalletFragment: WithBackFragment(R.layout.fragment_import_wallet) {

    companion object {
        fun newInstance() = ImportWalletFragment()
    }

    private lateinit var contentView: View
    private lateinit var wordFormView: WordFormView
    private lateinit var wordHintView: WordHintView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        contentView = view.findViewById(R.id.content)

        wordFormView = view.findViewById(R.id.word_form)
        wordFormView.doOnTextChanged = {text ->
            searchWord(text)
        }

        wordHintView = view.findViewById(R.id.word_hint)
        wordHintView.doOnClickText = { word ->
            wordFormView.setWord(word)
        }

        headerView.bindContentPadding(contentView)
    }

    private fun searchWord(word: String) {
        if (word.isEmpty()) {
            wordHintView.hide()
            return
        }
        lifecycleScope.launch(Dispatchers.Main) {
            val result = MnemonicHelper.search(word)
            withContext(Dispatchers.Main) {
                wordHintView.setWords(result)
            }
        }
    }
}