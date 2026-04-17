package com.tonapps.mvi

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

private class StateViewModel() : ViewModel() {
    var isInitialized: Boolean = false
}

@Composable
private fun requireVmStoreOwner(): ViewModelStoreOwner {
    return checkNotNull(LocalViewModelStoreOwner.current) {
        "No LifecycleOwner was provided via LocalLifecycleOwner"
    }
}

@Composable
fun OnceOnly(
    initializer: () -> Unit
) {
    val owner = requireVmStoreOwner()
    val viewModel = viewModel(owner, null, { StateViewModel() })
    if (!viewModel.isInitialized) {
        viewModel.isInitialized = true
        initializer.invoke()
    }
}
