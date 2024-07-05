package com.tonapps.tonkeeper.ui.screen.token.unverified

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeperx.R
import uikit.base.BaseFragment
import uikit.widget.HeaderView

class TokenUnverifiedScreen: BaseFragment(R.layout.fragment_token_unverified), BaseFragment.Modal {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<HeaderView>(R.id.header).doOnActionClick = { finish() }
        view.findViewById<View>(R.id.button).setOnClickListener { finish() }
    }

    companion object {
        fun newInstance() = TokenUnverifiedScreen()
    }
}