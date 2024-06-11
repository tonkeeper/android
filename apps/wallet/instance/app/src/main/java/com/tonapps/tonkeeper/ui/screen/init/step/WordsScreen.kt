package com.tonapps.tonkeeper.ui.screen.init.step

import android.os.Bundle
import android.text.Editable
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.tonapps.blockchain.ton.TonMnemonic
import com.tonapps.tonkeeper.ui.component.WordEditText
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.iconPrimaryColor
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.doKeyboardAnimation
import uikit.extensions.getCurrentFocusEditText
import uikit.extensions.getViews
import uikit.extensions.hideKeyboard
import uikit.extensions.scrollDown
import uikit.extensions.withAlpha
import uikit.widget.ColumnLayout
import uikit.widget.LoaderView

class WordsScreen: BaseFragment(R.layout.fragment_init_words) {

    private val initViewModel: InitViewModel by viewModel(ownerProducer = { requireParentFragment() })

    private lateinit var scrollView: NestedScrollView
    private lateinit var contentView: ColumnLayout
    private lateinit var button: Button
    private lateinit var loaderView: LoaderView

    private val wordInputs: List<WordEditText> by lazy {
        contentView.getViews().filterIsInstance<WordEditText>()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scrollView = view.findViewById(R.id.scroll)
        scrollView.doKeyboardAnimation { offset, _, _ ->
            scrollView.updatePadding(bottom = offset)
        }

        contentView = view.findViewById(R.id.content)

        button = view.findViewById(R.id.button)
        button.setOnClickListener { next() }

        loaderView = view.findViewById(R.id.loader)
        loaderView.setTrackColor(requireContext().iconPrimaryColor.withAlpha(.32f))
        loaderView.setColor(requireContext().iconPrimaryColor)

        for ((index, wordInput) in wordInputs.withIndex()) {
            wordInput.doOnTextChanged = { onTextChanged(index, it) }
        }

        wordInputs.last().setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                next()
                true
            } else {
                false
            }
        }

        collectFlow(initViewModel.uiTopOffset) {
            contentView.updatePadding(top = it)
        }
    }

    private fun next() {
        lifecycleScope.launch {
            val words = getMnemonic()
            if (words.isEmpty()) {
                return@launch
            }
            setLoading()
            try {
                initViewModel.setMnemonic(words)
            } catch (e: Throwable) {
                setDefault()
            }
        }
    }

    private fun setLoading() {
        button.text = ""
        button.isEnabled = false
        loaderView.visibility = View.VISIBLE
        wordInputs.forEach { it.isEnabled = false }
    }

    private fun setDefault() {
        button.setText(Localization.continue_action)
        button.isEnabled = false
        loaderView.visibility = View.GONE
        wordInputs.forEach { it.isEnabled = true }
    }

    private fun onTextChanged(index: Int, editable: Editable) {
        if (index == 0) {
            val words = TonMnemonic.parseMnemonic(editable.toString())
            if (words.size > 1) {
                editable.clear()
                setWords(words)
                return
            }
        }
        lifecycleScope.launch { checkWords() }
    }

    private suspend fun checkWords() {
        val mnemonic = getMnemonic()
        if (mnemonic.size != 24) {
            button.isEnabled = false
        } else {
            val valid = withContext(Dispatchers.IO) {
                TonMnemonic.isValid(mnemonic)
            }
            button.isEnabled = valid
        }
    }

    private suspend fun getMnemonic(): List<String> = withContext(Dispatchers.IO) {
        val words = wordInputs
            .map { it.text?.toString() }
            .filter { TonMnemonic.isValid(it) }
            .filterNotNull()

        if (words.size == wordInputs.size) {
            words
        } else {
            emptyList()
        }
    }

    private fun setWords(list: List<String>) {
        for (i in list.indices) {
            val wordInput = wordInputs.getOrNull(i) ?: break
            wordInput.setText(list[i])
        }
        if (list.size == wordInputs.size) {
            context?.getCurrentFocusEditText()?.hideKeyboard()
            scrollView.scrollDown(true)
        }
    }

    companion object {

        private const val ARG_TESTNET = "testnet"

        fun newInstance(testnet: Boolean): WordsScreen {
            val fragment = WordsScreen()
            fragment.arguments = Bundle().apply {
                putBoolean(ARG_TESTNET, testnet)
            }
            return fragment
        }
    }
}