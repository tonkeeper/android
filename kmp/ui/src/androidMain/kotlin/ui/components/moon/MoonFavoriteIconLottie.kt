package ui.components.moon

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import org.jetbrains.compose.resources.ExperimentalResourceApi
import ui.theme.resources.Res

private const val FavoriteLottieFile = "favorite_icon.json"
private const val LottieResourceDir = "files/lottie"

private object FavoriteIconKeyPaths {
    const val STAR_LAYER = "Слой 4 Outlines"
    const val BURST_LAYER = "Слой 4 Outlines 7"
    const val GROUP = "Group 1"
    const val FILL = "Fill 1"
    const val STROKE = "Stroke 1"
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun MoonFavoriteIconLottie(
    inactiveColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
) {
    val resourcePath = "$LottieResourceDir/$FavoriteLottieFile"
    val json by produceState<String?>(null, resourcePath) {
        value = Res.readBytes(resourcePath).decodeToString()
    }
    when (val j = json) {
        null -> Spacer(modifier)
        else -> {
            val spec = remember(j) { LottieCompositionSpec.JsonString(j) }
            val composition by rememberLottieComposition(spec)
            val dynamicProperties = rememberLottieDynamicProperties(
                rememberLottieDynamicProperty(
                    property = LottieProperty.COLOR,
                    FavoriteIconKeyPaths.STAR_LAYER,
                    FavoriteIconKeyPaths.GROUP,
                    FavoriteIconKeyPaths.FILL,
                ) { frameInfo ->
                    lerp(
                        start = inactiveColor,
                        stop = accentColor,
                        fraction = frameInfo.linearKeyframeProgress,
                    ).toArgb()
                },
                rememberLottieDynamicProperty(
                    property = LottieProperty.COLOR,
                    value = accentColor.toArgb(),
                    FavoriteIconKeyPaths.BURST_LAYER,
                    FavoriteIconKeyPaths.GROUP,
                    FavoriteIconKeyPaths.FILL,
                ),
                rememberLottieDynamicProperty(
                    property = LottieProperty.STROKE_COLOR,
                    value = accentColor.toArgb(),
                    FavoriteIconKeyPaths.BURST_LAYER,
                    FavoriteIconKeyPaths.GROUP,
                    FavoriteIconKeyPaths.STROKE,
                ),
            )
            if (progress != null) {
                LottieAnimation(
                    composition = composition,
                    progress = progress,
                    dynamicProperties = dynamicProperties,
                    modifier = modifier,
                )
            } else {
                LottieAnimation(
                    composition = composition,
                    dynamicProperties = dynamicProperties,
                    modifier = modifier,
                )
            }
        }
    }
}
