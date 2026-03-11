package ui.lifecycle

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

@Composable
fun OnCreate(onEvent: () -> Unit) {
    LifecycleEventEffect(Lifecycle.Event.ON_CREATE) {
        onEvent()
    }
}

@Composable
fun OnResume(onEvent: () -> Unit) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onEvent()
    }
}

@Composable
fun OnPause(onEvent: () -> Unit) {
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        onEvent()
    }
}
