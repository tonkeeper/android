package com.tonapps.tonkeeper.ui.screen.name.edit

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.component.label.LabelEditorView
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.Wallet
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.doKeyboardAnimation
import uikit.widget.HeaderView

class EditNameScreen: BaseWalletScreen(R.layout.fragment_name_edit), BaseFragment.BottomSheet {

    private val walletId: String by lazy { arguments?.getString(ARG_WALLET_ID) ?: "" }

    override val viewModel: EditNameViewModel by viewModel { parametersOf(walletId) }

    private lateinit var editorView: LabelEditorView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val headerView = view.findViewById<HeaderView>(R.id.header)
        headerView.doOnActionClick = { finish() }

        editorView = view.findViewById(R.id.editor)
        editorView.doOnDone = ::saveLabel

        view.doKeyboardAnimation { offset, progress, _ ->
            editorView.setBottomOffset(offset, progress)
        }
        collectFlow(viewModel.uiLabelFlow, ::setLabel)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { editorView.loadEmoji() }
    }

    override fun onPause() {
        viewModel.save(editorView.name, editorView.emoji, editorView.color)
        super.onPause()
    }

    private fun setLabel(label: Wallet.Label) {
        with(editorView) {
            name = label.name
            emoji = label.emoji
            color = label.color
        }
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

        private const val ARG_WALLET_ID = "wallet_id"

        fun newInstance(walletId: String? = null): EditNameScreen {
            val fragment = EditNameScreen()
            fragment.arguments = Bundle().apply {
                putString(ARG_WALLET_ID, walletId)
            }
            return fragment
        }
    }
}