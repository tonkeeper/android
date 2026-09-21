package com.tonapps.tonkeeper.ui.screen.name.edit

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.component.label.LabelEditorView
import com.tonapps.tonkeeperx.R
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.doKeyboardAnimation
import uikit.widget.HeaderView

class EditNameScreen : BaseWalletScreen<ScreenContext.None>(R.layout.fragment_name_edit, ScreenContext.None),
    BaseFragment.BottomSheet {

    override val fragmentName: String = "EditNameScreen"

    override val viewModel: EditNameViewModel by viewModel {
        parametersOf(arguments?.getString(ARG_WALLET_ID))
    }

    private lateinit var editorView: LabelEditorView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val headerView = view.findViewById<HeaderView>(R.id.header)
        headerView.doOnActionClick = { finish() }

        editorView = view.findViewById(R.id.editor)
        editorView.doOnDone = ::saveLabel

        collectFlow(viewModel.labelFlow) { label ->
            editorView.name = label.name
            editorView.emoji = label.emoji
            editorView.color = label.color
        }

        view.doKeyboardAnimation { offset, progress, showKeyboard ->
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

        private const val ARG_WALLET_ID = "wallet_id"

        fun newInstance(walletId: String? = null): EditNameScreen {
            val fragment = EditNameScreen()
            walletId?.let { fragment.putStringArg(ARG_WALLET_ID, it) }
            return fragment
        }
    }
}
