package com.tonapps.tonkeeper.ui.screen.notifications.enable

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
import com.tonapps.tonkeeperx.R
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.getDimensionPixelSize

class NotificationsEnableScreen: BaseFragment(R.layout.fragment_notifications_enable) {

    private val notificationsEnableViewModel: NotificationsEnableViewModel by viewModel()

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            enablePush()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val laterView = view.findViewById<View>(R.id.later)
        laterView.setOnClickListener { finish() }

        val button = view.findViewById<Button>(R.id.button)
        button.setOnClickListener { requestPermission() }

        val offsetMedium = requireContext().getDimensionPixelSize(uikit.R.dimen.offsetMedium)

        ViewCompat.setOnApplyWindowInsetsListener(view) {  _, insets ->
            val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars() + WindowInsetsCompat.Type.navigationBars())
            laterView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = systemBarInsets.top + offsetMedium
            }

            button.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = systemBarInsets.bottom + offsetMedium
            }
            insets
        }
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            enablePush()
        }
    }

    private fun enablePush() {
        collectFlow(notificationsEnableViewModel.enablePush()) {
            finish()
        }
    }

    companion object {
        fun newInstance() = NotificationsEnableScreen()
    }
}