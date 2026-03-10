package ui.components.popup

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

@Composable
fun ActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    items: List<ComposeActionItem>,
    onItemClick: (ComposeActionItem) -> Unit,
    properties: PopupProperties = PopupProperties(),
    offset: DpOffset = DpOffset(0.dp, 12.dp),
) {
    val expandedState = remember { MutableTransitionState(false) }
    expandedState.targetState = expanded
    if (expandedState.currentState || expandedState.targetState) {
        val transformOriginState = remember { mutableStateOf(TransformOrigin.Center) }
        val density = LocalDensity.current

        val popupPositionProvider = ActionMenuPositionProvider(
            contentOffset = offset,
            density = density
        ) { parentBounds, menuBounds ->
            transformOriginState.value = calculateTransformOrigin(parentBounds, menuBounds)
        }

        Popup(
            onDismissRequest = onDismissRequest,
            popupPositionProvider = popupPositionProvider,
            properties = properties,
        ) {
            ActionMenuContent(
                modifier = modifier,
                expandedState = expandedState,
                transformOriginState = transformOriginState,
                items = items,
                onItemClick = onItemClick
            )
        }
    }
}
