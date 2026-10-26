package com.tonapps.onboading.screens.networks

import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.core.components.painterResource
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.components.moon.ButtonColorsPrimary
import ui.components.moon.ButtonColorsSecondary
import ui.components.moon.ButtonSizeLarge
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonLoader
import ui.components.moon.MoonTopAppBarSimple
import ui.theme.Dimens
import ui.theme.MoonTheme
import ui.theme.UIKit
import ui.theme.appColorSchemeBlue
import kotlin.math.PI
import kotlin.math.round
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

sealed interface NewNetworksState {
    data object Loading : NewNetworksState

    // Only the chains that actually got added: the rest of the orbit stays grey.
    data class Added(val chains: List<Chain>) : NewNetworksState

    // Nothing was added (e.g. the backend didn't respond).
    data object Error : NewNetworksState
}

// Geometry of the Figma frame (390×844): every size below is scaled together with the orbit.
private const val ORBIT_DP = 368f
private const val ICON_ORBIT_RADIUS_DP = 136f // the middle ring
private const val ICON_MAX_DP = 64f
private const val ICON_MIN_DP = 36f
private const val LOGO_DP = 100f
private const val ORBIT_CENTER_OFFSET_DP = -92f // from the vertical centre of the screen
private const val TEXT_CENTER_OFFSET_DP = 162f
private const val CONFETTI_TOP_DP = 40f
private const val CONFETTI_HEIGHT_DP = 474f
private const val CONFETTI_AREA_TOP_DP = 168f // the Lottie halves are bottom-anchored in an 844dp frame
private const val CONFETTI_HALF_ASPECT_RATIO = 187f / 540f
// In this module's assets.
private const val CONFETTI_LEFT_FILE = "new_networks_confetti_left.json"
private const val CONFETTI_RIGHT_FILE = "new_networks_confetti_right.json"

// The first icon sits top-left like in the design, the rest follow clockwise.
private const val FIRST_ICON_ANGLE_DEG = -135f

// Same height as the large button that replaces the loader, so the bar doesn't jump.
private val LOADER_ROW_HEIGHT = 56.dp

// While loading the orbit turns calmly: once per LOOP_TURN_MS, reached by a short t² speed-up.
private const val LOOP_TURN_MS = 5_700
private const val LOOP_SPEED = 360f / LOOP_TURN_MS // degrees per millisecond
private const val SPIN_UP_MS = 800
private const val SPIN_UP_DEG = LOOP_SPEED * SPIN_UP_MS / 2f // t² ends exactly at the loop speed
private val SpinUpEasing = CubicBezierEasing(1f / 3f, 0f, 2f / 3f, 1f / 3f) // t²

// The finish is the orbit's turn from the design (one revolution in 4.8s on
// cubic-bezier(0.65, 0, 0.15, 1)), picked up where its speed equals the loop speed: 24.28% of the
// time, 7.58% of the turn. From there it speeds up to ~308°/s in ~0.8s, then slows down for ~2.8s.
private val DesignTurnEasing = CubicBezierEasing(0.65f, 0f, 0.15f, 1f)
private const val DESIGN_TURN_MS = 4_800
private const val FINISH_FROM_TIME = 0.2428f
private const val FINISH_FROM_PROGRESS = 0.0758f
private val FinishEasing = Easing {
    (DesignTurnEasing.transform(FINISH_FROM_TIME + it * (1f - FINISH_FROM_TIME)) - FINISH_FROM_PROGRESS) /
        (1f - FINISH_FROM_PROGRESS)
}
private val FINISH_MS = (DESIGN_TURN_MS * (1f - FINISH_FROM_TIME)).toInt()
private const val FINISH_DEG = 360f * (1f - FINISH_FROM_PROGRESS)

private const val SPIN_START_DELAY_MS = 600

// The result (colour, pulse, text, confetti) starts when the orbit visibly stops: with under 3° to
// go, the last 0.5s of the slow-down reads as standing still. Same as in the design.
private const val REVEAL_BEFORE_STOP_MS = 500
private const val COLOR_IN_MS = 500

// Figma's Easy Ease — fly-out, fades and every state swap.
private val EaseStandard = CubicBezierEasing(0.33f, 0f, 0.67f, 1f)

/**
 * Shown after the passcode when the user's wallets miss chains the app now supports. The orbit
 * holds one icon per chain in [chains] (icons shrink as their number grows), spins while
 * [state] is [NewNetworksState.Loading] and stops in its resting position before revealing
 * the result: the added chains turn from grey to colour, confetti drops and [continueText] appears.
 * The close button lets the user leave in any state, the loading one included; back is swallowed,
 * so the screen is left only through its buttons.
 */
@Composable
fun NewNetworksScreen(
    chains: List<Chain>,
    state: NewNetworksState,
    continueText: String,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NewNetworksContent(
        chains = chains,
        state = state,
        continueText = continueText,
        onContinue = onContinue,
        onRetry = onRetry,
        onClose = onClose,
        motion = !rememberAnimationsDisabled(),
        modifier = modifier,
    )
    // Also stops the predictive back gesture from previewing the screen underneath.
    BackHandler {}
}

// With system animations off the design asks for no fly-out, rotation or confetti.
@Composable
private fun rememberAnimationsDisabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

private class OrbitMotion(count: Int, motion: Boolean) {
    private val start = if (motion) 0f else 1f

    val rings = Animatable(start)
    val logoScale = Animatable(1f)
    val rotation = Animatable(0f) // degrees
    val fly = List(count) { Animatable(start) }
    val pop = List(count) { Animatable(1f) }
    val colored = List(count) { Animatable(0f) }
    val confetti = Animatable(0f)

    var introPlayed = !motion
}

@Composable
private fun NewNetworksContent(
    chains: List<Chain>,
    state: NewNetworksState,
    continueText: String,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    motion: Boolean,
    modifier: Modifier = Modifier,
) {
    val orbit = remember(chains.size, motion) { OrbitMotion(chains.size, motion) }
    // What the text, buttons and icon colours show; with motion it lags behind [state] until the
    // orbit settles. Without motion it is [state] itself, so nothing waits on an effect.
    var settled by remember { mutableStateOf<NewNetworksState>(NewNetworksState.Loading) }
    val shown = if (motion) settled else state
    val stagger = staggerMs(chains.size)
    val addedNow = (state as? NewNetworksState.Added)?.chains?.toSet().orEmpty()
    val coloredOf: (Int) -> Float = if (motion) {
        { index -> orbit.colored[index].value }
    } else {
        { index -> if (chains[index] in addedNow) 1f else 0f }
    }

    LaunchedEffect(orbit) {
        if (!orbit.introPlayed) {
            orbit.playIntro(stagger)
        }
    }

    LaunchedEffect(orbit, state) {
        if (!motion) {
            return@LaunchedEffect
        }
        when (state) {
            NewNetworksState.Loading -> {
                settled = state
                orbit.colored.forEach { it.snapTo(0f) }
                orbit.confetti.snapTo(0f)
                orbit.spin()
            }

            is NewNetworksState.Added,
            NewNetworksState.Error -> coroutineScope {
                val untilStop = orbit.finishSpin(this, chains.size)
                delay((untilStop - REVEAL_BEFORE_STOP_MS).coerceAtLeast(0))
                settled = state
                if (state is NewNetworksState.Added) {
                    val added = state.chains.toSet()
                    orbit.reveal(chains.indices.filter { chains[it] in added })
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(UIKit.colorScheme.background.page),
    ) {
        val scale = minOf(1f, (maxWidth - 22.dp) / ORBIT_DP.dp, maxHeight / 760.dp)

        // Composed from the start so the Lottie files are parsed before the success moment.
        if (motion) {
            Confetti(
                play = shown is NewNetworksState.Added,
                progress = { orbit.confetti.value },
                scale = scale,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        Orbit(
            chains = chains,
            orbit = orbit,
            coloredOf = coloredOf,
            scale = scale,
            motion = motion,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (ORBIT_CENTER_OFFSET_DP * scale).dp),
        )

        StatusText(
            chains = chains,
            shown = shown,
            motion = motion,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (TEXT_CENTER_OFFSET_DP * scale).dp),
        )

        Actions(
            shown = shown,
            continueText = continueText,
            onContinue = onContinue,
            onRetry = onRetry,
            motion = motion,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        MoonTopAppBarSimple(
            title = "",
            actionIconRes = UIKitIcon.ic_close_16,
            onActionClick = onClose,
            windowInsets = WindowInsets.statusBars,
            backgroundColor = Color.Transparent,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

// 60ms between icons as in the design, squeezed so a long list still finishes in time.
private fun staggerMs(count: Int): Int {
    return if (count <= 5) 60 else 240 / (count - 1)
}

private suspend fun OrbitMotion.playIntro(stagger: Int) = coroutineScope {
    launch { rings.animateTo(1f, tween(800, delayMillis = 150, easing = EaseStandard)) }
    launch {
        delay(100)
        logoScale.animateTo(0.94f, tween(200, easing = EaseStandard))
        logoScale.animateTo(1f, tween(350, easing = EaseStandard))
    }
    fly.forEachIndexed { index, animatable ->
        launch { animatable.animateTo(1f, tween(700, delayMillis = 250 + index * stagger, easing = EaseStandard)) }
    }
    introPlayed = true
}

// Runs until cancelled by the next state.
private suspend fun OrbitMotion.spin() {
    if (rotation.value == 0f) {
        delay(SPIN_START_DELAY_MS.toLong()) // starts while the icons are still flying out
    }
    rotation.animateTo(rotation.value + SPIN_UP_DEG, tween(SPIN_UP_MS, easing = SpinUpEasing))
    while (true) {
        rotation.animateTo(rotation.value + 360f, tween(LOOP_TURN_MS, easing = LinearEasing))
    }
}

// Plays the design's turn from the loop speed: a speed-up, then the long slow-down into the resting
// layout, which N evenly spaced icons repeat every 360/N degrees. The distance is stretched or
// squeezed to the nearest resting layout (at most half a gap, ±13% speed with four icons).
// Launched in [scope] so the caller can time the reveal; returns the ms until the stop.
private fun OrbitMotion.finishSpin(scope: CoroutineScope, count: Int): Long {
    val current = rotation.value
    val period = 360f / count.coerceAtLeast(1)
    val target = round((current + FINISH_DEG) / period) * period
    scope.launch { rotation.animateTo(target, tween(FINISH_MS, easing = FinishEasing)) }
    return FINISH_MS.toLong()
}

private suspend fun OrbitMotion.reveal(indices: List<Int>) = coroutineScope {
    indices.forEach { index ->
        // All together, the pulse along with the colour.
        launch { colored[index].animateTo(1f, tween(COLOR_IN_MS, easing = EaseStandard)) }
        launch {
            pop[index].animateTo(1.15f, tween(180, easing = EaseStandard))
            pop[index].animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
        }
    }
    launch {
        logoScale.animateTo(1.08f, tween(200, easing = EaseStandard))
        logoScale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
    }
    launch { confetti.animateTo(1f, tween(1_100, easing = EaseStandard)) }
}

@Composable
private fun Orbit(
    chains: List<Chain>,
    orbit: OrbitMotion,
    coloredOf: (Int) -> Float,
    scale: Float,
    motion: Boolean,
    modifier: Modifier = Modifier,
) {
    val separator = UIKit.colorScheme.separator.common
    val breathing: State<Float> = if (motion) {
        val transition = rememberInfiniteTransition(label = "orbit")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.02f,
            animationSpec = infiniteRepeatable(tween(2_000, easing = EaseStandard), RepeatMode.Reverse),
            label = "breathing",
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    val iconSize = iconSizeDp(chains.size) * scale
    val radiusPx = with(LocalDensity.current) { (ICON_ORBIT_RADIUS_DP * scale).dp.toPx() }
    val painters = chains.map { it.painterResource() }
    val blurSupported = motion && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(
        modifier = modifier.size((ORBIT_DP * scale).dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val rings = orbit.rings.value
            val ringScale = 0.6f + 0.4f * rings
            val stroke = Stroke(width = 1.dp.toPx())
            val unit = size.minDimension / ORBIT_DP
            listOf(
                Triple(ORBIT_DP, 0.5f, 1f),
                Triple(272f, 0.75f, breathing.value),
                Triple(176f, 1f, breathing.value),
            ).forEach { (diameter, alpha, breath) ->
                drawCircle(
                    color = separator.copy(alpha = separator.alpha * alpha * rings),
                    radius = diameter / 2f * unit * ringScale * breath - stroke.width / 2f,
                    style = stroke,
                )
            }
        }

        painters.forEachIndexed { index, painter ->
            val layerModifier = Modifier.graphicsLayer {
                val fly = orbit.fly[index].value
                val angle = (FIRST_ICON_ANGLE_DEG + index * 360f / chains.size + orbit.rotation.value) * PI / 180.0
                translationX = (cos(angle) * radiusPx * fly).toFloat()
                translationY = (sin(angle) * radiusPx * fly).toFloat()
                val iconScale = (0.3f + 0.7f * fly) * orbit.pop[index].value
                scaleX = iconScale
                scaleY = iconScale
            }
            Box(layerModifier) {
                val colored = coloredOf(index)
                if (colored > 0f) {
                    ChainIcon(painter = painter, size = iconSize, colorFilter = null)
                }
                val fly = orbit.fly[index].value
                val greyBlur = if (blurSupported) (14f * (1f - fly)).dp else 0.dp
                GreyChainIcon(
                    painter = painter,
                    size = iconSize,
                    modifier = Modifier
                        .graphicsLayer { alpha = (fly * 2f).coerceAtMost(1f) * (1f - colored) }
                        .then(if (greyBlur > 0.dp) Modifier.blur(greyBlur) else Modifier),
                )
            }
        }

        // Above the icons: they fly out from under the logo.
        Icon(
            painter = painterResource(UIKitIcon.ic_logo_128),
            contentDescription = null,
            tint = UIKit.colorScheme.icon.primary,
            modifier = Modifier
                .size((LOGO_DP * scale).dp)
                .graphicsLayer {
                    scaleX = orbit.logoScale.value
                    scaleY = orbit.logoScale.value
                },
        )
    }
}

// 64dp up to four chains, then shrinking with the count so a crowded orbit still breathes.
private fun iconSizeDp(count: Int): Float {
    if (count <= 4) {
        return ICON_MAX_DP
    }
    return (ICON_MAX_DP * sqrt(4f / count)).coerceIn(ICON_MIN_DP, ICON_MAX_DP)
}

@Composable
private fun ChainIcon(
    painter: Painter,
    size: Float,
    colorFilter: ColorFilter?,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painter,
        contentDescription = null,
        colorFilter = colorFilter,
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape),
    )
}

// The grey loading look from the design: a content-tint disc with the chain's glyph in the
// secondary icon colour. The glyph is told apart from the logo's background by luminance; logos
// with a light background (Polygon, Robinhood) are inverted first so their dark glyph lights up.
@Composable
private fun GreyChainIcon(
    painter: Painter,
    size: Float,
    modifier: Modifier = Modifier,
) {
    val glyphColor = UIKit.colorScheme.icon.secondary
    val lightBackground = rememberHasLightBackground(painter)
    val glyphFilter = remember(glyphColor, lightBackground) { glyphFilter(glyphColor, lightBackground) }
    Image(
        painter = painter,
        contentDescription = null,
        colorFilter = glyphFilter,
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(UIKit.colorScheme.background.contentTint),
    )
}

// Luminance above the threshold becomes the glyph; the steep contrast keeps the background out.
private const val GLYPH_LUMINANCE_THRESHOLD = 0.45f
private const val GLYPH_CONTRAST = 4f

private fun glyphFilter(color: Color, invert: Boolean): ColorFilter {
    val contrast = if (invert) -GLYPH_CONTRAST else GLYPH_CONTRAST
    val offset = if (invert) {
        (1f - GLYPH_LUMINANCE_THRESHOLD) * GLYPH_CONTRAST
    } else {
        -GLYPH_LUMINANCE_THRESHOLD * GLYPH_CONTRAST
    }
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0f, 0f, 0f, 0f, color.red * 255f,
                0f, 0f, 0f, 0f, color.green * 255f,
                0f, 0f, 0f, 0f, color.blue * 255f,
                0.2126f * contrast, 0.7152f * contrast, 0.0722f * contrast, 0f, offset * 255f,
            ),
        ),
    )
}

// Renders the logo once at a small size and averages the luminance of a ring near its edge, where
// only the background is: a big glyph (Avalanche) would skew an average over the whole logo.
@Composable
private fun rememberHasLightBackground(painter: Painter): Boolean {
    val density = LocalDensity.current
    return remember(painter, density) {
        val side = 48
        val bitmap = ImageBitmap(side, side)
        CanvasDrawScope().draw(
            density = density,
            layoutDirection = LayoutDirection.Ltr,
            canvas = androidx.compose.ui.graphics.Canvas(bitmap),
            size = Size(side.toFloat(), side.toFloat()),
        ) {
            with(painter) { draw(size) }
        }
        val pixels = IntArray(side * side)
        bitmap.readPixels(pixels)
        var sum = 0f
        var count = 0
        val center = side / 2f
        for ((index, pixel) in pixels.withIndex()) {
            val dx = index % side + 0.5f - center
            val dy = index / side + 0.5f - center
            val distance = sqrt(dx * dx + dy * dy) / center
            if (distance !in 0.8f..0.95f || (pixel ushr 24) and 0xFF < 128) continue
            val red = (pixel shr 16 and 0xFF) / 255f
            val green = (pixel shr 8 and 0xFF) / 255f
            val blue = (pixel and 0xFF) / 255f
            sum += 0.2126f * red + 0.7152f * green + 0.0722f * blue
            count++
        }
        count > 0 && sum / count > 0.5f
    }
}

@Composable
private fun Confetti(
    play: Boolean,
    progress: () -> Float,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(top = (CONFETTI_TOP_DP * scale).dp)
            .fillMaxWidth()
            .height((CONFETTI_HEIGHT_DP * scale).dp)
            .clipToBounds()
            .graphicsLayer {
                val value = progress()
                alpha = value
                translationY = -60.dp.toPx() * scale * (1f - value)
            },
    ) {
        Row(Modifier.offset(y = (CONFETTI_AREA_TOP_DP * scale).dp)) {
            ConfettiHalf(CONFETTI_LEFT_FILE, play, Modifier.weight(1f))
            ConfettiHalf(CONFETTI_RIGHT_FILE, play, Modifier.weight(1f))
        }
    }
}

// Plays once and holds the last frame.
@Composable
private fun ConfettiHalf(fileName: String, play: Boolean, modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset(fileName))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = play,
        restartOnPlay = true,
        iterations = 1,
    )
    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier.aspectRatio(CONFETTI_HALF_ASPECT_RATIO),
    )
}

// Outgoing content blurs to 6dp while it fades up, incoming content sharpens from 6dp (Android 12+).
@Composable
private fun AnimatedVisibilityScope.transitionBlur(max: Dp, motion: Boolean): Modifier {
    if (!motion || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return Modifier
    }
    val blur by transition.animateDp(label = "blur") { if (it == EnterExitState.Visible) 0.dp else max }
    return if (blur > 0.dp) Modifier.blur(blur) else Modifier
}

@Composable
private fun StatusText(
    chains: List<Chain>,
    shown: NewNetworksState,
    motion: Boolean,
    modifier: Modifier = Modifier,
) {
    val offset = with(LocalDensity.current) { 8.dp.roundToPx() }
    AnimatedContent(
        targetState = shown,
        contentKey = { it::class },
        transitionSpec = {
            (fadeIn(tween(400, delayMillis = 100, easing = EaseStandard)) +
                slideInVertically(tween(400, delayMillis = 100, easing = EaseStandard)) { offset })
                .togetherWith(
                    fadeOut(tween(250, easing = EaseStandard)) +
                        slideOutVertically(tween(250, easing = EaseStandard)) { -offset },
                )
        },
        label = "text",
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) { target ->
        val title = when (target) {
            NewNetworksState.Loading -> stringResource(Localization.new_networks_loading_title)
            is NewNetworksState.Added -> stringResource(Localization.new_networks_added_title)
            NewNetworksState.Error -> stringResource(Localization.new_networks_error_title)
        }
        val subtitle = when (target) {
            NewNetworksState.Loading -> stringResource(Localization.new_networks_loading_subtitle)
            is NewNetworksState.Added -> addedSubtitle(target.chains.ifEmpty { chains })
            NewNetworksState.Error -> stringResource(Localization.new_networks_error_subtitle)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(transitionBlur(6.dp, motion))
                .padding(start = Dimens.offsetLarge, end = Dimens.offsetLarge, bottom = Dimens.offsetMedium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.offsetExtraSmall),
        ) {
            Text(
                text = title,
                style = UIKit.typography.h2,
                color = UIKit.colorScheme.text.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = subtitle,
                style = UIKit.typography.body1,
                color = UIKit.colorScheme.text.secondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// "Solana, Robinhood, Polygon and Avalanche are now in your wallet."
@Composable
private fun addedSubtitle(chains: List<Chain>): String {
    val names = chains.map { it.name }
    val list = when (names.size) {
        0 -> ""
        1 -> names.first()
        else -> stringResource(
            Localization.new_networks_list_last,
            names.dropLast(1).joinToString(", "),
            names.last(),
        )
    }
    return if (names.size == 1) {
        stringResource(Localization.new_networks_added_subtitle_one, list)
    } else {
        stringResource(Localization.new_networks_added_subtitle_many, list)
    }
}

@Composable
private fun Actions(
    shown: NewNetworksState,
    continueText: String,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    motion: Boolean,
    modifier: Modifier = Modifier,
) {
    val offset = with(LocalDensity.current) { 16.dp.roundToPx() }
    AnimatedContent(
        targetState = shown,
        contentKey = { it::class },
        transitionSpec = {
            (fadeIn(tween(400, delayMillis = 150, easing = EaseStandard)) +
                slideInVertically(tween(400, delayMillis = 150, easing = EaseStandard)) { offset })
                .togetherWith(fadeOut(tween(200, easing = EaseStandard)))
        },
        label = "actions",
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) { target ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(transitionBlur(4.dp, motion))
                .padding(Dimens.offsetMedium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.offsetMedium),
        ) {
            when (target) {
                NewNetworksState.Loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(LOADER_ROW_HEIGHT),
                    contentAlignment = Alignment.Center,
                ) {
                    MoonLoader(Modifier.size(24.dp))
                }

                is NewNetworksState.Added -> MoonAccentButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = continueText,
                    size = ButtonSizeLarge,
                    buttonColors = ButtonColorsPrimary,
                    onClick = onContinue,
                )

                NewNetworksState.Error -> {
                    MoonAccentButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(Localization.try_again),
                        size = ButtonSizeLarge,
                        buttonColors = ButtonColorsPrimary,
                        onClick = onRetry,
                    )
                    // The missing chains keep syncing on the next unlock.
                    MoonAccentButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = continueText,
                        size = ButtonSizeLarge,
                        buttonColors = ButtonColorsSecondary,
                        onClick = onContinue,
                    )
                }
            }
        }
    }
}

private fun previewChains(count: Int): List<Chain> {
    val all = Chain.all.filter { it.network.mode == Network.Mode.Mainnet }
    return List(count) { all[it % all.size] }
}

@Composable
private fun PreviewHost(content: @Composable () -> Unit) {
    MoonTheme(appColorSchemeBlue()) {
        Box(Modifier.background(UIKit.colorScheme.background.page)) {
            content()
        }
    }
}

/**
 * Run in interactive mode (or on a device via "Run preview"): the screen loads for 5 seconds,
 * then reveals the result. Tap the button to start over; every other run fails to show the
 * error state, whose "Try again" restarts loading.
 */
@Preview(name = "Flow · 4 chains", widthDp = 390, heightDp = 844)
@Composable
private fun NewNetworksFlowPreview() {
    NewNetworksFlowPreview(count = 4)
}

@Preview(name = "Flow · 8 chains", widthDp = 390, heightDp = 844)
@Composable
private fun NewNetworksFlow8Preview() {
    NewNetworksFlowPreview(count = 8)
}

@Composable
private fun NewNetworksFlowPreview(count: Int) {
    val chains = remember { previewChains(count) }
    var state by remember { mutableStateOf<NewNetworksState>(NewNetworksState.Loading) }
    var runs by remember { mutableStateOf(0) }
    LaunchedEffect(runs) {
        state = NewNetworksState.Loading
        delay(5_000)
        state = if (runs % 2 == 0) NewNetworksState.Added(chains) else NewNetworksState.Error
    }
    PreviewHost {
        NewNetworksContent(
            chains = chains,
            state = state,
            continueText = stringResource(Localization.new_networks_open_wallet),
            onContinue = { runs++ },
            onRetry = { runs++ },
            onClose = {},
            motion = true,
        )
    }
}

@Preview(name = "Loading · 4 chains", widthDp = 390, heightDp = 844)
@Composable
private fun NewNetworksLoadingPreview() {
    PreviewHost {
        NewNetworksContent(
            chains = previewChains(4),
            state = NewNetworksState.Loading,
            continueText = "Open wallet",
            onContinue = {},
            onRetry = {},
            onClose = {},
            motion = false,
        )
    }
}

@Preview(name = "Added · 6 chains", widthDp = 390, heightDp = 844)
@Composable
private fun NewNetworksAddedPreview() {
    val chains = previewChains(6)
    PreviewHost {
        NewNetworksContent(
            chains = chains,
            state = NewNetworksState.Added(chains),
            continueText = "Open wallet",
            onContinue = {},
            onRetry = {},
            onClose = {},
            motion = false,
        )
    }
}

// The first chain failed to register: it stays grey and is left out of the subtitle.
@Preview(name = "Added · 6 chains, first one failed", widthDp = 390, heightDp = 844)
@Composable
private fun NewNetworksPartlyAddedPreview() {
    val chains = previewChains(6)
    PreviewHost {
        NewNetworksContent(
            chains = chains,
            state = NewNetworksState.Added(chains.drop(1)),
            continueText = "Open wallet",
            onContinue = {},
            onRetry = {},
            onClose = {},
            motion = false,
        )
    }
}

@Preview(name = "Error · 8 chains", widthDp = 390, heightDp = 844)
@Composable
private fun NewNetworksErrorPreview() {
    PreviewHost {
        NewNetworksContent(
            chains = previewChains(8),
            state = NewNetworksState.Error,
            continueText = "Open wallet",
            onContinue = {},
            onRetry = {},
            onClose = {},
            motion = false,
        )
    }
}

@Preview(name = "Loading · 2 chains, small screen", widthDp = 320, heightDp = 640)
@Composable
private fun NewNetworksSmallPreview() {
    PreviewHost {
        NewNetworksContent(
            chains = previewChains(2),
            state = NewNetworksState.Loading,
            continueText = "Open wallet",
            onContinue = {},
            onRetry = {},
            onClose = {},
            motion = false,
        )
    }
}
