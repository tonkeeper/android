package uikit.extensions

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

fun <T> LifecycleOwner.collectFlow(flow: Flow<T>, action: suspend (T) -> Unit): Job {
    return flow.onEach(action).launchIn(lifecycleScope) // .flowWithLifecycle(lifecycle)
}