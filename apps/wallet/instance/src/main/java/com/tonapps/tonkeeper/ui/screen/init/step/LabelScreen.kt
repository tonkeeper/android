package com.tonapps.tonkeeper.ui.screen.init.step

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.ui.component.label.LabelEditorView
import com.tonapps.tonkeeper.ui.screen.init.InitEvent
import com.tonapps.tonkeeper.ui.screen.init.InitScreen
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.WalletColor
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.doKeyboardAnimation

class LabelScreen: BaseFragment(R.layout.fragment_init_label) {

    private val initViewModel: InitViewModel by viewModel(ownerProducer = { requireParentFragment() })

    private lateinit var editorView: LabelEditorView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        editorView = view.findViewById(R.id.editor)
        editorView.doOnChange = { name, emoji, color ->
            initViewModel.setLabel(name, emoji, color)
        }
        editorView.doOnDone = { name, emoji, color ->
            initViewModel.setLabel(name, emoji, color)
            initViewModel.nextStep(InitEvent.Step.LabelAccount)
        }

        view.doKeyboardAnimation { offset, progress, _ ->
            editorView.setBottomOffset(offset, progress)
        }

        collectFlow(initViewModel.uiTopOffset) {
            view.updatePadding(top = it)
        }

        val label = initViewModel.getLabel()
        with(editorView) {
            name = label.name
            emoji = label.emoji
            color = label.color
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { editorView.loadEmoji() }
    }

    companion object {
        fun newInstance() = LabelScreen()
    }
}