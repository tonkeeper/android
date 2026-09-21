package ui.components.moon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.components.moon.container.CornerBadgeStrategy
import ui.components.moon.container.MoonBadgedBox
import ui.painterResource
import ui.theme.Dimens
import ui.theme.UIKit

private const val HEADER_ANIMATION_DURATION_MS = 180
private const val TITLE_MIN_FONT_SCALE = 0.85f
private const val TITLE_FONT_SCALE_STEP = 0.95f

@Composable
fun MoonTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onTitleClick: (() -> Unit)? = null,
    subtitle: @Composable (RowScope.() -> Unit)? = null,
    isUpdating: Boolean = false,
    textVisible: Boolean = true,
    titleAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    navigationIconRes: Int? = null,
    onNavigationClick: (() -> Unit)? = null,
    navigation: @Composable RowScope.() -> Unit = {},
    hasCustomActions: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    actionIconRes: Int? = null,
    onActionClick: (() -> Unit)? = null,
    ignoreSystemOffset: Boolean = true,
    showDivider: Boolean = false,
    horizontalPadding: Dp = Dimens.offsetMedium,
    backgroundColor: Color = UIKit.colorScheme.background.page,
    iconTintColor: Color = UIKit.colorScheme.buttonSecondary.primaryForeground,
    iconBackgroundColor: Color = UIKit.colorScheme.buttonSecondary.primaryBackground,
    content: @Composable (ColumnScope.() -> Unit)? = null
) {
    val headerHeight = Dimens.heightBar
    val subtitleContainerTargetVisible = subtitle != null || isUpdating

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
        Layout(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = horizontalPadding),
            content = {
                if (content != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        content()
                    }
                } else {
                    Column(
                        modifier = Modifier
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
                        title()

                        MoonExpandable(
                            visible = subtitleContainerTargetVisible,
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
                                } else if (subtitle != null) {
                                    subtitle()
                                }
                            }
                        }
                    }
                }

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
                    navigation()
                }

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
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val navigationPlaceable = measurables[1].measure(loose)
            val actionsPlaceable = measurables[2].measure(loose)
            val sideControlsWidth = maxOf(navigationPlaceable.width, actionsPlaceable.width)
            val titleSpacing = Dimens.offsetExtraSmall.roundToPx()
            val titleMaxWidth = (constraints.maxWidth - 2 * (sideControlsWidth + titleSpacing))
                .coerceAtLeast(0)
            val titlePlaceable = measurables[0].measure(
                loose.copy(maxWidth = titleMaxWidth)
            )
            val height = constraints.maxHeight
            layout(constraints.maxWidth, height) {
                navigationPlaceable.placeRelative(
                    x = 0,
                    y = (height - navigationPlaceable.height) / 2,
                )
                actionsPlaceable.placeRelative(
                    x = constraints.maxWidth - actionsPlaceable.width,
                    y = (height - actionsPlaceable.height) / 2,
                )
                titlePlaceable.placeRelative(
                    x = (constraints.maxWidth - titlePlaceable.width) / 2,
                    y = (height - titlePlaceable.height) / 2,
                )
            }
        }
    }

    if (showDivider) {
        MoonDivider()
    }
}

@Composable
fun MoonTopAppBar(
    modifier: Modifier = Modifier,
    title: String = "",
    onTitleClick: (() -> Unit)? = null,
    subtitle: String? = null,
    isUpdating: Boolean = false,
    textVisible: Boolean = true,
    titleAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    navigationIconRes: Int? = null,
    onNavigationClick: (() -> Unit)? = null,
    navigation: @Composable RowScope.() -> Unit = {},
    hasCustomActions: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    actionIconRes: Int? = null,
    onActionClick: (() -> Unit)? = null,
    ignoreSystemOffset: Boolean = true,
    showDivider: Boolean = false,
    horizontalPadding: Dp = Dimens.offsetMedium,
    backgroundColor: Color = UIKit.colorScheme.background.page,
    iconTintColor: Color = UIKit.colorScheme.buttonSecondary.primaryForeground,
    iconBackgroundColor: Color = UIKit.colorScheme.buttonSecondary.primaryBackground,
    // Design-requested behavior (TK-3117): a title that does not fit between the bar controls
    // is first scaled down slightly and only then ellipsized.
    titleAutoShrink: Boolean = false,
    content: @Composable (ColumnScope.() -> Unit)? = null
) {
    MoonTopAppBar(
        title = { MoonTopAppBarTitle(text = title, autoShrink = titleAutoShrink) },
        modifier = modifier,
        onTitleClick = onTitleClick,
        subtitle = subtitle?.takeIf { it.isNotBlank() }?.let { text ->
            { MoonTopAppBarSubtitle(text = text) }
        },
        isUpdating = isUpdating,
        textVisible = textVisible,
        titleAlignment = titleAlignment,
        navigationIconRes = navigationIconRes,
        onNavigationClick = onNavigationClick,
        navigation = navigation,
        hasCustomActions = hasCustomActions,
        actions = actions,
        actionIconRes = actionIconRes,
        onActionClick = onActionClick,
        ignoreSystemOffset = ignoreSystemOffset,
        showDivider = showDivider,
        horizontalPadding = horizontalPadding,
        backgroundColor = backgroundColor,
        iconTintColor = iconTintColor,
        iconBackgroundColor = iconBackgroundColor,
        content = content,
    )
}

@Composable
fun MoonTopAppBarIcon(
    painter: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showNotificationDot: Boolean = false,
    tintColor: Color = UIKit.colorScheme.icon.secondary,
    contentDescription: String? = null,
) {
    val badge: (@Composable () -> Unit)? = if (showNotificationDot) {
        {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(UIKit.colorScheme.accent.red)
            )
        }
    } else {
        null
    }

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        MoonBadgedBox(strategy = CornerBadgeStrategy(), badge = badge) {
            Icon(
                modifier = Modifier.size(28.dp),
                painter = painter,
                tint = tintColor,
                contentDescription = contentDescription
            )
        }
    }
}

@Composable
fun MoonTopAppBarTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = UIKit.colorScheme.text.primary,
    // Design-requested behavior (TK-3117): a title that does not fit between the bar controls
    // is first scaled down slightly and only then ellipsized.
    autoShrink: Boolean = false,
) {
    val style = UIKit.typography.h3
    if (!autoShrink) {
        Text(
            modifier = modifier,
            text = text,
            color = color,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        return
    }

    var fontScale by remember(text) { mutableStateOf(1f) }
    var readyToDraw by remember(text) { mutableStateOf(false) }
    Text(
        modifier = modifier.drawWithContent {
            if (readyToDraw) {
                drawContent()
            }
        },
        text = text,
        color = color,
        style = style.copy(fontSize = style.fontSize * fontScale),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        onTextLayout = { result ->
            if (result.didOverflowWidth && fontScale > TITLE_MIN_FONT_SCALE) {
                fontScale = (fontScale * TITLE_FONT_SCALE_STEP)
                    .coerceAtLeast(TITLE_MIN_FONT_SCALE)
            } else {
                readyToDraw = true
            }
        },
    )
}

@Composable
fun MoonTopAppBarSubtitle(
    text: CharSequence,
    modifier: Modifier = Modifier,
    color: Color = UIKit.colorScheme.text.secondary,
) {
    MoonItemSubtitle(
        text = text,
        modifier = modifier,
        color = color,
        maxLines = 1,
    )
}

@Composable
fun MoonExpandable(visible: Boolean, content: @Composable AnimatedVisibilityScope.() -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(HEADER_ANIMATION_DURATION_MS)) +
                expandVertically(animationSpec = tween(HEADER_ANIMATION_DURATION_MS)),
        exit = fadeOut(animationSpec = tween(HEADER_ANIMATION_DURATION_MS)) +
                shrinkVertically(animationSpec = tween(HEADER_ANIMATION_DURATION_MS)),
        content = content,
    )
}
