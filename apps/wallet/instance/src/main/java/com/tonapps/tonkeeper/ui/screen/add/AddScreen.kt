package com.tonapps.tonkeeper.ui.screen.add

import android.os.Bundle
import android.view.View
import androidx.core.widget.NestedScrollView
import com.tonapps.tonkeeper.fragment.signer.add.SignerAddFragment
import com.tonapps.tonkeeper.ui.screen.init.InitAction
import com.tonapps.tonkeeper.ui.screen.init.InitFragment
import com.tonapps.tonkeeperx.R
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.topScrolled
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.ModalHeader

class AddScreen: BaseFragment(R.layout.fragment_add_wallet), BaseFragment.Modal {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val headerView = view.findViewById<ModalHeader>(R.id.header)
        headerView.onCloseClick = { finish() }

        val scrollView = view.findViewById<NestedScrollView>(R.id.scroll)
        collectFlow(scrollView.topScrolled, headerView::setDivider)

        openByClick(R.id.new_wallet, InitAction.Create)
        openByClick(R.id.import_wallet, InitAction.Import)
        openByClick(R.id.watch_wallet, InitAction.Watch)
        openByClick(R.id.testnet_wallet, InitAction.Testnet)

        view.findViewById<View>(R.id.signer_wallet).setOnClickListener {
            navigation?.add(SignerAddFragment.newInstance())
        }
    }

    private fun openByClick(id: Int, action: InitAction) {
        view?.findViewById<View>(id)?.setOnClickListener {
            navigation?.add(InitFragment.newInstance(action))
        }
    }

    companion object {
        fun newInstance() = AddScreen()
    }
}