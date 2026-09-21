package com.tonapps.tonkeeper.ui.screen.init.step

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import com.tonapps.tonkeeperx.R
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.getDimensionPixelSize

class PushScreen: BaseFragment(R.layout.fragment_notifications_enable) {

    override val fragmentName: String = "PushScreen"

    private val initViewModel: InitViewModel by viewModel(ownerProducer = { requireParentFragment() })

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            enablePush()
        } else {
            disablePush()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val laterView = view.findViewById<View>(R.id.later)
        laterView.setOnClickListener { disablePush() }

        val button = view.findViewById<Button>(R.id.button)
        button.setOnClickListener { requestPermission() }

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

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            initViewModel.onPushPermissionRequested()
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            enablePush()
        }
    }

    private fun enablePush() {
        initViewModel.enablePush(true)
    }

    private fun disablePush() {
        initViewModel.enablePush(false)
    }

    companion object {
        fun newInstance() = PushScreen()
    }

}