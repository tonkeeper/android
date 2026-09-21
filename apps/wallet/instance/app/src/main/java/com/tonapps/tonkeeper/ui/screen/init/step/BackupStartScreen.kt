package com.tonapps.tonkeeper.ui.screen.init.step

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import com.tonapps.tonkeeperx.R
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.getDimensionPixelSize

class BackupStartScreen: BaseFragment(R.layout.fragment_backup_start) {

    override val fragmentName: String = "BackupStartScreen"

    private val initViewModel: InitViewModel by viewModel(ownerProducer = { requireParentFragment() })

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val laterView = view.findViewById<View>(R.id.later)
        laterView.setOnClickListener { skip() }

        val button = view.findViewById<Button>(R.id.button)
        button.setOnClickListener { startBackup() }

        val offsetMedium = requireContext().getDimensionPixelSize(uikit.R.dimen.offsetMedium)

        // The screen lives inside a bottom sheet that already starts below the status bar, so the
        // skip button keeps its XML margin to stay within the top bar row; only the bottom button
        // still needs the navigation-bar inset.
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            button.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = navBottom + offsetMedium
            }
            insets
        }
    }

    private fun startBackup() {
        initViewModel.navigateToBackupPhrase()
    }

    private fun skip() {
        initViewModel.skipBackup()
    }

    companion object {
        fun newInstance() = BackupStartScreen()
    }

}
