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
        val offsetLarge = requireContext().getDimensionPixelSize(uikit.R.dimen.offsetLarge)

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars() + WindowInsetsCompat.Type.navigationBars())
            laterView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = systemBarInsets.top + offsetMedium
            }

            button.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = systemBarInsets.bottom + offsetLarge
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
