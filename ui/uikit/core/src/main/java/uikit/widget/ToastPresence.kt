package uikit.widget

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

object ToastPresence {
    private val _owners = MutableStateFlow(emptySet<Any>())
    val owners: StateFlow<Set<Any>> = _owners

    fun set(owner: Any, shown: Boolean) {
        _owners.update {
            if (shown) {
                it + owner
            } else {
                it - owner
            }
        }
    }
}
