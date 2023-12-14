package com.tonkeeper.fragment.wallet.init.pager.child

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import androidx.fragment.app.viewModels
import com.tonkeeper.R
import com.tonkeeper.extensions.launch
import com.tonkeeper.fragment.wallet.init.InitModel
import uikit.base.BaseFragment
import uikit.extensions.scrollToBottom
import uikit.extensions.scrollToTop
import uikit.extensions.scrollToView
import uikit.widget.WordFormView
import uikit.widget.WordHintView

class ImportChild: BaseFragment(R.layout.fragment_import) {

    companion object {
        fun newInstance() = ImportChild()
    }

    private val parentFeature: InitModel by viewModels({ requireParentFragment() })

    private lateinit var wordContent: ScrollView
    private lateinit var wordFormView: WordFormView
    private lateinit var wordHintView: WordHintView
    private lateinit var nextButton: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        wordContent = view.findViewById(R.id.word_content)

        wordFormView = view.findViewById(R.id.word_form)
        wordFormView.doOnTextChanged = { text ->
            parentFeature.requestHint(text)
        }
        wordFormView.doOnComplete = {
            parentFeature.requestCheckValidWords(it)
        }
        wordFormView.doOnFocusInput = { input, index ->
            if (index == 0) {
                wordContent.scrollToTop()
            } else if (index > 22) {
                wordContent.scrollToBottom()
            } else {
                wordContent.scrollToView(input)
            }
        }


        wordHintView = view.findViewById(R.id.word_hint)
        wordHintView.doOnClickText = { word ->
            wordFormView.setWord(word)
            wordHintView.hide()
        }

        nextButton = view.findViewById(R.id.next)
        nextButton.setOnClickListener {
            val words = wordFormView.getWords()
            parentFeature.setWords(words)
        }

        parentFeature.hintWords.launch(this) { words ->
            if (words.isEmpty()) {
                wordHintView.hide()
            } else {
                wordHintView.setWords(words)
            }
        }

        parentFeature.validWords.launch(this) { isValid ->
            nextButton.isEnabled = isValid
        }
    }

    override fun onResume() {
        super.onResume()
        wordFormView.focus()
    }
}