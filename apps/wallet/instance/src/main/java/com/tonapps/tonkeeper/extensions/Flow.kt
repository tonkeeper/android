package com.tonapps.tonkeeper.extensions

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

fun <T> Flow<T>.launch(owner: LifecycleOwner, action: suspend (T) -> Unit) {
    onEach {
        action(it)
    }.launchIn(owner.lifecycleScope)
}
