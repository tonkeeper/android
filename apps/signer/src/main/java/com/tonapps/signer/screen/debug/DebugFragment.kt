package com.tonapps.signer.screen.debug

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.signer.R
import security.Security
import uikit.base.BaseFragment

class DebugFragment: BaseFragment(R.layout.fragment_debug), BaseFragment.SwipeBack {

    companion object {
        fun newInstance() = DebugFragment()
    }

    private lateinit var rootedView: AppCompatTextView
    private lateinit var strongBoxView: AppCompatTextView
    private lateinit var devView: AppCompatTextView
    private lateinit var adbView: AppCompatTextView
    private lateinit var debugView: AppCompatTextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rootedView = view.findViewById(R.id.rooted)
        rootedView.text = wrapValue("Rooted", Security.isDeviceRooted())

        strongBoxView = view.findViewById(R.id.strongbox)
        strongBoxView.text = wrapValue("StrongBox", Security.isSupportStrongBox(requireContext()))

        devView = view.findViewById(R.id.dev)
        devView.text = wrapValue("Dev mode", Security.isDevelopmentEnabled(requireContext()))

        adbView = view.findViewById(R.id.adb)
        adbView.text = wrapValue("ADB enabled", Security.isAdbEnabled(requireContext()))

        debugView = view.findViewById(R.id.debug)
        debugView.text = wrapValue("Debug enabled", Security.isDebuggable(requireContext()))
    }

    private fun wrapValue(title: String, value: Boolean): String {
        return "$title: $value"
    }
}