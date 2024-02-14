package uikit.extensions

import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat

val insetsBottomTypeMask: Int
    get() = WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.systemBars()

val WindowInsetsCompat.bottomBars: Insets
    get() = getInsets(insetsBottomTypeMask)

val WindowInsetsCompat.bottomBarsOffset: Int
    get() = bottomBars.bottom
