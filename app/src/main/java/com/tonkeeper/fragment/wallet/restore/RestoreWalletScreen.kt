package com.tonkeeper.fragment.wallet.restore

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.tonkeeper.R
import com.tonkeeper.ton.MnemonicHelper
import uikit.base.fragment.BaseFragment
import uikit.widget.WordFormView
import uikit.widget.WordHintView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uikit.extensions.dp
import uikit.extensions.scrollToBottom
import uikit.extensions.scrollToView
import uikit.navigation.Navigation.Companion.nav
import uikit.widget.BackHeaderView

class RestoreWalletFragment: BaseFragment(R.layout.fragment_restore_wallet) {

    companion object {
        fun newInstance() = RestoreWalletFragment()
    }

    private lateinit var contentView: ScrollView
    private lateinit var wordFormView: WordFormView
    private lateinit var nextButton: Button
    private lateinit var wordHintView: WordHintView
    private lateinit var headerView: BackHeaderView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        contentView = view.findViewById(R.id.content)

        wordFormView = view.findViewById(R.id.word_form)
        wordFormView.doOnTextChanged = { text ->
            Log.d("KeyboardNewLog", "text: $text")
            searchWord(text)
        }
        wordFormView.doOnFocusInput = { input, index ->
            if (index > 22) {
                contentView.scrollToBottom()
            } else {
                contentView.scrollToView(input)
            }
        }

        nextButton = view.findViewById(R.id.next)

        wordHintView = view.findViewById(R.id.word_hint)
        wordHintView.doOnClickText = { word ->
            wordFormView.setWord(word)
            hideWordHint()
        }

        headerView = view.findViewById(R.id.header)
        headerView.doOnBackClick = {
            nav()?.back()
        }

        headerView.bindContentPadding(contentView)
    }

    private fun searchWord(word: String) {
        if (word.isEmpty()) {
            hideWordHint()
            return
        }
        lifecycleScope.launch(Dispatchers.Main) {
            val result = MnemonicHelper.search(word)
            withContext(Dispatchers.Main) {
                showWordHint(result)
            }
        }
    }

    private fun hideWordHint() {
        wordHintView.hide()
    }

    private fun showWordHint(words: List<String>) {
        if (words.isEmpty()) {
            wordHintView.hide()
            return
        }

        wordHintView.setWords(words)
        contentView.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).also {
            it.bottomMargin = 52.dp
        }
    }
}