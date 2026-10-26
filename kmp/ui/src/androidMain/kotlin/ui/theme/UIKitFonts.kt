package ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import uikit.R

@Composable
internal actual fun rememberTTFirsNeueFontFamily(): FontFamily = remember {
    FontFamily(
        Font(R.font.tt_firs_neue_normal, weight = FontWeight.Normal),
        Font(R.font.tt_firs_neue_medium, weight = FontWeight.Medium),
        Font(R.font.tt_firs_neue_demi_bold, weight = FontWeight.SemiBold),
        Font(R.font.tt_firs_neue_demi_bold, weight = FontWeight.Bold),
    )
}
