package com.tonapps.tonkeeper.ui.screen.init.step

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import com.tonapps.tonkeeper.extensions.hideKeyboard
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import com.tonapps.tonkeeperx.BuildConfig
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.doKeyboardAnimation
import uikit.extensions.scrollView
import uikit.widget.HeaderView
import uikit.widget.TextHeaderView
import uikit.widget.WordInput

class BackupCheckInitScreen : BaseFragment(R.layout.fragment_backup_check) {

    override val fragmentName: String = "BackupCheckInitScreen"

    override val secure: Boolean = !BuildConfig.DEBUG

    private val initViewModel: InitViewModel by viewModel(ownerProducer = { requireParentFragment() })

    private val words: Array<String> by lazy {
        initViewModel.getMnemonic()?.toTypedArray() ?: emptyArray()
    }

    private val indexes: IntArray by lazy {
        words.indices.shuffled().take(3.coerceAtMost(words.size))
            .sorted()
            .toIntArray()
    }

    private lateinit var button: Button
    private lateinit var wordInputs: List<WordInput>
    private lateinit var scrollView: NestedScrollView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<HeaderView>(R.id.header).visibility = View.GONE

        view.findViewById<TextHeaderView>(R.id.text).desciption =
            getString(
                Localization.backup_check_subtitle,
                indexes[0] + 1,
                indexes[1] + 1,
                indexes[2] + 1
            )

        scrollView = view.findViewById(R.id.scroll)

        wordInputs = listOf(
            view.findViewById(R.id.word_input_1),
            view.findViewById(R.id.word_input_2),
            view.findViewById(R.id.word_input_3)
        )

        for (i in wordInputs.indices) {
            val hasNext = i < wordInputs.size - 1
            val hasPrev = i > 0
            val wordInput = wordInputs[i]
            wordInput.setIndex(indexes[i] + 1)
            wordInput.doOnNext = {
                if (hasNext) wordInputs[i + 1].focus(true) else confirmBackup()
            }
            wordInput.doOnPrev = {
                if (hasPrev) wordInputs[i - 1].focus(false)
            }
            wordInput.doOnTextChanged = { checkEnableButton() }
            wordInput.doOnFocus = { focus ->
                if (focus) scrollView.postOnAnimation { scrollView.scrollView(wordInput) }
                checkWords()
            }
        }

        button = view.findViewById(R.id.done)
        button.isEnabled = false
        button.setOnClickListener { confirmBackup() }
        button.text = getString(Localization.continue_action)

        scrollView.doKeyboardAnimation { offset, progress, _ ->
            scrollView.updatePadding(bottom = offset + button.height)
            button.translationY = -offset.toFloat()
            if (progress >= .9f || .1f >= progress) {
                getCurrentFocus()?.let { scrollView.postOnAnimation { scrollView.scrollView(it) } }
            }
        }

        collectFlow(initViewModel.uiTopOffset) {
            scrollView.updatePadding(top = it)
        }

        wordInputs.first().focus(true)
    }

    private fun confirmBackup() {
        if (button.isEnabled) {
            hideKeyboard()
            initViewModel.completeBackup()
        }
    }

    private fun checkEnableButton() {
        val inputWords = wordInputs.map { it.text.trim() }.filter { it.isNotEmpty() }
        button.isEnabled = if (inputWords.size != indexes.size) {
            false
        } else {
            inputWords == indexes.map { words[it] }
        }
    }

    private fun checkWords() {
        for (i in wordInputs.indices) {
            val wordInput = wordInputs[i]
            val word = wordInput.text.trim()
            if (word.isBlank() || wordInput.isFocused) continue
            wordInput.setError(word != words[indexes[i]])
        }
    }

    companion object {
        fun newInstance() = BackupCheckInitScreen()
    }
}
