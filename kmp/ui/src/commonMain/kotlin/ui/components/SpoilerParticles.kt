package ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.*
import kotlin.random.Random

@Composable
fun SpoilerParticles(
    modifier: Modifier = Modifier,
    particleCount: Int = 2000,
    pointRadius: Dp = 1.dp,
    color: Color = Color.White,
    bucketAlphas: FloatArray = floatArrayOf(0.30f, 0.60f, 1.00f),
    simHz: Int = 30,
    minLifetimeMs: Int = 1000,
    maxLifetimeMs: Int = 3000,
    minSpeed: Float = 4f,
    maxSpeed: Float = 10f,
    seed: Int = 1337,
    edgeFeather: Dp = 10.dp,
    edgeJitter: Float = 0.15f
) {
    val buckets = bucketAlphas.size
    require(buckets >= 1)

    val rnd = remember(seed, particleCount) { Random(seed) }
    val px = remember(particleCount) { FloatArray(particleCount) }
    val py = remember(particleCount) { FloatArray(particleCount) }
    val vx = remember(particleCount) { FloatArray(particleCount) }
    val vy = remember(particleCount) { FloatArray(particleCount) }
    val lifeMs = remember(particleCount) { IntArray(particleCount) }
    val timeMs = remember(particleCount) { IntArray(particleCount) }
    val bucket = remember(particleCount) { IntArray(particleCount) }

    val points = remember(particleCount, buckets) {
        Array(buckets) { ArrayList<Offset>(particleCount / buckets + 64) }
    }

    var resetPending by remember { mutableStateOf(true) }

    fun resetOne(i: Int, w: Float, h: Float) {
        px[i] = rnd.nextFloat() * w
        py[i] = rnd.nextFloat() * h
        val ang = (rnd.nextFloat() * (PI * 2)).toFloat()
        val speed = minSpeed + rnd.nextFloat() * (maxSpeed - minSpeed)
        vx[i] = cos(ang) * speed
        vy[i] = sin(ang) * speed
        val life = minLifetimeMs + rnd.nextInt(max(1, maxLifetimeMs - minLifetimeMs))
        lifeMs[i] = life
        timeMs[i] = rnd.nextInt(life)
        bucket[i] = rnd.nextInt(buckets)
    }

    fun resetAll(w: Float, h: Float) {
        for (i in 0 until particleCount) resetOne(i, w, h)
    }

    val progress by rememberInfiniteTransition(label = "clock").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing)
        ),
        label = "clockAnim"
    )
    var lastProgress by remember { mutableFloatStateOf(0f) }
    var accumulator by remember { mutableFloatStateOf(0f) }
    val stepSec = 1f / simHz.coerceAtLeast(1)

    Box(
        modifier = modifier.onSizeChanged {
            resetPending = true
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            if (resetPending) {
                resetAll(w, h)
                accumulator = 0f
                lastProgress = progress
                resetPending = false
            }

            var dp = progress - lastProgress
            if (dp < 0f) dp += 1f
            val dt = dp * 1.0f
            accumulator = (accumulator + dt).coerceAtMost(0.25f)

            while (accumulator >= stepSec) {
                val ms = (stepSec * 1000f).toInt()
                for (i in 0 until particleCount) {
                    val tms = timeMs[i] + ms
                    if (tms >= lifeMs[i]) {
                        resetOne(i, w, h); continue
                    }
                    timeMs[i] = tms

                    var x = px[i] + vx[i] * stepSec
                    var y = py[i] + vy[i] * stepSec

                    if (x < 0f) x += w else if (x >= w) x -= w
                    if (y < 0f) y += h else if (y >= h) y -= h

                    px[i] = x; py[i] = y
                }
                accumulator -= stepSec
            }
            lastProgress = progress

            val stroke = pointRadius.toPx().coerceAtLeast(1f)
            val featherPx = edgeFeather.toPx().coerceAtLeast(1f)

            for (b in 0 until buckets) points[b].clear()

            fun hash01(i: Int): Float {
                var x = i
                x = x xor (x shl 13)
                x = x xor (x ushr 17)
                x = x xor (x shl 5)
                return ((x ushr 1) / Int.MAX_VALUE.toFloat())
            }

            fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
                val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
                return t * t * (3 - 2 * t)
            }

            val lifeStride = max(1, (1000f / simHz).toInt())

            for (i in 0 until particleCount) {
                val x = px[i]; val y = py[i]

                val distToEdge = min(min(x, w - x), min(y, h - y))
                var weight = smoothstep(0f, featherPx, distToEdge)

                val salt = (timeMs[i] / lifeStride)
                val jitter = (hash01(i * 7349 + salt) - 0.5f) * 2f * edgeJitter
                weight = (weight + jitter).coerceIn(0f, 1f)

                val gate = hash01(i * 9176 + salt * 13)
                if (gate <= weight) {
                    points[bucket[i]].add(Offset(x, y))
                }
            }

            for (b in 0 until buckets) {
                val pts = points[b]
                if (pts.isEmpty()) continue
                drawPoints(
                    points = pts,
                    pointMode = PointMode.Points,
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                    color = color.copy(alpha = bucketAlphas[b].coerceIn(0f, 1f))
                )
            }
        }
    }
}
