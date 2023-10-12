package com.tonkeeper.fragment.legal

import android.os.Bundle
import android.view.View
import com.tonkeeper.R
import uikit.base.fragment.BaseFragment
import uikit.widget.HeaderView

class LegalFragment: BaseFragment(R.layout.fragment_legal), BaseFragment.SwipeBack {

    companion object {
        fun newInstance() = LegalFragment()
    }

    override var doOnDragging: ((Boolean) -> Unit)? = null
    override var doOnDraggingProgress: ((Float) -> Unit)? = null

    private lateinit var headerView: HeaderView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }
    }

}