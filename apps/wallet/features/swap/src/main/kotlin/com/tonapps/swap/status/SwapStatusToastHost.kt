package com.tonapps.swap.status

import android.graphics.Matrix
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.SweepGradientShader
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.core.components.assetImageUrl
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.data.multichain.exchange.status.SentSwap
import com.tonapps.wallet.data.multichain.exchange.status.SwapStatus
import com.tonapps.wallet.data.multichain.exchange.status.SwapStatusToast
import com.tonapps.wallet.data.multichain.exchange.status.SwapStatusTracker
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ui.components.moon.MoonItemImage
import ui.confirm
import ui.moon.MoonToastEasing
import ui.moon.moonToastEnter
import ui.moon.moonToastExit
import ui.preview.ThemedPreview
import ui.reject
import ui.theme.UIKit
import uikit.widget.ToastPresence
import kotlin.math.abs
import kotlin.math.roundToInt

private const val OutcomeDelay = 5_000L
private const val CrossfadeDuration = 150
private const val ResizeDuration = 250
private const val BeamTurnDuration = 2_000
private const val IconFadeDuration = 200
private const val IconStartScale = 0.7f
private const val OutcomeDuration = 900
private const val RingStart = 100f
private const val RingPeakStart = 250f
private const val RingPeakEnd = 350f
private const val ShakeStart = 50f
private const val ShakeStep = 80f
private const val SuccessRingPeak = 0.7f
private const val FailureRingPeak = 0.8f
private val ToastShape = RoundedCornerShape(28.dp)
private val LeadingSize = 32.dp
private const val DismissThreshold = 0.35f
private val TrackWidth = 1.5.dp
private val BeamWidth = 2.dp
private val RingWidth = 1.5.dp
private val RingSpread = 2.dp
private val TrackColor = Color.White.copy(alpha = 0.08f)
private val BeamColors = listOf(0f, 0f, 0.28f, 0.85f, 0f).map { Color.White.copy(alpha = it) }
private val BeamStops = listOf(0f, 0.5f, 0.88f, 0.985f, 1f)
private val ShakeOffsets = listOf(0f, -4f, 4f, -2f, 1f, 0f)
private val IconFadeEasing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
private val ShakeEasing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

@Composable
fun SwapStatusToastHost(tracker: SwapStatusTracker) {
    val toast by tracker.toast.collectAsState()
    val covered = ToastPresence.owners.collectAsState().value.isNotEmpty()
    val visible = toast != null && !covered
    val details by tracker.details.collectAsState()
    val haptic = LocalHapticFeedback.current

    var lastToast by remember { mutableStateOf<SwapStatusToast?>(null) }
    toast?.let { lastToast = it }

    var felt by remember { mutableStateOf<Pair<SentSwap, SwapStatus>?>(null) }
    LaunchedEffect(toast?.swap, toast?.status, visible) {
        val current = toast?.takeIf { visible } ?: return@LaunchedEffect
        if (felt != current.swap to current.status) {
            felt = current.swap to current.status
            when (current.status) {
                SwapStatus.Pending -> Unit
                SwapStatus.Done -> haptic.confirm()
                SwapStatus.Failed -> haptic.reject()
            }
        }
        if (current.status != SwapStatus.Pending) {
            delay(OutcomeDelay)
            tracker.hide(current)
        }
    }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = statusBarPadding + 8.dp - RingSpread,
                start = 16.dp - RingSpread,
                end = 16.dp - RingSpread,
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = moonToastEnter(),
            exit = moonToastExit(),
        ) {
            lastToast?.let { shown ->
                key(shown.swap) {
                    SwipeableToast(
                        toast = shown,
                        outcomeDue = felt != shown.swap to shown.status,
                        onClick = tracker::openDetails,
                        onDismiss = { tracker.hide(shown) },
                        modifier = Modifier.padding(RingSpread),
                    )
                }
            }
        }
    }
    details?.let { SwapDetailsModal(details = it, onClose = tracker::closeDetails) }
}

@Composable
private fun SwipeableToast(
    toast: SwapStatusToast,
    outcomeDue: Boolean,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val swipe = remember { AnchoredDraggableState(0, DraggableAnchors { 0 at 0f }) }
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(swipe, toast.status) {
        if (swipe.settledValue != 0) {
            swipe.snapTo(0)
        }
        snapshotFlow { swipe.settledValue }.first { it != 0 }
        dismiss()
    }
    val settle by rememberUpdatedState(AnchoredDraggableDefaults.flingBehavior(swipe))
    val gesture = remember { SwipeGesture { settle } }
    val atRest by remember { derivedStateOf { abs(swipe.requireOffset()) < 1f } }
    var width by remember { mutableIntStateOf(0) }
    LaunchedEffect(swipe) {
        snapshotFlow { width.takeIf { it > 0 && atRest && !gesture.busy && swipe.settledValue == 0 } }
            .filterNotNull()
            .collect { measured ->
                try {
                    swipe.snapTo(0)
                    swipe.updateSideAnchors(measured)
                } catch (e: CancellationException) {
                    ensureActive()
                }
            }
    }

    val colors = UIKit.colorScheme
    val outcome = rememberToastOutcome(toast.status, outcomeDue)
    val beamAngle = if (toast.status == SwapStatus.Pending) {
        rememberBeamAngle()
    } else {
        null
    }
    val ring = remember(toast.status, colors) {
        when (toast.status) {
            SwapStatus.Pending -> null
            SwapStatus.Done -> OutcomeRing(colors.accent.green, SuccessRingPeak)
            SwapStatus.Failed -> OutcomeRing(colors.accent.orange, FailureRingPeak)
        }
    }
    val shakes = toast.status == SwapStatus.Failed
    Row(
        modifier = modifier
            .onSizeChanged {
                width = it.width
                if (swipe.anchors.size == 1) {
                    swipe.updateSideAnchors(it.width)
                }
            }
            .offset { IntOffset(swipe.requireOffset().roundToInt(), 0) }
            .anchoredDraggable(
                state = swipe,
                orientation = Orientation.Horizontal,
                enabled = swipe.settledValue == 0,
                interactionSource = gesture,
                flingBehavior = gesture,
            )
            .drawWithCache {
                val stroke = Stroke(RingWidth.toPx())
                val brush = ring?.let { SolidColor(it.color) }
                onDrawWithContent {
                    drawContent()
                    if (ring != null && brush != null) {
                        drawOutcomeRing(outcome.time.value, ring.peak * swipe.fade(), brush, stroke)
                    }
                }
            }
            .graphicsLayer {
                alpha = swipe.fade()
                shadowElevation = 4.dp.toPx()
                shape = ToastShape
            }
            .clip(ToastShape)
            .background(colors.background.contentTint)
            .drawWithCache {
                val track = Stroke(TrackWidth.toPx())
                val trackBrush = SolidColor(TrackColor)
                val beam = Stroke(BeamWidth.toPx())
                val pivot = size.center
                val shader = SweepGradientShader(pivot, BeamColors, BeamStops)
                val brush = ShaderBrush(shader)
                val rotation = Matrix()
                onDrawBehind {
                    val angle = beamAngle?.value ?: return@onDrawBehind
                    drawPillStroke(trackBrush, track)
                    rotation.setRotate(angle, pivot.x, pivot.y)
                    shader.setLocalMatrix(rotation)
                    drawPillStroke(brush, beam)
                }
            }
            .clickable(enabled = atRest, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .graphicsLayer {
                    if (shakes) {
                        translationX = shakeOffset(outcome.time.value).dp.toPx()
                    }
                }
                .animateContentSize(tween(ResizeDuration, easing = MoonToastEasing)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val outcomePop = Modifier.graphicsLayer {
                scaleX = outcome.iconScale.value
                scaleY = outcome.iconScale.value
                alpha = outcome.iconAlpha.value
            }
            Crossfade(
                targetState = toast.status,
                animationSpec = tween(CrossfadeDuration),
            ) { status ->
                Box(
                    modifier = Modifier.size(LeadingSize),
                    contentAlignment = Alignment.Center,
                ) {
                    when (status) {
                        SwapStatus.Pending -> MoonItemImage(image = toast.swap.sell.assetImageUrl(), size = LeadingSize)
                        SwapStatus.Done -> OutcomeIcon(
                            iconRes = UIKitIcon.ic_checkmark_circle_32,
                            color = colors.accent.green,
                            modifier = outcomePop,
                        )
                        SwapStatus.Failed -> OutcomeIcon(
                            iconRes = UIKitIcon.ic_xmark_circle_32,
                            color = colors.accent.orange,
                            modifier = outcomePop,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Crossfade(
                    targetState = toast.status,
                    animationSpec = tween(CrossfadeDuration),
                ) { status ->
                    Text(
                        text = stringResource(
                            when (status) {
                                SwapStatus.Pending -> Localization.swapping
                                SwapStatus.Done -> Localization.swap_status_swapped
                                SwapStatus.Failed -> Localization.swap_status_failed
                            }
                        ),
                        style = UIKit.typography.label2,
                        color = colors.text.primary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Text(
                    text = toast.amount,
                    style = UIKit.typography.body2,
                    color = colors.text.secondary,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(UIKitIcon.ic_chevron_right_16),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = colors.icon.secondary,
            )
        }
    }
}

private fun AnchoredDraggableState<Int>.updateSideAnchors(width: Int) {
    if (width > 0) {
        val outer = width * DismissThreshold * 2
        updateAnchors(DraggableAnchors { -1 at -outer; 0 at 0f; 1 at outer }, targetValue)
    }
}

private fun AnchoredDraggableState<Int>.fade(): Float {
    val outer = anchors.maxPosition()
    return if (outer > 0f) {
        1f - (abs(requireOffset()) / outer).coerceIn(0f, 1f)
    } else {
        1f
    }
}

private class SwipeGesture(private val settle: () -> FlingBehavior) : MutableInteractionSource, FlingBehavior {
    private var dragging by mutableStateOf(false)
    private var settling by mutableStateOf(false)
    val busy: Boolean
        get() = dragging || settling

    override val interactions: Flow<Interaction> = emptyFlow()

    override suspend fun emit(interaction: Interaction) {
        tryEmit(interaction)
    }

    override fun tryEmit(interaction: Interaction): Boolean {
        when (interaction) {
            is DragInteraction.Start -> dragging = true
            is DragInteraction.Stop, is DragInteraction.Cancel -> dragging = false
        }
        return true
    }

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        settling = true
        try {
            return with(settle()) { performFling(initialVelocity) }
        } finally {
            settling = false
        }
    }
}

@Immutable
private class OutcomeRing(val color: Color, val peak: Float)

@Stable
private class ToastOutcome(val plays: Boolean, iconScale: Float, iconAlpha: Float, time: Float) {
    val iconScale = Animatable(iconScale)
    val iconAlpha = Animatable(iconAlpha)
    val time = Animatable(time)
}

@Composable
private fun rememberToastOutcome(status: SwapStatus, outcomeDue: Boolean): ToastOutcome {
    val outcome = remember(status) {
        when {
            outcomeDue && status != SwapStatus.Pending -> ToastOutcome(
                plays = true,
                iconScale = IconStartScale,
                iconAlpha = 0f,
                time = 0f,
            )
            else -> ToastOutcome(plays = false, iconScale = 1f, iconAlpha = 1f, time = OutcomeDuration.toFloat())
        }
    }
    LaunchedEffect(outcome) {
        if (!outcome.plays) {
            return@LaunchedEffect
        }
        launch { outcome.iconScale.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 400f)) }
        launch { outcome.iconAlpha.animateTo(1f, tween(IconFadeDuration, easing = IconFadeEasing)) }
        outcome.time.animateTo(OutcomeDuration.toFloat(), tween(OutcomeDuration, easing = LinearEasing))
    }
    return outcome
}

@Composable
private fun rememberBeamAngle(): State<Float> {
    return rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(BeamTurnDuration, easing = LinearEasing)),
    )
}

private fun DrawScope.drawOutcomeRing(time: Float, peak: Float, brush: Brush, stroke: Stroke) {
    val alpha = when {
        time < RingStart -> 0f
        time < RingPeakStart -> peak * (time - RingStart) / (RingPeakStart - RingStart)
        time < RingPeakEnd -> peak
        else -> peak * (1f - (time - RingPeakEnd) / (OutcomeDuration - RingPeakEnd))
    }
    if (alpha <= 0f) {
        return
    }
    val growth = ((time - RingStart) / (OutcomeDuration - RingStart)).coerceIn(0f, 1f)
    val spread = RingSpread.toPx() * MoonToastEasing.transform(growth)
    drawPillStroke(brush, stroke, spread, alpha)
}

private fun DrawScope.drawPillStroke(brush: Brush, stroke: Stroke, spread: Float = 0f, alpha: Float = 1f) {
    val inset = stroke.width / 2f - spread
    val pillSize = Size(size.width - inset * 2f, size.height - inset * 2f)
    drawRoundRect(
        brush = brush,
        topLeft = Offset(inset, inset),
        size = pillSize,
        cornerRadius = CornerRadius(pillSize.height / 2f),
        alpha = alpha,
        style = stroke,
    )
}

private fun shakeOffset(time: Float): Float {
    val shakeTime = time - ShakeStart
    val step = (shakeTime / ShakeStep).toInt()
    if (shakeTime <= 0f || step >= ShakeOffsets.lastIndex) {
        return 0f
    }
    val fraction = ShakeEasing.transform((shakeTime - step * ShakeStep) / ShakeStep)
    return ShakeOffsets[step] + (ShakeOffsets[step + 1] - ShakeOffsets[step]) * fraction
}

@Composable
private fun OutcomeIcon(iconRes: Int, color: Color, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = modifier
            .size(LeadingSize)
            .background(color.copy(alpha = 0.12f), CircleShape)
            .padding(6.dp),
        tint = color,
    )
}

@Preview
@Composable
private fun SwapStatusToastPreview() {
    val gram = AssetEntity(id = "ton/mainnet/coin", name = "Gram", symbol = "GRAM", decimals = 9, imageUrl = "")
    val swap = SentSwap(
        walletId = "wallet",
        txHash = "4d1e1608",
        sell = gram,
        sendAmount = BigInteger.parseString("100000000000"),
        buy = gram,
        receiveAmount = BigInteger.parseString("100000000000"),
    )
    ThemedPreview {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SwapStatus.entries.forEach { status ->
                SwipeableToast(
                    toast = SwapStatusToast(swap, status, "100 GRAM"),
                    outcomeDue = false,
                    onClick = {},
                    onDismiss = {},
                )
            }
        }
    }
}
