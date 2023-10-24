package com.tonkeeper.fragment.nft

import android.os.Bundle
import android.view.View
import com.tonkeeper.R
import uikit.base.fragment.BaseFragment
import uikit.widget.LoaderView

class NFTFragment: BaseFragment(R.layout.fragment_nft), BaseFragment.BottomSheet {

    companion object {
        fun newInstance() = NFTFragment()
    }

    private lateinit var loaderView: LoaderView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loaderView = view.findViewById(R.id.loader)
        loaderView.resetAnimation()
    }
}