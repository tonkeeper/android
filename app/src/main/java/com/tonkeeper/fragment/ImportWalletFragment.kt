package com.tonkeeper.fragment

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.tonkeeper.R
import com.tonkeeper.core_ton.MnemonicHelper
import com.tonkeeper.uikit.base.BaseFragment
import com.tonkeeper.uikit.widget.WordForm
import com.tonkeeper.uikit.widget.WordHintView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImportWalletFragment: BaseFragment(R.layout.fragment_import_wallet) {

    companion object {
        fun newInstance() = ImportWalletFragment()
    }

    private lateinit var wordFormView: WordForm
    private lateinit var wordHintView: WordHintView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        wordFormView = view.findViewById(R.id.word_form)
        wordFormView.doOnTextChanged = {text ->
            searchWord(text)
        }

        wordHintView = view.findViewById(R.id.word_hint)
        wordHintView.doOnClickText = { word ->
            wordFormView.setWord(word)
        }
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