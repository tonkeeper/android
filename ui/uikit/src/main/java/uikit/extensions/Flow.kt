package uikit.extensions

import androidx.recyclerview.widget.ListAdapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform

fun <I> Flow<List<I>>.adapter(adapter: ListAdapter<I, *>): Flow<List<I>> = transform { value ->
    adapter.submitList(value)
    return@transform emit(value)
}