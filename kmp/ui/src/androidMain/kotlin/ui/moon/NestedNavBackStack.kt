package ui.moon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

class NestedNavBackStack internal constructor(
    private val backStack: NavBackStack<NavKey>,
    private val onBack: () -> Unit,
) : MutableList<NavKey> by backStack {

    fun safeRemoveLastOrNull(key: NavKey): NavKey? {
        if (lastOrNull() != key) {
            return null
        }

        return safeRemoveLastOrNull()
    }

    fun safeRemoveLastOrNull(): NavKey? {
        if (size > 1) {
            return backStack.removeLastOrNull()
        }

        onBack()

        return null
    }
}

@Composable
fun rememberNestedNavBackStack(
    initial: NavKey,
    onBack: () -> Unit,
    saveable: Boolean = true,
): NestedNavBackStack {
    val backStack = if (saveable) {
        rememberNavBackStack(initial)
    } else {
        remember { NavBackStack<NavKey>(initial) }
    }
    val currentOnBack by rememberUpdatedState(onBack)
    return remember(backStack) {
        NestedNavBackStack(backStack) { currentOnBack() }
    }
}
