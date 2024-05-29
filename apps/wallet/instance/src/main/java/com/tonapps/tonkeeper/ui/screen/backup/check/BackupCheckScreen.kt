package com.tonapps.tonkeeper.ui.screen.backup.check

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.pinToBottomInsets
import uikit.widget.HeaderView
import uikit.widget.TextHeaderView
import uikit.widget.WordInput

class BackupCheckScreen: BaseFragment(R.layout.fragment_backup_check), BaseFragment.SwipeBack {

    private val backupCheckViewModel: BackupCheckViewModel by viewModel()

    private val args: BackupCheckArgs by lazy { BackupCheckArgs(requireArguments()) }

    private val indexes: IntArray by lazy {
        val words = args.words
        words.toMutableList().shuffled().subList(0, 3.coerceAtMost(words.size)).map { words.indexOf(it) }.sorted().toIntArray()
    }

    private lateinit var button: Button
    private lateinit var wordInputs: List<WordInput>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<HeaderView>(R.id.header).doOnActionClick = { finish() }

        val textView = view.findViewById<TextHeaderView>(R.id.text)
        textView.desciption = getString(Localization.backup_check_subtitle, indexes[0] + 1, indexes[1] + 1, indexes[2] + 1)

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
            wordInput.doOnFocus = { checkWords() }
        }

        button = view.findViewById(R.id.done)
        button.isEnabled = false
        button.pinToBottomInsets()
        button.setOnClickListener { saveBackup() }

        wordInputs.first().focus(true)
    }

    private fun saveBackup() {
        if (button.isEnabled) {
            backupCheckViewModel.saveBackup(args.backupId).onEach {
                finish()
            }.launchIn(lifecycleScope)
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
            words: Array<String>,
            backupId: Long
        ): BackupCheckScreen {
            val fragment = BackupCheckScreen()
            fragment.setArgs(BackupCheckArgs(words, backupId))
            return fragment
        }

    }

}