package uikit.mvi

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import uikit.base.BaseFragment
import kotlinx.coroutines.launch

@Deprecated("Use default ViewModel and Flow logic")
abstract class UiScreen<S: UiState, E: UiEffect, F: UiFeature<S, E>>(
    layoutRes: Int
): BaseFragment(layoutRes) {

    abstract val feature: F

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                feature.uiState.collect { state -> newUiState(state) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            feature.uiEffect.collect { effect ->
                effect?.let { newUiEffect(it) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        feature.destroy()
    }

    abstract fun newUiState(state: S)

    open fun newUiEffect(effect: E) {

    }
}