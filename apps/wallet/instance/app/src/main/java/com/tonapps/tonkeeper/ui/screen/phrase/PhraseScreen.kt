package com.tonapps.tonkeeper.ui.screen.phrase

import android.content.res.Configuration
import android.os.Bundle
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
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
import com.tonapps.blockchain.model.legacy.WalletEntity
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val config =
            Configuration(requireContext().resources.configuration).apply { fontScale = 1f }
        val configContext = requireContext().createConfigurationContext(config)
        val themedContext = ContextThemeWrapper(configContext, requireContext().theme)
        val noScaleInflater = inflater.cloneInContext(themedContext)
        return super.onCreateView(noScaleInflater, container, savedInstanceState)
    }

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

        applyFontScaleFix(view, requireContext().resources.configuration.fontScale)

        if (wordsView.isSmallScreen) {
            textHeaderView.descriptionView.visibility = View.GONE
        }

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

    private fun applyFontScaleFix(view: View, fontScale: Float) {
        if (fontScale <= 1f) return
        if (view is TextView) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, view.textSize / fontScale)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyFontScaleFix(view.getChildAt(i), fontScale)
            }
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
