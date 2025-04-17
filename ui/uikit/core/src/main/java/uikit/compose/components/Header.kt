package uikit.compose.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uikit.compose.Dimens
import uikit.compose.UIKit

private const val HEADER_ANIMATION_DURATION_MS = 180

@Composable
fun Header(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    isUpdating: Boolean = false,
    textVisible: Boolean = true,
    titleAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    @DrawableRes navigationIconRes: Int? = null,
    onNavigationClick: (() -> Unit)? = null,
    hasCustomActions: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    @DrawableRes actionIconRes: Int? = null,
    onActionClick: (() -> Unit)? = null,
    ignoreSystemOffset: Boolean = false,
    showDivider: Boolean = true,
    backgroundColor: Color = UIKit.colors.backgroundContent,
    iconTintColor: Color = UIKit.colors.buttonSecondaryForeground,
    iconBackgroundColor: Color = UIKit.colors.buttonSecondaryBackground,
) {
    val headerHeight = Dimens.barHeight
    val density = LocalDensity.current
    val subtitleContainerTargetVisible = !subtitle.isNullOrBlank() || isUpdating
    val textAlpha by animateFloatAsState(
        targetValue = if (textVisible) 1f else 0f,
        animationSpec = tween(durationMillis = HEADER_ANIMATION_DURATION_MS),
        label = "TextAlphaAnimation"
    )

    val headerModifier = modifier
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

    Column(modifier = headerModifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = Dimens.offsetMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val navIconAlpha by animateFloatAsState(
                targetValue = if (navigationIconRes != null) 1f else 0f,
                animationSpec = tween(HEADER_ANIMATION_DURATION_MS),
                label = "NavIconAlpha"
            )
            Box(
                modifier = Modifier
                    .size(Dimens.actionSize)
                    .alpha(navIconAlpha)
            ) {
                if (navigationIconRes != null && navIconAlpha > 0.01f) {
                    HeaderIcon(
                        resId = navigationIconRes,
                        onClick = if (navIconAlpha == 1f) onNavigationClick else null,
                        tintColor = iconTintColor,
                        backgroundColor = iconBackgroundColor,
                        contentDescription = "Navigation"
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(textAlpha)
                    .padding(horizontal = Dimens.offsetMedium),
                horizontalAlignment = titleAlignment,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    color = UIKit.colors.textPrimary,
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
                    enter = fadeIn(animationSpec = tween(HEADER_ANIMATION_DURATION_MS)) + expandVertically(animationSpec = tween(
                        HEADER_ANIMATION_DURATION_MS
                    )),
                    exit = fadeOut(animationSpec = tween(HEADER_ANIMATION_DURATION_MS)) + shrinkVertically(animationSpec = tween(
                        HEADER_ANIMATION_DURATION_MS
                    ))
                ) {
                    Row(
                        modifier = Modifier.height(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!subtitle.isNullOrBlank() && !isUpdating) {
                            Text(
                                text = subtitle,
                                color = UIKit.colors.textSecondary,
                                style = UIKit.typography.body2.copy(fontWeight = FontWeight.Medium),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isUpdating) {
                            Spacer(modifier = Modifier.width(Dimens.offsetExtraSmall))
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = UIKit.colors.textSecondary,
                                strokeWidth = 1.5.dp
                            )
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
                val startPadding = if (hasCustomActions && actionIconAlpha > 0.01f) Dimens.offsetExtraSmall else 0.dp
                Spacer(modifier = Modifier.width(startPadding))

                Box(
                    modifier = Modifier
                        .size(Dimens.actionSize)
                        .alpha(actionIconAlpha)
                ) {
                    if (actionIconRes != null && actionIconAlpha > 0.01f) {
                        HeaderIcon(
                            resId = actionIconRes,
                            onClick = if (actionIconAlpha == 1f) onActionClick else null,
                            tintColor = iconTintColor,
                            backgroundColor = iconBackgroundColor,
                            contentDescription = "Action"
                        )
                    }
                }
            }
        }

        if (showDivider) {
            Divider(
                color = UIKit.colors.separatorCommon,
                thickness = (1 / density.density).dp
            )
        }
    }
}

@Composable
private fun HeaderIcon(
    @DrawableRes resId: Int,
    onClick: (() -> Unit)?,
    tintColor: Color,
    backgroundColor: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(Dimens.actionSize)
            .clip(CircleShape)
            .background(backgroundColor)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = resId),
            contentDescription = contentDescription,
            tint = tintColor
        )
    }
}
