package com.tonapps.tonkeeper.ui.screen.phrase

import android.os.Bundle
import android.view.View
import android.widget.Button
import com.tonapps.tonkeeper.extensions.copyToClipboard
import com.tonapps.tonkeeper.ui.screen.backup.check.BackupCheckScreen
import com.tonapps.tonkeeperx.R
import uikit.base.BaseFragment
import uikit.extensions.pinToBottomInsets
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.HeaderView
import uikit.widget.PhraseWords

class PhraseScreen: BaseFragment(R.layout.fragment_phrase), BaseFragment.SwipeBack {

    private val args: PhraseArgs by lazy { PhraseArgs(requireArguments()) }

    private lateinit var headerView: HeaderView
    private lateinit var wordsView: PhraseWords
    private lateinit var copyButton: Button
    private lateinit var checkButton: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }

        wordsView = view.findViewById(R.id.words)
        wordsView.setWords(args.words)

        copyButton = view.findViewById(R.id.copy)
        copyButton.pinToBottomInsets()
        copyButton.setOnClickListener {
            requireContext().copyToClipboard(args.words.joinToString(" "))
        }

        checkButton = view.findViewById(R.id.check)
        checkButton.pinToBottomInsets()
        checkButton.setOnClickListener {
            navigation?.add(BackupCheckScreen.newInstance(args.words, args.backupId))
            finish()
        }

        if (args.backup) {
            checkButton.visibility = View.VISIBLE
        } else {
            copyButton.visibility = View.VISIBLE
        }
    }

    companion object {

        fun newInstance(
            words: Array<String>,
            backup: Boolean = false,
            backupId: Long = 0
        ): PhraseScreen {
            val fragment = PhraseScreen()
            fragment.setArgs(PhraseArgs(words, backup, backupId))
            return fragment
        }
    }
}