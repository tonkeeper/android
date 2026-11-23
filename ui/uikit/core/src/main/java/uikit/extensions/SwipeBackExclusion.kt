package uikit.extensions

import android.graphics.Rect
import android.view.View
import uikit.R

private class SwipeBackExclusions {
    val rects = mutableMapOf<Any, Rect>()
}

fun View.setSwipeBackExclusionRect(owner: Any, rect: Rect) {
    val exclusions = getTag(R.id.swipe_back_exclusions) as? SwipeBackExclusions
        ?: SwipeBackExclusions().also { setTag(R.id.swipe_back_exclusions, it) }
    exclusions.rects[owner] = rect
}

fun View.removeSwipeBackExclusionRect(owner: Any) {
    (getTag(R.id.swipe_back_exclusions) as? SwipeBackExclusions)?.rects?.remove(owner)
}

fun View.hasSwipeBackExclusionAt(x: Int, y: Int): Boolean {
    val exclusions = getTag(R.id.swipe_back_exclusions) as? SwipeBackExclusions ?: return false
    return exclusions.rects.values.any { it.contains(x, y) }
}
