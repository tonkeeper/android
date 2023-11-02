package com.tonkeeper.fragment.settings.security

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.tonkeeper.App
import com.tonkeeper.R
import uikit.base.fragment.BaseFragment
import uikit.widget.HeaderView
import uikit.widget.item.ItemSwitchView

class SecurityFragment : BaseFragment(R.layout.fragment_security), BaseFragment.SwipeBack {

    companion object {
        fun newInstance() = SecurityFragment()
    }

    override var doOnDragging: ((Boolean) -> Unit)? = null
    override var doOnDraggingProgress: ((Float) -> Unit)? = null

    private lateinit var headerView: HeaderView
    private lateinit var biometricView: ItemSwitchView
    private lateinit var lockScreenView: ItemSwitchView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }

        biometricView = view.findViewById(R.id.biometric)
        biometricView.checked = App.settings.biometric
        biometricView.doOnCheckedChanged = { App.settings.biometric = it }

        lockScreenView = view.findViewById(R.id.lock_screen)
        lockScreenView.checked = App.settings.lockScreen
        lockScreenView.doOnCheckedChanged = { App.settings.lockScreen = it }

    }
}