package com.tonapps.mvi.flow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.tonapps.mvi.Mvi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException

@Composable
fun <T : R, R> Flow<T>.collectSafeState(
    initial: R,
    context: CoroutineContext = EmptyCoroutineContext,
): State<R> = produceState(initial, this, context) {
    val safeFlow = this@collectSafeState.retryWhen { cause, _ ->
        if (cause is CancellationException) {
            false
        } else {
            !Mvi.config().isFastFail
        }
    }

    if (context == EmptyCoroutineContext) {
        safeFlow.collect { value = it }
    } else withContext(context) {
        safeFlow.collect { value = it }
    }
}
