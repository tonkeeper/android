package com.tonapps.tonkeeper.ui.screen.name.edit

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.ui.component.label.LabelEditorView
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.Wallet
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.doKeyboardAnimation
import uikit.widget.HeaderView

class EditNameScreen: BaseFragment(R.layout.fragment_name_edit), BaseFragment.BottomSheet {

    private val editNameViewModel: EditNameViewModel by viewModel()

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
        collectFlow(editNameViewModel.uiLabelFlow, ::setLabel)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { editorView.loadEmoji() }
    }

    override fun onPause() {
        editNameViewModel.save(editorView.name, editorView.emoji, editorView.color)
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
        editNameViewModel.save(name, emoji, color)
        finish()
    }

    override fun onDragging() {
        super.onDragging()
        editorView.removeFocus()
    }

    companion object {
        fun newInstance() = EditNameScreen()
    }
}