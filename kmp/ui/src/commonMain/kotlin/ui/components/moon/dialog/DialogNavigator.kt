package ui.components.moon.dialog

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class DialogNavigator(
    val state: SheetState,
    private val scope: CoroutineScope,
    val onClose: () -> Unit,
) {
    fun close(withAnimation: Boolean = true) {
        if (withAnimation) {
            scope.launch { state.hide() }.invokeOnCompletion { onClose() }
        } else {
            onClose()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberDialogNavigator(onClose: () -> Unit): DialogNavigator {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    return remember(onClose) { DialogNavigator(state, scope, onClose) }
}
