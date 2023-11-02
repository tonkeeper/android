package com.tonkeeper.fragment.wallet.restore

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.fragment.app.viewModels
import com.tonkeeper.R
import uikit.widget.WordFormView
import uikit.widget.WordHintView
import uikit.extensions.scrollToBottom
import uikit.extensions.scrollToTop
import uikit.extensions.scrollToView
import uikit.mvi.UiScreen
import uikit.navigation.Navigation.Companion.nav
import uikit.widget.BackHeaderView
import uikit.widget.LoaderView

class RestoreWalletScreen: UiScreen<RestoreWalletScreenState, RestoreWalletEffect, RestoreWalletScreenFeature>(R.layout.fragment_restore_wallet) {

    companion object {
        fun newInstance() = RestoreWalletScreen()
    }

    override val feature: RestoreWalletScreenFeature by viewModels()

    private lateinit var contentView: ScrollView
    private lateinit var wordFormView: WordFormView
    private lateinit var nextButton: Button
    private lateinit var loaderView: LoaderView
    private lateinit var wordHintView: WordHintView
    private lateinit var headerView: BackHeaderView

    private val contentViewLayoutParams: FrameLayout.LayoutParams?
        get() = contentView.layoutParams as? FrameLayout.LayoutParams

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        contentView = view.findViewById(R.id.content)

        wordFormView = view.findViewById(R.id.word_form)
        wordFormView.doOnTextChanged = { text ->
            feature.requestHint(text)
        }
        wordFormView.doOnComplete = {
            feature.checkValidWords(it)
        }
        wordFormView.doOnFocusInput = { input, index ->
            if (index == 0) {
                contentView.scrollToTop()
            } else if (index > 22) {
                contentView.scrollToBottom()
            } else {
                contentView.scrollToView(input)
            }
        }

        nextButton = view.findViewById(R.id.next)
        nextButton.setOnClickListener {
            feature.start(wordFormView.getWords())
        }
        loaderView = view.findViewById(R.id.loader)

        wordHintView = view.findViewById(R.id.word_hint)
        wordHintView.doOnClickText = { word ->
            wordFormView.setWord(word)
            hideWordHint()
        }

        headerView = view.findViewById(R.id.header)
        headerView.doOnBackClick = {
            finish()
        }

        headerView.bindContentPadding(contentView)
    }

    override fun newUiState(state: RestoreWalletScreenState) {
        if (state.done) {
            nav()?.init(false)
            return
        }
        if (state.hintWords.isEmpty()) {
            hideWordHint()
        } else {
            showWordHint(state.hintWords)
        }

        nextButton.isEnabled = state.canNext

        if (state.loading) {
            showButtonLoading()
        } else {
            hideButtonLoading()
        }
    }

    private fun showButtonLoading() {
        loaderView.visibility = View.VISIBLE
        loaderView.resetAnimation()
        nextButton.text = ""
    }

    private fun hideButtonLoading() {
        loaderView.visibility = View.GONE
        loaderView.stopAnimation()
        nextButton.text = getString(R.string.continue_action)
    }

    private fun hideWordHint() {
        wordHintView.hide()
        contentViewLayoutParams?.bottomMargin = 0
    }

    private fun showWordHint(words: List<String>) {
        wordHintView.setWords(words)
        contentViewLayoutParams?.bottomMargin = wordHintView.measuredHeight
    }
}