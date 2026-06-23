package ui.components.moon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ui.painterResource
import ui.theme.Dimens
import ui.theme.UIKit

private const val HEADER_ANIMATION_DURATION_MS = 180

@Composable
fun MoonTopAppBar(
    modifier: Modifier = Modifier,
    title: String,
    onTitleClick: (() -> Unit)? = null,
    subtitle: String? = null,
    subtitleContent: @Composable (RowScope.() -> Unit)? = null,
    isUpdating: Boolean = false,
    textVisible: Boolean = true,
    titleAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    navigationIconRes: Int? = null,
    onNavigationClick: (() -> Unit)? = null,
    hasCustomActions: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    actionIconRes: Int? = null,
    onActionClick: (() -> Unit)? = null,
    ignoreSystemOffset: Boolean = true,
    showDivider: Boolean = false,
    backgroundColor: Color = UIKit.colorScheme.background.page,
    iconTintColor: Color = UIKit.colorScheme.buttonSecondary.primaryForeground,
    iconBackgroundColor: Color = UIKit.colorScheme.buttonSecondary.primaryBackground,
    content: @Composable (ColumnScope.() -> Unit)? = null
) {
    val headerHeight = Dimens.heightBar
    val subtitleContainerTargetVisible =
        !subtitle.isNullOrBlank() || subtitleContent != null || isUpdating

    val textAlpha by animateFloatAsState(
        targetValue = if (textVisible) 1f else 0f,
        animationSpec = tween(durationMillis = HEADER_ANIMATION_DURATION_MS),
        label = "TextAlphaAnimation"
    )

    val actionIconAlpha by animateFloatAsState(
        targetValue = if (actionIconRes != null) 1f else 0f,
        animationSpec = tween(HEADER_ANIMATION_DURATION_MS),
        label = "ActionIconAlpha"
    )

    val startPadding =
        if (hasCustomActions && actionIconAlpha > 0.01f) Dimens.offsetExtraSmall else 0.dp

    val titleInteractionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(headerHeight)
            .background(backgroundColor)
            .let {
                if (!ignoreSystemOffset) {
                    it.windowInsetsPadding(WindowInsets.statusBars)
                } else {
                    it
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = Dimens.offsetMedium),
            contentAlignment = Alignment.Center
        ) {
            if (content != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    content()
                }
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .alpha(textAlpha)
                        .then(
                            if (onTitleClick != null) {
                                Modifier.clickable(
                                    onClick = onTitleClick,
                                    interactionSource = titleInteractionSource,
                                    indication = null,
                                )
                            } else {
                                Modifier
                            }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = title,
                        color = UIKit.colorScheme.text.primary,
                        style = UIKit.typography.h3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )

                    AnimatedVisibility(
                        visible = subtitleContainerTargetVisible,
                        enter = fadeIn(animationSpec = tween(HEADER_ANIMATION_DURATION_MS)) +
                                expandVertically(animationSpec = tween(HEADER_ANIMATION_DURATION_MS)),
                        exit = fadeOut(animationSpec = tween(HEADER_ANIMATION_DURATION_MS)) +
                                shrinkVertically(animationSpec = tween(HEADER_ANIMATION_DURATION_MS))
                    ) {
                        Row(
                            modifier = Modifier.height(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isUpdating) {
                                Spacer(modifier = Modifier.width(Dimens.offsetExtraSmall))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = UIKit.colorScheme.text.secondary,
                                    strokeWidth = 1.5.dp
                                )
                            } else if (!subtitle.isNullOrBlank()) {
                                Text(
                                    text = subtitle,
                                    color = UIKit.colorScheme.text.secondary,
                                    style = UIKit.typography.body2,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else if (subtitleContent != null) {
                                subtitleContent()
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (navigationIconRes != null) {
                        MoonActionIcon(
                            painter = painterResource(id = navigationIconRes),
                            onClick = onNavigationClick,
                            tintColor = iconTintColor,
                            backgroundColor = iconBackgroundColor,
                            contentDescription = "Navigation"
                        )
                        Spacer(Modifier.width(16.dp))
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    actions()

                    Spacer(modifier = Modifier.width(startPadding))

                    if (actionIconRes != null && actionIconAlpha > 0.01f) {
                        Box(
                            modifier = Modifier
                                .size(Dimens.sizeAction)
                                .alpha(actionIconAlpha)
                        ) {
                            MoonActionIcon(
                                painter = painterResource(id = actionIconRes),
                                onClick = onActionClick,
                                tintColor = iconTintColor,
                                backgroundColor = iconBackgroundColor,
                                contentDescription = "Action"
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDivider) {
        MoonDivider()
    }
}
