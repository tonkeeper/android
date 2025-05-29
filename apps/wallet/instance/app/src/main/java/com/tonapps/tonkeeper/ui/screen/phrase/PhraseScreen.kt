package com.tonapps.tonkeeper.ui.screen.phrase

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.extensions.copyToClipboard
import com.tonapps.tonkeeper.koin.accountRepository
import com.tonapps.tonkeeper.koin.settingsRepository
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeper.ui.screen.backup.check.BackupCheckScreen
import com.tonapps.tonkeeperx.BuildConfig
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.doKeyboardAnimation
import uikit.widget.HeaderView
import uikit.widget.PhraseWords
import uikit.widget.TextHeaderView

class PhraseScreen(wallet: WalletEntity): WalletContextScreen(R.layout.fragment_phrase, wallet), BaseFragment.SwipeBack {

    override val fragmentName: String = "PhraseScreen"

    private val args: PhraseArgs by lazy { PhraseArgs(requireArguments()) }

    override val viewModel: BaseWalletVM.EmptyViewViewModel by viewModel()

    override val secure: Boolean = !BuildConfig.DEBUG

    private lateinit var headerView: HeaderView
    private lateinit var textHeaderView: TextHeaderView
    private lateinit var tronWarningView: AppCompatTextView
    private lateinit var wordsView: PhraseWords
    private lateinit var copyButton: Button
    private lateinit var tronButton: Button
    private lateinit var checkButton: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }

        textHeaderView = view.findViewById(R.id.text_header)

        tronWarningView = view.findViewById(R.id.tron_warning)

        if (args.isTron) {
            textHeaderView.title = getString(Localization.phrase_title_tron)
            textHeaderView.desciption = getString(Localization.phrase_description_tron)
            tronWarningView.visibility = View.VISIBLE
        }

        wordsView = view.findViewById(R.id.words)
        wordsView.setWords(args.words)

        copyButton = view.findViewById(R.id.copy)
        copyButton.setOnClickListener {
            requireContext().copyToClipboard(args.words.joinToString(" "), true)
        }

        tronButton = view.findViewById(R.id.tron)
        tronButton.setOnClickListener {
            lifecycleScope.launch {
                val tronWords = requireContext().accountRepository?.getTronMnemonic(wallet.id) ?: return@launch
                navigation?.add(newInstance(wallet, tronWords, isTron = true))
            }
        }

        checkButton = view.findViewById(R.id.check)
        checkButton.setOnClickListener {
            navigation?.add(BackupCheckScreen.newInstance(wallet, args.words, args.backupId))
            finish()
        }

        if (args.backup) {
            checkButton.visibility = View.VISIBLE
        } else {
            copyButton.visibility = View.VISIBLE
        }

        val tronUsdtEnabled = context?.settingsRepository?.getTronUsdtEnabled(wallet.id) ?: false

        if (tronUsdtEnabled && !args.isTron && !args.backup) {
            tronButton.visibility = View.VISIBLE
        }

        view.doKeyboardAnimation { offset, _, _ ->
            checkButton.translationY = -offset.toFloat()
        }
    }

    companion object {

        fun newInstance(
            wallet: WalletEntity,
            words: Array<String>,
            backup: Boolean = false,
            backupId: Long = 0,
            isTron: Boolean = false,
        ): PhraseScreen {
            val fragment = PhraseScreen(wallet)
            fragment.setArgs(PhraseArgs(words, backup, backupId, isTron))
            return fragment
        }
    }
}