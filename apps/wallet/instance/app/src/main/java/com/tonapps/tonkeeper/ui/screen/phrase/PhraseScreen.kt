package com.tonapps.tonkeeper.ui.screen.phrase

import android.os.Bundle
import android.view.View
import android.widget.Button
import com.tonapps.tonkeeper.extensions.copyToClipboard
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeper.ui.screen.backup.check.BackupCheckScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.entities.WalletEntity
import uikit.base.BaseFragment
import uikit.extensions.pinToBottomInsets
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.extensions.doKeyboardAnimation
import uikit.widget.HeaderView
import uikit.widget.PhraseWords

class PhraseScreen(wallet: WalletEntity): WalletContextScreen(R.layout.fragment_phrase, wallet), BaseFragment.SwipeBack {

    private val args: PhraseArgs by lazy { PhraseArgs(requireArguments()) }

    override val viewModel: BaseWalletVM.EmptyViewViewModel by viewModel()

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
        copyButton.setOnClickListener {
            requireContext().copyToClipboard(args.words.joinToString(" "))
        }

        checkButton = view.findViewById(R.id.check)
        checkButton.setOnClickListener {
            navigation?.add(BackupCheckScreen.newInstance(screenContext.wallet, args.words, args.backupId))
            finish()
        }

        if (args.backup) {
            checkButton.visibility = View.VISIBLE
        } else {
            copyButton.visibility = View.VISIBLE
        }

        view.doKeyboardAnimation { offset, _, _ ->
            copyButton.translationY = -offset.toFloat()
            checkButton.translationY = -offset.toFloat()
        }
    }

    companion object {

        fun newInstance(
            wallet: WalletEntity,
            words: Array<String>,
            backup: Boolean = false,
            backupId: Long = 0
        ): PhraseScreen {
            val fragment = PhraseScreen(wallet)
            fragment.setArgs(PhraseArgs(words, backup, backupId))
            return fragment
        }
    }
}