package ui.components.moon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ui.painterResource
import ui.theme.Dimens
import ui.theme.UIKit

private const val HEADER_ANIMATION_DURATION_MS = 180

@Composable
fun MoonTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
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
    val subtitleContainerTargetVisible = !subtitle.isNullOrBlank() || isUpdating
    val textAlpha by animateFloatAsState(
        targetValue = if (textVisible) 1f else 0f,
        animationSpec = tween(durationMillis = HEADER_ANIMATION_DURATION_MS),
        label = "TextAlphaAnimation"
    )

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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = Dimens.offsetMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (navigationIconRes != null) {
                val navIconAlpha by animateFloatAsState(
                    targetValue = 1f,
                    animationSpec = tween(HEADER_ANIMATION_DURATION_MS),
                    label = "NavIconAlpha"
                )

                if (navIconAlpha > 0.01f) {
                    MoonActionIcon(
                        painter = painterResource(id = navigationIconRes),
                        onClick = if (navIconAlpha == 1f) onNavigationClick else null,
                        tintColor = iconTintColor,
                        backgroundColor = iconBackgroundColor,
                        contentDescription = "Navigation"
                    )
                }

                Spacer(Modifier.width(16.dp))
            }

            if (content != null) {
                Column(
                    modifier = Modifier
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    content()
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .alpha(textAlpha),
                    horizontalAlignment = titleAlignment,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = title,
                        color = UIKit.colorScheme.text.primary,
                        style = UIKit.typography.h3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = when (titleAlignment) {
                            Alignment.CenterHorizontally -> TextAlign.Center
                            Alignment.End -> TextAlign.End
                            else -> TextAlign.Start
                        }
                    )

                    AnimatedVisibility(
                        visible = subtitleContainerTargetVisible,
                        enter = fadeIn(animationSpec = tween(HEADER_ANIMATION_DURATION_MS)) + expandVertically(
                            animationSpec = tween(
                                HEADER_ANIMATION_DURATION_MS
                            )
                        ),
                        exit = fadeOut(animationSpec = tween(HEADER_ANIMATION_DURATION_MS)) + shrinkVertically(
                            animationSpec = tween(
                                HEADER_ANIMATION_DURATION_MS
                            )
                        )
                    ) {
                        Row(
                            modifier = Modifier.height(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!subtitle.isNullOrBlank() && !isUpdating) {
                                Text(
                                    text = subtitle,
                                    color = UIKit.colorScheme.text.secondary,
                                    style = UIKit.typography.body2.copy(fontWeight = FontWeight.Medium),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isUpdating) {
                                Spacer(modifier = Modifier.width(Dimens.offsetExtraSmall))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = UIKit.colorScheme.text.secondary,
                                    strokeWidth = 1.5.dp
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                actions()

                val actionIconAlpha by animateFloatAsState(
                    targetValue = if (actionIconRes != null) 1f else 0f,
                    animationSpec = tween(HEADER_ANIMATION_DURATION_MS),
                    label = "ActionIconAlpha"
                )
                val startPadding =
                    if (hasCustomActions && actionIconAlpha > 0.01f) Dimens.offsetExtraSmall else 0.dp
                Spacer(modifier = Modifier.width(startPadding))

                Box(
                    modifier = Modifier
                        .size(Dimens.sizeAction)
                        .alpha(actionIconAlpha)
                ) {
                    // TODO can be multiitem
                    if (actionIconRes != null && actionIconAlpha > 0.01f) {
                        MoonActionIcon(
                            painter = painterResource(id = actionIconRes),
                            onClick = if (actionIconAlpha == 1f) onActionClick else null,
                            tintColor = iconTintColor,
                            backgroundColor = iconBackgroundColor,
                            contentDescription = "Action"
                        )
                    }
                }
            }
        }
    }

    if (showDivider) {
        MoonDivider()
    }
}
