package ui.components.moon

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import org.jetbrains.compose.resources.ExperimentalResourceApi
import ui.theme.resources.Res

private const val LottieResourceDir = "files/lottie"

@OptIn(ExperimentalResourceApi::class)
@Composable
actual fun MoonLottie(
    fileName: String,
    modifier: Modifier,
    iterations: Int,
    contentDescription: String?,
    color: Color?,
    progress: (() -> Float)?,
) {
    if ('/' in fileName || '\\' in fileName) {
        Spacer(modifier)
        return
    }
    val resourcePath = "$LottieResourceDir/$fileName"
    val json by produceState<String?>(null, resourcePath) {
        value = Res.readBytes(resourcePath).decodeToString()
    }
    when (val j = json) {
        null -> Spacer(modifier)
        else -> {
            val spec = remember(j) { LottieCompositionSpec.JsonString(j) }
            val composition by rememberLottieComposition(spec)
            val dynamicProperties = if (color != null) {
                rememberLottieDynamicProperties(
                    rememberLottieDynamicProperty(
                        property = LottieProperty.COLOR_FILTER,
                        value = PorterDuffColorFilter(color.toArgb(), PorterDuff.Mode.SRC_ATOP),
                        "**",
                    ),
                )
            } else {
                null
            }
            if (progress != null) {
                LottieAnimation(
                    composition = composition,
                    progress = progress,
                    dynamicProperties = dynamicProperties,
                    modifier = modifier.then(
                        if (contentDescription != null) {
                            Modifier.semantics { this.contentDescription = contentDescription }
                        } else {
                            Modifier
                        },
                    ),
                )
            } else {
                LottieAnimation(
                    composition = composition,
                    iterations = iterations,
                    dynamicProperties = dynamicProperties,
                    modifier = modifier.then(
                        if (contentDescription != null) {
                            Modifier.semantics { this.contentDescription = contentDescription }
                        } else {
                            Modifier
                        },
                    ),
                )
            }
        }
    }
}
