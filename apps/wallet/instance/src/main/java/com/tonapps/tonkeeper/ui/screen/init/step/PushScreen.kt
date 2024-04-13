package com.tonapps.tonkeeper.ui.screen.init.step

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import com.tonapps.tonkeeperx.R
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.pinToBottomInsets

class PushScreen: BaseFragment(R.layout.fragment_init_push)  {

    private val initViewModel: InitViewModel by viewModel(ownerProducer = { requireParentFragment() })

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        initViewModel.setPush()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val button = view.findViewById<Button>(R.id.button)
        button.setOnClickListener { requestPermission() }
        button.pinToBottomInsets()
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            initViewModel.setPush()
        }
    }

    companion object {
        fun newInstance() = PushScreen()
    }
}