package uikit.extensions

import android.view.animation.Animation

inline fun Animation.doOnEnd(
    crossinline action: (animation: Animation) -> Unit
): Animation.AnimationListener =
    setListener(onEnd = action)

inline fun Animation.doOnStart(
    crossinline action: (animation: Animation) -> Unit
): Animation.AnimationListener =
    setListener(onStart = action)

inline fun Animation.doOnRepeat(
    crossinline action: (animation: Animation) -> Unit
): Animation.AnimationListener =
    setListener(onRepeat = action)

inline fun Animation.setListener(
    crossinline onEnd: (animation: Animation) -> Unit = {},
    crossinline onStart: (animation: Animation) -> Unit = {},
    crossinline onRepeat: (animation: Animation) -> Unit = {}
): Animation.AnimationListener {
    val listener = object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation) {
            onStart(animation)
        }

        override fun onAnimationEnd(animation: Animation) {
            onEnd(animation)
        }

        override fun onAnimationRepeat(animation: Animation) {
            onRepeat(animation)
        }
    }

    setAnimationListener(listener)
    return listener
}
