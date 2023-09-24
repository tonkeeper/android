package com.tonkeeper.uikit.base.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.tonkeeper.uikit.R
import com.tonkeeper.uikit.base.fragment.BaseFragment
import com.tonkeeper.uikit.widget.BackHeaderView

open class WithBackFragment(
    private val layoutId: Int
): BaseFragment(R.layout.fragment_with_back) {

    lateinit var headerView: BackHeaderView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)!!
        val contentView = view.findViewById<FrameLayout>(R.id.content)
        inflater.inflate(layoutId, contentView, true)
        headerView = view.findViewById(R.id.header)
        headerView.doOnBackClick = {
            activity?.onBackPressed()
        }
        return view
    }

}