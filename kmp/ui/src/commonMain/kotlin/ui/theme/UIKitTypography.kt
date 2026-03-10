package ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import ui.theme.resources.Res
import ui.theme.resources.montserrat_bold
import ui.theme.resources.montserrat_light
import ui.theme.resources.montserrat_medium
import ui.theme.resources.montserrat_regular
import ui.theme.resources.montserrat_semi_bold
import ui.theme.resources.roboto_mono

@Composable
private fun rememberMontserratFontFamily(): FontFamily {
    val light = Font(Res.font.montserrat_light, weight = FontWeight.Light)
    val normal = Font(Res.font.montserrat_regular, weight = FontWeight.Normal)
    val medium =  Font(Res.font.montserrat_medium, weight = FontWeight.Medium)
    val semibold = Font(Res.font.montserrat_semi_bold, weight = FontWeight.SemiBold)
    val bold = Font(Res.font.montserrat_bold, weight = FontWeight.Bold)
    return remember(light, normal, medium, semibold, bold) {
        FontFamily(light, normal, medium, semibold, bold)
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
    montserrat: FontFamily = rememberMontserratFontFamily(),
    mono: FontFamily = rememberRobotoMonoFamily(),
): UIKitTypography {
    return remember(montserrat, mono) {
        UIKitTypography(
            num1 = TextStyle(
                fontSize = 32.sp,
                fontFamily = montserrat,
                fontWeight = FontWeight.SemiBold,
                lineHeight = TextStyle.Default.lineHeight,
                letterSpacing = TextStyle.Default.letterSpacing
            ),
            num2 = TextStyle(
                fontSize = 28.sp,
                fontFamily = montserrat,
                fontWeight = FontWeight.SemiBold,
                lineHeight = TextStyle.Default.lineHeight,
                letterSpacing = TextStyle.Default.letterSpacing
            ),
            h1 = TextStyle(
                fontSize = 32.sp,
                fontFamily = montserrat,
                fontWeight = FontWeight.Bold,
                lineHeight = TextStyle.Default.lineHeight,
                letterSpacing = TextStyle.Default.letterSpacing
            ),
            h2 = TextStyle(
                fontSize = 24.sp,
                lineHeight = 32.sp,
                fontFamily = montserrat,
                fontWeight = FontWeight.Bold,
            ),
            h3 = TextStyle(
                fontSize = 20.sp,
                fontFamily = montserrat,
                fontWeight = FontWeight.Bold,
                lineHeight = TextStyle.Default.lineHeight,
                letterSpacing = TextStyle.Default.letterSpacing
            ),
            label1 = TextStyle(
                fontSize = 16.sp,
                fontFamily = montserrat,
                fontWeight = FontWeight.SemiBold,
                lineHeight = TextStyle.Default.lineHeight,
                letterSpacing = TextStyle.Default.letterSpacing
            ),
            label2 = TextStyle(
                fontSize = 14.sp,
                fontFamily = montserrat,
                fontWeight = FontWeight.SemiBold,
                lineHeight = TextStyle.Default.lineHeight,
                letterSpacing = TextStyle.Default.letterSpacing
            ),
            label3 = TextStyle(
                fontSize = 12.sp,
                fontFamily = montserrat,
                fontWeight = FontWeight.SemiBold,
                lineHeight = TextStyle.Default.lineHeight,
                letterSpacing = TextStyle.Default.letterSpacing
            ),
            body1 = TextStyle(
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontFamily = montserrat,
                fontWeight = FontWeight.Medium
            ),
            body2 = TextStyle(
                fontSize = 14.sp,
                fontFamily = montserrat,
                fontWeight = FontWeight.Medium,
                lineHeight = TextStyle.Default.lineHeight,
                letterSpacing = TextStyle.Default.letterSpacing
            ),
            body3 = TextStyle(
                fontSize = 12.sp,
                fontFamily = montserrat,
                fontWeight = FontWeight.Medium,
                lineHeight = TextStyle.Default.lineHeight,
                letterSpacing = TextStyle.Default.letterSpacing
            ),
            body4CAPS = TextStyle(
                fontSize = 10.sp,
                fontFamily = montserrat,
                fontWeight = FontWeight.SemiBold,
                lineHeight = TextStyle.Default.lineHeight,
                letterSpacing = TextStyle.Default.letterSpacing
            ),
            mono = TextStyle(
                fontSize = 16.sp,
                fontFamily = mono,
                fontWeight = FontWeight.Normal,
                lineHeight = 20.sp,
                letterSpacing = 0.5.sp
            )
        )
    }
}