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
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowSource
import com.tonapps.tonkeeper.extensions.clearClipboard
import com.tonapps.tonkeeper.extensions.clipboardText
import com.tonapps.tonkeeper.extensions.copyToClipboard
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.koin.accountRepository
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.backup.check.BackupCheckScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.doKeyboardAnimation
import uikit.widget.HeaderView
import uikit.widget.PhraseWords
import uikit.widget.TextHeaderView
import uikit.widget.ToastView

class PhraseScreen : BaseWalletScreen<ScreenContext.None>(R.layout.fragment_phrase, ScreenContext.None), BaseFragment.SwipeBack {

    override val fragmentName: String = "PhraseScreen"

    private val args: PhraseArgs by lazy { PhraseArgs(requireArguments()) }

    override val viewModel: PhraseViewModel by viewModel()

    override val detectScreenCapture: Boolean = true

    private lateinit var headerView: HeaderView
    private lateinit var textHeaderView: TextHeaderView
    private lateinit var tronWarningView: AppCompatTextView
    private lateinit var wordsView: PhraseWords
    private lateinit var copyButton: Button
    private lateinit var tronButton: Button
    private lateinit var checkButton: Button

    private var copiedPhrase = false

    override fun onScreenCaptured() {
        navigation?.toast(Localization.screenshot_warning, ToastView.DURATION_LONG)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        copiedPhrase = savedInstanceState?.getBoolean(STATE_COPIED_PHRASE) == true
    }

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
            copiedPhrase = true
        }

        tronButton = view.findViewById(R.id.tron)
        tronButton.setOnClickListener {
            lifecycleScope.launch {
                val legacyId = viewModel.legacyWalletIdOrNull() ?: return@launch
                val tronWords = requireContext().accountRepository?.getTronMnemonic(legacyId) ?: return@launch
                navigation?.add(newInstance(tronWords, isTron = true))
            }
        }

        checkButton = view.findViewById(R.id.check)
        checkButton.setOnClickListener {
            navigation?.add(BackupCheckScreen.newInstance(args.words, args.backupId, args.source))
            finish()
        }

        if (args.backup) {
            checkButton.visibility = View.VISIBLE
        } else {
            copyButton.visibility = View.VISIBLE
        }

        if (!args.isTron && !args.backup) {
            lifecycleScope.launch {
                if (viewModel.legacyWalletIdOrNull() != null) {
                    tronButton.visibility = View.VISIBLE
                }
            }
        }

        view.doKeyboardAnimation { offset, _, _ ->
            checkButton.translationY = -offset.toFloat()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_COPIED_PHRASE, copiedPhrase)
    }

    override fun onDestroy() {
        val host = activity
        if (host != null &&
            !host.isChangingConfigurations &&
            copiedPhrase &&
            host.clipboardText() == args.words.joinToString(" ")
        ) {
            host.clearClipboard()
        }
        super.onDestroy()
    }

    private fun applyFontScaleFix(view: View, fontScale: Float) {
        if (fontScale <= 1f) {
            return
        }
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

        private const val STATE_COPIED_PHRASE = "copied_phrase"

        fun newInstance(
            words: Array<String>,
            backup: Boolean = false,
            backupId: Long = 0,
            isTron: Boolean = false,
            source: WalletFlowSource = WalletFlowSource.Settings,
        ): PhraseScreen {
            val fragment = PhraseScreen()
            fragment.setArgs(PhraseArgs(words, backup, backupId, isTron, source))
            return fragment
        }
    }
}
