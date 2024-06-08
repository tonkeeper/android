package com.tonapps.tonkeeper.ui.screen.settings.legal

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeper.ui.screen.web.WebScreen
import com.tonapps.tonkeeperx.R
import uikit.base.BaseFragment
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.HeaderView

class LegalScreen: BaseFragment(R.layout.fragment_legal), BaseFragment.SwipeBack {

    private lateinit var headerView: HeaderView
    private lateinit var termsView: View
    private lateinit var privacyView: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }

        termsView = view.findViewById(R.id.terms)
        termsView.setOnClickListener {
            navigation?.add(WebScreen.newInstance("https://tonkeeper.com/terms/"))
        }

        privacyView = view.findViewById(R.id.privacy)
        privacyView.setOnClickListener {
            navigation?.add(WebScreen.newInstance("https://tonkeeper.com/privacy/"))
        }
    }

    companion object {
        fun newInstance() = LegalScreen()
    }
}