package com.tonapps.tonkeeper.ui.screen.init.step

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.extensions.clipboardText
import com.tonapps.tonkeeper.ui.screen.init.InitEvent
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import com.tonapps.tonkeeperx.R
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.pinToBottomInsets
import uikit.widget.InputView

class WatchScreen: BaseFragment(R.layout.fragment_init_watch) {

    private val initViewModel: InitViewModel by viewModel(ownerProducer = { requireParentFragment() })

    private lateinit var contentView: View
    private lateinit var inputView: InputView
    private lateinit var button: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        contentView = view.findViewById(R.id.content)
        inputView = view.findViewById(R.id.input)
        inputView.inputType = EditorInfo.TYPE_CLASS_TEXT
        inputView.doOnButtonClick = { paste() }
        inputView.setOnDoneActionListener { next() }

        button = view.findViewById(R.id.button)
        button.setOnClickListener { next() }
        button.pinToBottomInsets()

        collectFlow(initViewModel.uiTopOffset) {
            contentView.updatePadding(top = it)
        }

        initViewModel.watchAccountFlow.onEach { account ->
            inputView.loading = false
            if (account == null) {
                inputView.error = true
                return@onEach
            }
            button.isEnabled = true
            inputView.error = false
        }.launchIn(lifecycleScope)

        inputView.doOnTextChange = ::onTextChanged
        inputView.text = initViewModel.getWatchAccount()?.query ?: ""
    }

    private fun onTextChanged(query: String) {
        val lastQuery = initViewModel.getWatchAccount()?.query
        if (lastQuery == query) {
            button.isEnabled = true
            inputView.loading = false
            inputView.error = false
            return
        }

        button.isEnabled = false
        inputView.loading = query.isNotEmpty()
        inputView.error = false
        initViewModel.resolveWatchAccount(query)
    }

    private fun next() {
        if (button.isEnabled) {
            inputView.hideKeyboard()
            initViewModel.nextStep(InitEvent.Step.WatchAccount)
        }
    }

    private fun paste() {
        val text = context?.clipboardText()
        if (!text.isNullOrEmpty()) {
            inputView.text = text
        }
    }

    override fun onResume() {
        super.onResume()
        inputView.focus()
    }

    companion object {
        fun newInstance() = WatchScreen()
    }
}