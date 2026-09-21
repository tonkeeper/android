package ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import ui.theme.resources.Res
import ui.theme.resources.roboto_mono
import ui.theme.resources.tt_firs_neue_demi_bold
import ui.theme.resources.tt_firs_neue_medium
import ui.theme.resources.tt_firs_neue_normal

private const val NUM_AND_HEADING_FONT_FEATURE_SETTINGS = "'ss09' on, 'ss17' on, 'ss18' on"
private const val TEXT_FONT_FEATURE_SETTINGS = "'ss07' on, $NUM_AND_HEADING_FONT_FEATURE_SETTINGS"

@Composable
private fun rememberTTFirsNeueFontFamily(): FontFamily {
    val normal = Font(Res.font.tt_firs_neue_normal, weight = FontWeight.Normal)
    val medium =  Font(Res.font.tt_firs_neue_medium, weight = FontWeight.Medium)
    val semibold = Font(Res.font.tt_firs_neue_demi_bold, weight = FontWeight.SemiBold)
    val bold = Font(Res.font.tt_firs_neue_demi_bold, weight = FontWeight.Bold)
    return remember(normal, medium, semibold, bold) {
        FontFamily(normal, medium, semibold, bold)
    }
}

@Composable
private fun rememberRobotoMonoFamily(): FontFamily {
    val mono = Font(Res.font.roboto_mono, weight = FontWeight.Normal)
    return remember(mono) { FontFamily(mono) }
}

@Immutable
data class UIKitTypography(
    val num1: TextStyle,
    val num2: TextStyle,
    val h1: TextStyle,
    val h2: TextStyle,
    val h3: TextStyle,
    val label1: TextStyle,
    val label2: TextStyle,
    val label3: TextStyle,
    val body1: TextStyle,
    val body2: TextStyle,
    val body3: TextStyle,
    val body4CAPS: TextStyle,
    val mono: TextStyle,
)

internal val LocalTypography = staticCompositionLocalOf<UIKitTypography> {
    error("No AppTypography provided")
}

@Composable
internal fun rememberAppTypography(
    ttFirsNeue: FontFamily = rememberTTFirsNeueFontFamily(),
    mono: FontFamily = rememberRobotoMonoFamily(),
): UIKitTypography {
    return remember(ttFirsNeue, mono) {
        UIKitTypography(
            num1 = TextStyle(
                fontSize = 32.sp,
                fontFamily = ttFirsNeue,
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = NUM_AND_HEADING_FONT_FEATURE_SETTINGS,
                lineHeight = TextStyle.Default.lineHeight,
                letterSpacing = TextStyle.Default.letterSpacing
            ),
            num2 = TextStyle(
                fontSize = 28.sp,
                fontFamily = ttFirsNeue,
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = NUM_AND_HEADING_FONT_FEATURE_SETTINGS,
                lineHeight = TextStyle.Default.lineHeight,
                letterSpacing = TextStyle.Default.letterSpacing
            ),
            h1 = TextStyle(
                fontSize = 32.sp,
                fontFamily = ttFirsNeue,
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = NUM_AND_HEADING_FONT_FEATURE_SETTINGS,
                lineHeight = TextStyle.Default.lineHeight,
                letterSpacing = TextStyle.Default.letterSpacing
            ),
            h2 = TextStyle(
                fontSize = 24.sp,
                lineHeight = 32.sp,
                fontFamily = ttFirsNeue,
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = NUM_AND_HEADING_FONT_FEATURE_SETTINGS,
            ),
            h3 = TextStyle(
                fontSize = 20.sp,
                fontFamily = ttFirsNeue,
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = NUM_AND_HEADING_FONT_FEATURE_SETTINGS,
                lineHeight = TextStyle.Default.lineHeight,
                letterSpacing = TextStyle.Default.letterSpacing
            ),
            label1 = TextStyle(
                fontSize = 16.sp,
                fontFamily = ttFirsNeue,
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = TEXT_FONT_FEATURE_SETTINGS,
                lineHeight = TextStyle.Default.lineHeight,
                letterSpacing = 0.005.em
            ),
            label2 = TextStyle(
                fontSize = 14.sp,
                fontFamily = ttFirsNeue,
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = TEXT_FONT_FEATURE_SETTINGS,
                lineHeight = TextStyle.Default.lineHeight,
                letterSpacing = 0.005.em
            ),
            label3 = TextStyle(
                fontSize = 12.sp,
                fontFamily = ttFirsNeue,
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = TEXT_FONT_FEATURE_SETTINGS,
                lineHeight = TextStyle.Default.lineHeight,
                letterSpacing = 0.01.em
            ),
            body1 = TextStyle(
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontFamily = ttFirsNeue,
                fontWeight = FontWeight.Normal,
                fontFeatureSettings = TEXT_FONT_FEATURE_SETTINGS,
                letterSpacing = 0.005.em
            ),
            body2 = TextStyle(
                fontSize = 14.sp,
                fontFamily = ttFirsNeue,
                fontWeight = FontWeight.Normal,
                fontFeatureSettings = TEXT_FONT_FEATURE_SETTINGS,
                lineHeight = 20.sp,
                letterSpacing = 0.005.em
            ),
            body3 = TextStyle(
                fontSize = 12.sp,
                fontFamily = ttFirsNeue,
                fontWeight = FontWeight.Normal,
                fontFeatureSettings = TEXT_FONT_FEATURE_SETTINGS,
                lineHeight = TextStyle.Default.lineHeight,
                letterSpacing = 0.01.em
            ),
            body4CAPS = TextStyle(
                fontSize = 10.sp,
                fontFamily = ttFirsNeue,
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = TEXT_FONT_FEATURE_SETTINGS,
                lineHeight = TextStyle.Default.lineHeight,
                letterSpacing = 0.01.em
            ),
            mono = TextStyle(
                fontSize = 16.sp,
                fontFamily = mono,
                fontWeight = FontWeight.Medium,
                lineHeight = 24.sp,
                letterSpacing = 0.5.sp
            ),
        )
    }
}
