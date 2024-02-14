package com.tonapps.tonkeeper.ui.screen.init.pager.child.watch

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.viewModels
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.extensions.clipboardText
import com.tonapps.tonkeeper.extensions.launch
import com.tonapps.tonkeeper.ui.screen.init.InitViewModel
import org.koin.androidx.viewmodel.ext.android.getViewModel
import uikit.base.BaseFragment
import uikit.mvi.AsyncState
import uikit.widget.InputView
import uikit.widget.LoaderView

class WatchChild: BaseFragment(R.layout.fragment_watch) {

    companion object {
        fun newInstance() = WatchChild()
    }

    private val parentFeature: InitViewModel by lazy {
        requireParentFragment().getViewModel()
    }

    private val viewModel: WatchViewModel by viewModels()

    private lateinit var addressInput: InputView
    private lateinit var saveButton: Button
    private lateinit var loaderView: LoaderView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        addressInput = view.findViewById(R.id.watch_address)
        addressInput.doOnTextChange = { viewModel.checkAddress(it) }
        addressInput.doOnButtonClick = { paste() }

        saveButton = view.findViewById(R.id.watch_save)
        saveButton.setOnClickListener { save() }

        loaderView = view.findViewById(R.id.watch_loading)

        viewModel.inputState.launch(this) {
            addressInput.loading = it == AsyncState.Loading
            addressInput.error = it == AsyncState.Error
            saveButton.isEnabled = it == AsyncState.Success
        }
    }

    private fun paste() {
        val text = context?.clipboardText()
        if (!text.isNullOrEmpty()) {
            addressInput.text = text
        }
    }

    private fun save() {
        val accountId = addressInput.text
        if (accountId.isEmpty()) {
            return
        }

        parentFeature.setWatchAccountId(accountId)
    }

    override fun onResume() {
        super.onResume()
        addressInput.focus()
    }
}