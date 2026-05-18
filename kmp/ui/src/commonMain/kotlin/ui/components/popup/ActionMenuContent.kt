package ui.components.popup

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import ui.components.moon.MoonDivider
import ui.components.moon.container.MoonSurface
import ui.theme.Dimens
import ui.theme.UIKit

@Composable
fun ActionMenuAnimatedSurface(
    expandedState: MutableTransitionState<Boolean>,
    transformOriginState: MutableState<TransformOrigin>,
    content: @Composable () -> Unit,
) {
    val transition = rememberTransition(expandedState, "ActionMenu")

    val scale by transition.animateFloat(transitionSpec = {
        if (false isTransitioningTo true) {
            tween(durationMillis = 120, easing = LinearOutSlowInEasing)
        } else {
            tween(durationMillis = 1, delayMillis = 75 - 1)
        }
    }) { expanded ->
        if (expanded) 1f else 0.8f
    }

    val alpha by transition.animateFloat(transitionSpec = {
        if (false isTransitioningTo true) {
            tween(durationMillis = 30)
        } else {
            tween(durationMillis = 75)
        }
    }) { expanded ->
        if (expanded) 1f else 0f
    }

    val isInspecting = LocalInspectionMode.current

    MoonSurface(
        modifier = Modifier.graphicsLayer {
            scaleX = if (!isInspecting) scale else if (expandedState.targetState) 1f else 0.8f
            scaleY = if (!isInspecting) scale else if (expandedState.targetState) 1f else 0.8f
            this.alpha = if (!isInspecting) alpha else if (expandedState.targetState) 1f else 0f
            transformOrigin = transformOriginState.value
        }.widthIn(max = 240.dp),
        color = UIKit.colorScheme.background.contentTint,
    ) {
        content()
    }
}

@Composable
fun ActionMenuContent(
    modifier: Modifier = Modifier,
    expandedState: MutableTransitionState<Boolean>,
    transformOriginState: MutableState<TransformOrigin>,
    items: List<ComposeActionItem>,
    onItemClick: (ComposeActionItem) -> Unit
) {
    ActionMenuAnimatedSurface(
        expandedState = expandedState,
        transformOriginState = transformOriginState,
    ) {
        Column(modifier = modifier) {
            items.forEachIndexed { index, item ->
                ActionMenuRow(
                    item = item,
                    onClick = { onItemClick(item) },
                )

                if (index < items.lastIndex) {
                    MoonDivider(
                        modifier = Modifier.padding(start = Dimens.offsetMedium)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionMenuRow(
    item: ComposeActionItem,
    onClick: () -> Unit,
) {
    if (item.iconPainter != null) {
        ActionMenuItem(
            text = item.text,
            painter = item.iconPainter,
            onClick = onClick,
        )
    } else {
        ActionMenuItem(
            text = item.text,
            icon = item.icon,
            onClick = onClick,
        )
    }
}