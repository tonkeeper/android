package com.tonapps.tonkeeper.ui.screen.staking.main

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentOnAttachListener
import com.tonapps.tonkeeper.ui.screen.staking.StakingViewModel
import com.tonapps.uikit.color.backgroundPageColor
import org.koin.androidx.viewmodel.ext.android.viewModel

abstract class StakeChildFragment(layoutId: Int): Fragment(layoutId) {

    val stakeViewModel: StakingViewModel by viewModel(ownerProducer = { requireParentFragment() })

    val rootScreen: StakeScreen?
        get() = parentFragment as? StakeScreen

    val rootFragmentManager: FragmentManager?
        get() = rootScreen?.childFragmentManager

    var visibleState: Boolean = false
        set(value) {
            if (value != field) {
                field = value
                onVisibleState(value)
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setOnClickListener {  }
        view.setBackgroundColor(requireContext().backgroundPageColor)
        view.postOnAnimation { visibleState = true }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        visibleState = false
    }

    open fun onKeyboardAnimation(offset: Int, progress: Float, isShowing: Boolean) {

    }

    abstract fun getTitle(): String

    open fun onVisibleState(visible: Boolean) {

    }

    fun finish() {
        rootScreen?.backFragment()
    }
}