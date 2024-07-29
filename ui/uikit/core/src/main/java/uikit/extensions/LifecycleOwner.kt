package uikit.extensions

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

fun <T> LifecycleOwner.collectFlow(flow: Flow<T>, action: suspend (T) -> Unit): Job {
    return flow.onEach(action).flowOn(Dispatchers.Main).launchIn(lifecycleScope) // .flowWithLifecycle(lifecycle)
}

fun <T> ViewModel.collectFlow(flow: Flow<T>, action: suspend (T) -> Unit): Job {
    return flow.onEach(action).launchIn(viewModelScope)
}
