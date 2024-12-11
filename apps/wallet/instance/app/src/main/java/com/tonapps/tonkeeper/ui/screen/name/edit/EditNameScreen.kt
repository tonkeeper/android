package com.tonapps.tonkeeper.ui.screen.name.edit

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeper.ui.component.label.LabelEditorView
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.entities.WalletEntity
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.doKeyboardAnimation
import uikit.widget.HeaderView

class EditNameScreen(wallet: WalletEntity): WalletContextScreen(R.layout.fragment_name_edit, wallet), BaseFragment.BottomSheet {

    override val fragmentName: String = "EditNameScreen"

    override val viewModel: EditNameViewModel by walletViewModel()

    private lateinit var editorView: LabelEditorView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val headerView = view.findViewById<HeaderView>(R.id.header)
        headerView.doOnActionClick = { finish() }

        editorView = view.findViewById(R.id.editor)
        editorView.doOnDone = ::saveLabel
        editorView.name = screenContext.wallet.label.name
        editorView.emoji = screenContext.wallet.label.emoji
        editorView.color = screenContext.wallet.label.color

        view.doKeyboardAnimation { offset, progress, _ ->
            editorView.setBottomOffset(offset, progress)
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { editorView.loadEmoji() }
    }

    override fun onPause() {
        viewModel.save(editorView.name, editorView.emoji, editorView.color)
        super.onPause()
    }

    private fun saveLabel(name: String, emoji: String, color: Int) {
        viewModel.save(name, emoji, color)
        finish()
    }

    override fun onDragging() {
        super.onDragging()
        editorView.removeFocus()
    }

    companion object {

        fun newInstance(wallet: WalletEntity) = EditNameScreen(wallet)
    }
}