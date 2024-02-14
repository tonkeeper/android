package com.tonapps.tonkeeper.ui.screen.name

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.event.ChangeWalletLabelEvent
import com.tonapps.tonkeeper.ui.screen.name.base.NameFragment
import com.tonapps.tonkeeper.ui.screen.name.base.NameMode
import com.tonapps.tonkeeper.ui.screen.name.base.NameModeEdit
import com.tonapps.tonkeeperx.R
import core.EventBus
import kotlinx.coroutines.launch
import uikit.base.BaseFragment
import uikit.widget.HeaderView

class RenameFragment(mode: NameMode): NameFragment(mode), BaseFragment.BottomSheet {

    companion object {
        fun newInstance() = RenameFragment(NameModeEdit)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val headerView = view.findViewById<HeaderView>(R.id.header)
        headerView.visibility = View.VISIBLE
        headerView.doOnActionClick = { finish() }
    }

    override fun onResume() {
        super.onResume()
        focus(true)
    }

    override fun onDragging() {
        super.onDragging()
        stopScroll()
    }

    override fun onData(name: String, emoji: CharSequence, color: Int) {
        lifecycleScope.launch {
            val wallet = App.walletManager.getWalletInfo() ?: return@launch
            App.walletManager.edit(wallet.id, name, emoji, color)
            EventBus.post(ChangeWalletLabelEvent(wallet.address))
            finish()
        }
    }
}