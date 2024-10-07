package com.tonapps.tonkeeper.ui.screen.backup.check

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.doKeyboardAnimation
import uikit.extensions.pinToBottomInsets
import uikit.extensions.scrollDown
import uikit.extensions.scrollView
import uikit.widget.HeaderView
import uikit.widget.TextHeaderView
import uikit.widget.WordInput

class BackupCheckScreen(wallet: WalletEntity): WalletContextScreen(R.layout.fragment_backup_check, wallet), BaseFragment.SwipeBack {

    private val args: BackupCheckArgs by lazy { BackupCheckArgs(requireArguments()) }

    override val secure: Boolean = true

    override val viewModel: BackupCheckViewModel by walletViewModel()

    private val indexes: IntArray by lazy {
        val words = args.words
        words.toMutableList().shuffled().subList(0, 3.coerceAtMost(words.size)).map { words.indexOf(it) }.sorted().toIntArray()
    }

    private lateinit var button: Button
    private lateinit var wordInputs: List<WordInput>
    private lateinit var scrollView: NestedScrollView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<HeaderView>(R.id.header).doOnCloseClick = { finish() }

        val textView = view.findViewById<TextHeaderView>(R.id.text)
        textView.desciption = getString(Localization.backup_check_subtitle, indexes[0] + 1, indexes[1] + 1, indexes[2] + 1)

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
                if (hasNext) {
                    wordInputs[i + 1].focus(true)
                } else {
                    saveBackup()
                }
            }
            wordInput.doOnPrev = {
                if (hasPrev) {
                    wordInputs[i - 1].focus(false)
                }
            }
            wordInput.doOnTextChanged = { checkEnableButton() }
            wordInput.doOnFocus = { focus ->
                if (focus) {
                    updateScroll(wordInput)
                }
                checkWords()
            }
        }

        button = view.findViewById(R.id.done)
        button.isEnabled = false
        button.setOnClickListener { saveBackup() }

        scrollView.doKeyboardAnimation { offset, progress, _ ->
            scrollView.updatePadding(bottom = offset)
            button.translationY = -offset.toFloat()
            if (progress >= .9f || .1f >= progress) {
                getCurrentFocus()?.let { updateScroll(it) }
            }
        }

        wordInputs.first().focus(true)
    }

    private fun updateScroll(view: View) {
        scrollView.postOnAnimation {
            scrollView.scrollView(view)
        }
    }

    private fun saveBackup() {
        if (button.isEnabled) {
            viewModel.saveBackup(args.backupId) { finish() }
        }
    }

    private fun checkEnableButton() {
        val inputWords = wordInputs.map { it.text.trim() }.filter { it.isNotEmpty() }
        if (inputWords.size != indexes.size) {
            button.isEnabled = false
        } else {
            button.isEnabled = inputWords == indexes.map { args.words[it] }
        }
    }

    private fun checkWords() {
        for (i in wordInputs.indices) {
            val wordInput = wordInputs[i]
            val word = wordInput.text.trim()
            if (word.isBlank() || wordInput.isFocused) {
                continue
            }
            wordInput.setError(word != args.words[indexes[i]])
        }
    }

    companion object {

        fun newInstance(
            wallet: WalletEntity,
            words: Array<String>,
            backupId: Long
        ): BackupCheckScreen {
            val fragment = BackupCheckScreen(wallet)
            fragment.setArgs(BackupCheckArgs(words, backupId))
            return fragment
        }

    }

}