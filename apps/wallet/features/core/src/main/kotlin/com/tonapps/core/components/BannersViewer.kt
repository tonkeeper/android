package com.tonapps.core.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.net.toUri
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.HomeBanner.HomeBannerAction
import com.tonapps.core.deeplink.DeepLinkRoute
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.api.entity.BannerEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ui.components.moon.MoonAsyncImage
import ui.painterResource
import ui.theme.UIKit
import ui.theme.modifiers.modifyIf
import kotlin.math.abs

private val BannerHeight = 90.dp
private val BannerImageSlotWidth = 134.dp
private val BannerBackPeek = 16.dp
private val BannerBottomMargin = 16.dp
private val BannerSideMargin = 16.dp
private val HideBlurRadius = 16.dp
private const val BannerBackScale = 0.88f
private const val BannerBackAlpha = 0.72f
private const val DismissThreshold = 0.3f
private const val SlideDurationMs = 300
private const val ReturnDurationMs = 200
private const val EntranceDurationMs = 150

@Composable
fun BannersViewer(
    banners: List<BannerEntity>,
    onHide: (BannerEntity) -> Unit,
    onClick: (BannerEntity.Button) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val state = remember(banners) { BannerCarouselState(banners, scope) }
    val currentBannerId = state.current?.id
    val clickedBannerIds = remember(currentBannerId) { mutableSetOf<String>() }

    LaunchedEffect(currentBannerId) {
        val banner = state.current ?: return@LaunchedEffect
        AnalyticsHelper.Default.events.homeBanner.bannerView(banner.id)
    }

    val trackingClick: (BannerEntity, BannerEntity.Button) -> Unit = { banner, button ->
        if (clickedBannerIds.add(banner.id)) {
            AnalyticsHelper.Default.events.homeBanner.bannerClick(banner.id, bannerAction(button))
        }
        onClick(button)
    }
    val widthPx = LocalWindowInfo.current.containerSize.width.toFloat()
    val peekHeight by animateDpAsState(
        targetValue = if (state.size > 1) {
            BannerBackPeek
        } else {
            0.dp
        },
        label = "bannerPeekHeight",
    )
    val collapseFraction = if (state.collapsing) {
        state.fade.value
    } else {
        0f
    }
    val containerHeight = if (state.items.isEmpty()) {
        0.dp
    } else {
        (BannerHeight + peekHeight + BannerBottomMargin) * (1f - collapseFraction)
    }

    Box(modifier = modifier.height(containerHeight)) {
        val current = state.current ?: return@Box

        state.next?.let { next ->
            BackBanner(
                banner = next,
                promote = state.promote,
                onClick = { button -> trackingClick(next, button) },
            )
        }

        FrontBanner(
            banner = current,
            offsetX = state.offsetX,
            fade = state.fade.value,
            draggable = state.size > 1 && !state.isAnimating,
            onClick = { button -> trackingClick(current, button) },
            onHide = { state.hide(onHide) },
            onDrag = { delta -> state.drag(delta, widthPx) },
            onDragStopped = { state.dragStopped(widthPx) },
        )
    }
}

@Stable
private class BannerCarouselState(
    banners: List<BannerEntity>,
    private val scope: CoroutineScope,
) {
    var items by mutableStateOf(banners)
        private set
    private var index by mutableIntStateOf(0)

    var offsetX by mutableFloatStateOf(0f)
    var promote by mutableFloatStateOf(0f)
    val fade = Animatable(0f)

    var isAnimating by mutableStateOf(false)
        private set
    var collapsing by mutableStateOf(false)
        private set

    val size: Int get() = items.size

    val current: BannerEntity?
        get() = if (items.isEmpty()) {
            null
        } else {
            items[index % items.size]
        }

    val next: BannerEntity?
        get() = if (items.size > 1) {
            items[(index + 1) % items.size]
        } else {
            null
        }

    fun drag(delta: Float, widthPx: Float) {
        offsetX += delta
        promote = (abs(offsetX) / widthPx).coerceIn(0f, 1f)
    }

    fun dragStopped(widthPx: Float) {
        if (abs(offsetX) > widthPx * DismissThreshold) {
            val direction = if (offsetX > 0) {
                1f
            } else {
                -1f
            }
            advance(direction, widthPx)
        } else {
            scope.launch {
                launch { animateOffset(0f, ReturnDurationMs) }
                animatePromote(0f, ReturnDurationMs)
            }
        }
    }

    private fun advance(direction: Float, widthPx: Float) {
        if (isAnimating || size <= 1) {
            return
        }
        isAnimating = true
        scope.launch {
            val slide = launch { animateOffset(direction * widthPx, SlideDurationMs) }
            val rise = launch { animatePromote(1f, SlideDurationMs) }
            slide.join()
            rise.join()

            index = (index + 1) % size
            offsetX = 0f
            animatePromote(0f, EntranceDurationMs)
            isAnimating = false
        }
    }

    private suspend fun animateOffset(target: Float, durationMs: Int) {
        val start = offsetX
        animate(
            initialValue = start,
            targetValue = target,
            animationSpec = tween(durationMs),
        ) { value, _ ->
            offsetX = value
        }
    }

    private suspend fun animatePromote(target: Float, durationMs: Int) {
        val start = promote
        animate(
            initialValue = start,
            targetValue = target,
            animationSpec = tween(durationMs),
        ) { value, _ ->
            promote = value
        }
    }

    fun hide(onHide: (BannerEntity) -> Unit) {
        if (isAnimating) {
            return
        }
        val hidden = current ?: return
        isAnimating = true
        collapsing = next == null
        scope.launch {
            val fadeOut = launch { fade.animateTo(1f, tween(SlideDurationMs)) }
            val rise = launch {
                if (size > 1) {
                    animatePromote(1f, SlideDurationMs)
                }
            }
            fadeOut.join()
            rise.join()

            onHide(hidden)
            items = items.filterNot { it.id == hidden.id }
            fade.snapTo(0f)
            offsetX = 0f
            collapsing = false
            animatePromote(0f, EntranceDurationMs)
            isAnimating = false
        }
    }
}

@Composable
private fun BoxScope.BackBanner(
    banner: BannerEntity,
    promote: Float,
    onClick: (BannerEntity.Button) -> Unit,
) {
    val scale = lerp(BannerBackScale, 1f, promote)
    BannerCard(
        banner = banner,
        overlayAlpha = 1f - promote,
        onClick = onClick,
        onHide = {},
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(horizontal = BannerSideMargin)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = lerp(BannerBackAlpha, 1f, promote)
                translationY = lerp(BannerBackPeek.toPx(), 0f, promote)
            },
    )
}

@Composable
private fun BoxScope.FrontBanner(
    banner: BannerEntity,
    offsetX: Float,
    fade: Float,
    draggable: Boolean,
    onClick: (BannerEntity.Button) -> Unit,
    onHide: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragStopped: () -> Unit,
) {
    BannerCard(
        banner = banner,
        onClick = onClick,
        onHide = onHide,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(horizontal = BannerSideMargin)
            .graphicsLayer {
                translationX = offsetX
                alpha = 1f - fade
            }
            .blur(HideBlurRadius * fade)
            .draggable(
                enabled = draggable,
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { onDrag(it) },
                onDragStopped = { onDragStopped() },
            ),
    )
}

@Composable
private fun BannerCard(
    banner: BannerEntity,
    onClick: (BannerEntity.Button) -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
    overlayAlpha: Float = 0f,
) {
    val foregroundColor = UIKit.colorScheme.text.primary
    val shape = UIKit.shapes.extraLarge
    Box(
        modifier = modifier
            .height(BannerHeight)
            .clip(shape)
            .background(UIKit.colorScheme.background.pageAlternate)
            .border(
                width = 1.dp,
                color = UIKit.colorScheme.buttonTertiary.primaryBackground,
                shape = shape,
            )
            .modifyIf {
                banner.button?.let { button ->
                    noRippleClickable { onClick(button) }
                }
            }
    ) {
        banner.image?.let { image ->
            MoonAsyncImage(
                image = image,
                modifier = Modifier.matchParentSize(),
                alignment = Alignment.CenterEnd,
                contentScale = ContentScale.FillHeight,
            )
        }
        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .wrapContentHeight()
                    .padding(start = 16.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = banner.title,
                    style = UIKit.typography.label2,
                    color = foregroundColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                banner.button?.let { button ->
                    Row(
                        modifier = Modifier.alpha(0.64f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = button.title,
                            style = UIKit.typography.body3,
                            color = foregroundColor,
                        )

                        Icon(
                            modifier = Modifier
                                .padding(start = 2.dp)
                                .size(12.dp),
                            painter = painterResource(UIKitIcon.ic_chevron_right_12),
                            contentDescription = null,
                            tint = UIKit.colorScheme.text.primary,
                        )
                    }
                }
            }

            banner.image?.let {
                Spacer(modifier = Modifier.width(BannerImageSlotWidth))
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(width = 54.dp, height = 44.dp)
                .noRippleClickable(onHide)
                .padding(10.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(UIKit.colorScheme.background.contentAlternate),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(UIKitIcon.ic_close_small_16),
                    contentDescription = null,
                    tint = UIKit.colorScheme.icon.secondary,
                )
            }
        }

        val overlayColor = when {
            UIKit.isLightTheme -> UIKit.colorScheme.background.pageAlternate
            else -> UIKit.colorScheme.background.content
        }

        if (overlayAlpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(overlayColor.copy(alpha = overlayAlpha))
            )
        }
    }
}

private fun bannerAction(button: BannerEntity.Button): HomeBannerAction {
    if (button.type != BannerEntity.Button.Type.DEEPLINK) {
        return HomeBannerAction.Other
    }
    return runCatching {
        when (DeepLinkRoute.resolve(button.payload.toUri())) {
            is DeepLinkRoute.Deposit, DeepLinkRoute.Purchase -> HomeBannerAction.Deposit
            is DeepLinkRoute.Withdraw -> HomeBannerAction.Withdraw
            is DeepLinkRoute.Swap -> HomeBannerAction.Swap
            DeepLinkRoute.Staking, is DeepLinkRoute.StakingPool -> HomeBannerAction.Staking
            is DeepLinkRoute.Battery -> HomeBannerAction.Battery
            is DeepLinkRoute.Send, is DeepLinkRoute.Transfer -> HomeBannerAction.Send
            is DeepLinkRoute.Tabs.Trading, is DeepLinkRoute.Asset -> HomeBannerAction.Trade
            is DeepLinkRoute.Exchange -> HomeBannerAction.Exchange
            is DeepLinkRoute.DApp, is DeepLinkRoute.Tabs.Browser -> HomeBannerAction.Dapp
            else -> HomeBannerAction.Other
        }
    }.getOrDefault(HomeBannerAction.Other)
}

@Composable
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick,
    )
}
