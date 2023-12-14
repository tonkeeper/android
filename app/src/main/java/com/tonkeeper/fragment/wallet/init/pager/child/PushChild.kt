package com.tonkeeper.fragment.wallet.init.pager.child

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.viewModels
import com.tonkeeper.R
import com.tonkeeper.fragment.wallet.init.InitModel
import uikit.base.BaseFragment
import uikit.widget.HeaderView

class PushChild: BaseFragment(R.layout.fragment_push) {

    companion object {
        fun newInstance() = PushChild()
    }

    private val feature: InitModel by viewModels({ requireParentFragment() })

    private val requestPermissionLauncher = registerForPermission{ isGranted: Boolean ->
        if (isGranted) {
            feature.setPushPermission(true)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val button = view.findViewById<Button>(R.id.button)
        button.setOnClickListener {
            requestPushPermission()
        }
    }

    private fun requestPushPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            feature.setPushPermission(true)
        }
    }

}