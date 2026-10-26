package com.tonapps.tonkeeper.ui.screen.dns.renew

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeperx.R
import uikit.base.BaseFragment
import uikit.extensions.applyNavBottomPadding
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.setMoonOnClickListener

class DNSOnSaleScreen: BaseFragment(R.layout.dialog_dns_onsale), BaseFragment.Modal {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.close).setMoonOnClickListener { finish() }
        view.findViewById<View>(R.id.ton).setMoonOnClickListener { finish() }

        view.findViewById<View>(R.id.container)
            .applyNavBottomPadding(requireContext().getDimensionPixelSize(uikit.R.dimen.offsetMedium))
    }

    companion object {

        fun newInstance() = DNSOnSaleScreen()
    }
}