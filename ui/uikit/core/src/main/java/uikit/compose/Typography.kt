package uikit.compose

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import uikit.R

val MontserratFontFamily = FontFamily(
    Font(R.font.montserrat_light, FontWeight.Light),
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_medium, FontWeight.Medium),
    Font(R.font.montserrat_semi_bold, FontWeight.SemiBold),
    Font(R.font.montserrat_bold, FontWeight.Bold)
)

val RobotoMonoFontFamily = FontFamily(
    Font(R.font.roboto_mono, FontWeight.Normal)
)

@Immutable
data class AppTypography(
    val num1: TextStyle,
    val h1: TextStyle,
    val num2: TextStyle,
    val h2: TextStyle,
    val h3: TextStyle,
    val label1: TextStyle,
    val label2: TextStyle,
    val label3: TextStyle,
    val body1: TextStyle,
    val body2: TextStyle,
    val body3: TextStyle,
    val body4Caps: TextStyle,
    val mono: TextStyle
)

val DefaultAppTypography = AppTypography(
    num1 = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = TextStyle.Default.lineHeight,
        letterSpacing = TextStyle.Default.letterSpacing
    ),
    h1 = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = TextStyle.Default.lineHeight,
        letterSpacing = TextStyle.Default.letterSpacing
    ),
    num2 = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = TextStyle.Default.lineHeight,
        letterSpacing = TextStyle.Default.letterSpacing
    ),
    h2 = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = TextStyle.Default.letterSpacing
    ),
    h3 = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = TextStyle.Default.lineHeight,
        letterSpacing = TextStyle.Default.letterSpacing
    ),
    label1 = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = TextStyle.Default.lineHeight,
        letterSpacing = TextStyle.Default.letterSpacing
    ),
    label2 = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = TextStyle.Default.lineHeight,
        letterSpacing = TextStyle.Default.letterSpacing
    ),
    label3 = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = TextStyle.Default.lineHeight,
        letterSpacing = TextStyle.Default.letterSpacing
    ),
    body1 = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = TextStyle.Default.letterSpacing
    ),
    body2 = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = TextStyle.Default.lineHeight,
        letterSpacing = TextStyle.Default.letterSpacing
    ),
    body3 = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = TextStyle.Default.lineHeight,
        letterSpacing = TextStyle.Default.letterSpacing
    ),
    body4Caps = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = TextStyle.Default.lineHeight,
        letterSpacing = TextStyle.Default.letterSpacing
    ),
    mono = TextStyle(
        fontFamily = RobotoMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp
    )
)

val LocalAppTypography = staticCompositionLocalOf<AppTypography> {
    error("No AppTypography provided")
}