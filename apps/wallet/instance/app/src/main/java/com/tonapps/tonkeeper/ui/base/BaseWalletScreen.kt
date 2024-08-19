package com.tonapps.tonkeeper.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import uikit.base.BaseFragment
import uikit.navigation.Navigation

abstract class BaseWalletScreen(@LayoutRes layoutId: Int): BaseFragment(layoutId) {

    abstract val viewModel: BaseWalletVM

    val navigation: Navigation?
        get() = context?.let { Navigation.from(it) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        activity?.let { viewModel.attachActivity(it) }
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.detachActivity()
    }
}