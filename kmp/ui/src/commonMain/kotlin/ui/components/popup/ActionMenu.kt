package ui.components.popup

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

enum class ActionMenuHorizontalAlignment {
    Start,
    Center,
}

@Composable
fun ActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    items: List<ComposeActionItem>,
    onItemClick: (ComposeActionItem, Int) -> Unit,
    properties: PopupProperties = PopupProperties(focusable = true),
    offset: DpOffset = DpOffset(0.dp, 12.dp),
    horizontalAlignment: ActionMenuHorizontalAlignment = ActionMenuHorizontalAlignment.Start,
    width: Dp = 240.dp,
) {
    ActionMenuPopupFrame(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        properties = properties,
        offset = offset,
        horizontalAlignment = horizontalAlignment,
    ) { expandedState, transformOriginState ->
        ActionMenuContent(
            modifier = modifier,
            expandedState = expandedState,
            transformOriginState = transformOriginState,
            items = items,
            onItemClick = onItemClick,
            width = width,
        )
    }
}

@Composable
fun ActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    properties: PopupProperties = PopupProperties(focusable = true),
    offset: DpOffset = DpOffset(0.dp, 12.dp),
    horizontalAlignment: ActionMenuHorizontalAlignment = ActionMenuHorizontalAlignment.Start,
    width: Dp = 240.dp,
    content: @Composable () -> Unit,
) {
    ActionMenuPopupFrame(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        properties = properties,
        offset = offset,
        horizontalAlignment = horizontalAlignment,
    ) { expandedState, transformOriginState ->
        ActionMenuAnimatedSurface(
            expandedState = expandedState,
            transformOriginState = transformOriginState,
            width = width,
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun ActionMenuPopupFrame(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    properties: PopupProperties,
    offset: DpOffset,
    horizontalAlignment: ActionMenuHorizontalAlignment,
    content: @Composable (
        expandedState: MutableTransitionState<Boolean>,
        transformOriginState: MutableState<TransformOrigin>,
    ) -> Unit,
) {
    val expandedState = remember { MutableTransitionState(false) }
    expandedState.targetState = expanded
    if (expandedState.currentState || expandedState.targetState) {
        val transformOriginState = remember { mutableStateOf(TransformOrigin.Center) }
        val density = LocalDensity.current

        val popupPositionProvider = ActionMenuPositionProvider(
            contentOffset = offset,
            density = density,
            horizontalAlignment = horizontalAlignment,
        ) { parentBounds, menuBounds ->
            transformOriginState.value = calculateTransformOrigin(parentBounds, menuBounds)
        }

        Popup(
            onDismissRequest = onDismissRequest,
            popupPositionProvider = popupPositionProvider,
            properties = properties,
        ) {
            content(expandedState, transformOriginState)
        }
    }
}
