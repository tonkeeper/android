package ui.components.moon.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

// If you want to use open screen like a popUp we need to reset state all time we show the screen
@Composable
fun MoonDialogDisposableScope(
    content: @Composable () -> Unit
) {
    val owner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            owner.viewModelStore.clear()
        }
    }

    CompositionLocalProvider(
        LocalViewModelStoreOwner provides owner
    ) {
        content()
    }
}
