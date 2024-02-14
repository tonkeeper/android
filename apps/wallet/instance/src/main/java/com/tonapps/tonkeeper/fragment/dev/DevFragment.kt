package com.tonapps.tonkeeper.fragment.dev

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeperx.R
import uikit.base.BaseFragment

class DevFragment: BaseFragment(R.layout.fragment_dev), BaseFragment.SwipeBack {

    companion object {
        fun newInstance() = DevFragment()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

    }
}