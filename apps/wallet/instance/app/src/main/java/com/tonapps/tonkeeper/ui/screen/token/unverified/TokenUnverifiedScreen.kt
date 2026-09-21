package com.tonapps.tonkeeper.ui.screen.token.unverified

import android.os.Bundle
import android.view.View
import androidx.core.view.marginBottom
import com.tonapps.tonkeeperx.R
import uikit.base.BaseFragment
import uikit.extensions.applyNavBottomMargin
import uikit.widget.HeaderView

class TokenUnverifiedScreen: BaseFragment(R.layout.fragment_token_unverified), BaseFragment.Modal {

    override val fragmentName: String = "UnverifiedTokenScreen"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<HeaderView>(R.id.header).doOnActionClick = { finish() }
        view.findViewById<View>(R.id.button).apply {
            applyNavBottomMargin(marginBottom)
            setOnClickListener { finish() }
        }
    }

    companion object {
        fun newInstance() = TokenUnverifiedScreen()
    }
}