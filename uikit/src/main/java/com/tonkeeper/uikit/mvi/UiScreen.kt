package com.tonkeeper.uikit.mvi

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tonkeeper.uikit.base.fragment.BaseFragment
import kotlinx.coroutines.launch

abstract class UiScreen<S: UiState, VM: UiFeature<S>>(
    layoutRes: Int
): BaseFragment(layoutRes) {

    abstract val viewModel: VM

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { newUiState(it) }
            }
        }
    }

    abstract fun newUiState(state: S)
}