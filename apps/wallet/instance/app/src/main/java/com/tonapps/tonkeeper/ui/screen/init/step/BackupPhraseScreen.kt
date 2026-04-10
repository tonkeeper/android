package com.tonapps.tonkeeper.ui.screen.init.step

import android.content.res.Configuration
import android.os.Bundle
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import com.tonapps.tonkeeperx.BuildConfig
import com.tonapps.tonkeeperx.R
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.getDimensionPixelSize
import uikit.widget.PhraseWords
import uikit.widget.TextHeaderView

class BackupPhraseScreen : BaseFragment(R.layout.fragment_backup_phrase) {

    override val fragmentName: String = "BackupPhraseScreen"

    override val secure: Boolean = !BuildConfig.DEBUG

    private val initViewModel: InitViewModel by viewModel(ownerProducer = { requireParentFragment() })

    private lateinit var textHeaderView: TextHeaderView

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

        textHeaderView = view.findViewById(R.id.text_header)
        val wordsView = view.findViewById<PhraseWords>(R.id.words)
        val mnemonic = initViewModel.getMnemonic()
        if (mnemonic != null) {
            wordsView.setWords(mnemonic)
        }

        val button = view.findViewById<Button>(R.id.button)
        button.setOnClickListener { initViewModel.navigateToBackupCheck() }

        val offsetLarge = requireContext().getDimensionPixelSize(uikit.R.dimen.offsetLarge)

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            button.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = systemBarInsets.bottom + offsetLarge
            }
            insets
        }

        applyFontScaleFix(view, requireContext().resources.configuration.fontScale)

        if (wordsView.isSmallScreen) {
            textHeaderView.descriptionView.visibility = View.GONE
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
        fun newInstance() = BackupPhraseScreen()
    }
}
