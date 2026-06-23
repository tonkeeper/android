package ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun painterResource(id: Int): Painter {
    return androidx.compose.ui.res.painterResource(id = id)
}

@Composable
actual fun fixAndroidResUrl(url: String): String {
    val context = LocalContext.current

    if (url.startsWith("res:")) {
        val resId = url.replace("res:/", "")
        val packageName = context.packageName
        return "android.resource://$packageName/$resId"
    }
    return url
}

