package uikit.widget

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.findFragment
import com.tonapps.uikit.color.accentBlueColor
import uikit.R
import uikit.extensions.dp
import java.lang.ref.WeakReference
import java.util.WeakHashMap

private fun assertBalloonTooltipMainThread() {
    check(Looper.myLooper() == Looper.getMainLooper()) {
        "BalloonTooltip must be called from the main thread"
    }
}

class BalloonTooltip private constructor(
    anchorView: View,
    private val badgeText: String,
    private val messageText: String,
    private val placement: Placement,
    private val offset: Int,
    private val autoDismissMs: Long,
    private val onShown: (() -> Unit)?,
    private val onClickListener: (() -> Unit)?,
) {

    enum class Placement {
        TOP,
        BOTTOM,
    }

    private data class TooltipGeometry(
        val xOffset: Int,
        val yOffset: Int,
        val arrowMarginStart: Int,
    )

    companion object {

        private const val DEFAULT_ARROW_MARGIN_START_DP = 24
        private const val ARROW_HALF_WIDTH_DP = 8
        private const val SCREEN_EDGE_PADDING_DP = 16
        private const val AUTO_DISMISS_MS = 5000L
        private const val ADVANCE_QUEUE_DELAY_MS = 200L
        private const val FADE_DURATION_MS = 200L
        const val NO_AUTO_DISMISS = 0L

        private val mainHandler = Handler(Looper.getMainLooper())
        private val advanceRunnable = Runnable { showNext() }
        private val fadeInInterpolator: Interpolator = DecelerateInterpolator()
        private val fadeOutInterpolator: Interpolator = AccelerateInterpolator()

        private var current: BalloonTooltip? = null
        private val queue = ArrayDeque<BalloonTooltip>()
        private val pendingAnchors = WeakHashMap<View, AnchorWatcher>()

        fun show(
            anchorView: View,
            badgeText: String,
            messageText: String,
            placement: Placement = Placement.TOP,
            offset: Int = 0,
            autoDismissMs: Long = AUTO_DISMISS_MS,
            onShown: (() -> Unit)? = null,
            onClickListener: (() -> Unit)? = null,
        ): BalloonTooltip {
            assertBalloonTooltipMainThread()
            existingForAnchor(anchorView)?.let { return it }

            val tooltip = BalloonTooltip(
                anchorView = anchorView,
                badgeText = badgeText,
                messageText = messageText,
                placement = placement,
                offset = offset,
                autoDismissMs = autoDismissMs,
                onShown = onShown,
                onClickListener = onClickListener,
            )
            if (current == null) {
                tooltip.display()
            } else {
                queue.addLast(tooltip)
            }
            return tooltip
        }

        private fun existingForAnchor(anchorView: View): BalloonTooltip? {
            current?.takeIf { !it.dismissed && it.anchor() === anchorView }?.let { return it }
            return queue.firstOrNull { !it.dismissed && it.anchor() === anchorView }
        }

        private fun showNext() {
            mainHandler.removeCallbacks(advanceRunnable)
            while (queue.isNotEmpty()) {
                val next = queue.first()
                val nextAnchor = next.anchor()
                when {
                    next.dismissed -> queue.removeFirst()
                    nextAnchor == null || !nextAnchor.isAttachedToWindow -> {
                        queue.removeFirst()
                        next.dismissed = true
                    }
                    !nextAnchor.isAnchorVisible() -> {
                        watchAnchor(nextAnchor)
                        return
                    }
                    else -> {
                        queue.removeFirst()
                        next.performShow()
                        return
                    }
                }
            }
        }

        private fun watchAnchor(anchor: View) {
            if (pendingAnchors.containsKey(anchor)) return
            val watcher = AnchorWatcher(anchor)
            pendingAnchors[anchor] = watcher
            watcher.attach()
        }

        private fun unwatchAnchor(anchor: View) {
            pendingAnchors.remove(anchor)?.detach()
        }

        /**
         * Mutates companion [pendingAnchors] and [queue] from view callbacks and via
         * [mainHandler]-scheduled [showNext]. That matches [showNext]'s queue iteration (main looper only);
         * public API enforces the same thread via [assertBalloonTooltipMainThread].
         */
        private class AnchorWatcher(anchor: View) {
            private val anchorRef = WeakReference(anchor)
            private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
                val a = anchorRef.get() ?: return@OnGlobalLayoutListener
                if (a.isAnchorVisible()) {
                    detach()
                    pendingAnchors.remove(a)
                    mainHandler.post { showNext() }
                }
            }
            private val attachListener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) {
                    detach()
                    pendingAnchors.remove(v)
                    queue.removeAll { it.anchor() === v }
                    mainHandler.post { showNext() }
                }
            }

            fun attach() {
                val a = anchorRef.get() ?: return
                a.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
                a.addOnAttachStateChangeListener(attachListener)
            }

            fun detach() {
                val a = anchorRef.get() ?: return
                if (a.viewTreeObserver.isAlive) {
                    a.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
                }
                a.removeOnAttachStateChangeListener(attachListener)
            }
        }

        private fun View.isAnchorVisible(): Boolean {
            if (!isShown) return false
            val activity = context.findActivity() as? FragmentActivity ?: return true
            val topFragment = activity.supportFragmentManager.fragments
                .lastOrNull { !it.isHidden && !it.isDetached } ?: return true
            var fragment: Fragment? = runCatching { findFragment<Fragment>() }.getOrNull()
            while (fragment != null) {
                if (fragment === topFragment) return true
                fragment = fragment.parentFragment
            }
            return false
        }

        private tailrec fun Context.findActivity(): Activity? = when (this) {
            is Activity -> this
            is ContextWrapper -> baseContext.findActivity()
            else -> null
        }

        private fun computeTooltipGeometry(
            anchor: View,
            tooltipWidth: Int,
            tooltipHeight: Int,
            placement: Placement,
            offsetPx: Int,
        ): TooltipGeometry {
            val context = anchor.context
            val screenWidth = context.resources.displayMetrics.widthPixels
            val edgePadding = SCREEN_EDGE_PADDING_DP.dp
            val arrowHalfWidth = ARROW_HALF_WIDTH_DP.dp
            val defaultArrowMarginStart = DEFAULT_ARROW_MARGIN_START_DP.dp

            val location = IntArray(2)
            anchor.getLocationOnScreen(location)
            val anchorCenterX = location[0] + anchor.width / 2
            val anchorTopY = location[1]
            val anchorBottomY = anchorTopY + anchor.height

            val rawXOffset = anchorCenterX - defaultArrowMarginStart - arrowHalfWidth
            val maxXOffset = (screenWidth - edgePadding - tooltipWidth).coerceAtLeast(edgePadding)
            val xOffset = rawXOffset.coerceIn(edgePadding, maxXOffset)

            val arrowMarginStart = (anchorCenterX - xOffset - arrowHalfWidth)
                .coerceIn(edgePadding, (tooltipWidth - edgePadding - arrowHalfWidth * 2).coerceAtLeast(edgePadding))

            val yOffset = when (placement) {
                Placement.TOP -> anchorTopY - tooltipHeight - offsetPx
                Placement.BOTTOM -> anchorBottomY + offsetPx
            }
            return TooltipGeometry(xOffset, yOffset, arrowMarginStart)
        }
    }

    private val anchorRef = WeakReference(anchorView)

    private fun anchor(): View? = anchorRef.get()

    private val dismissRunnable = Runnable { dismiss() }
    private var popup: PopupWindow? = null
    private var dismissed = false
    private var dismissCleanupDone = false
    private var anchorVisibilityListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var visibilityAnchorRef: WeakReference<View>? = null

    private fun display() {
        if (dismissed) return
        val anchor = anchor() ?: run {
            dismissOrphaned()
            return
        }
        if (!anchor.isAttachedToWindow) {
            dismissed = true
            advanceQueue()
            return
        }
        if (!anchor.isAnchorVisible()) {
            enqueueDeferredIfNeeded()
            current = null
            watchAnchor(anchor)
            return
        }
        performShow()
    }

    private fun dismissOrphaned() {
        if (dismissed) return
        dismissed = true
        mainHandler.removeCallbacks(dismissRunnable)
        removeAnchorVisibilityListener()
        if (popup?.isShowing == true) {
            popup?.dismiss()
        } else {
            performAfterPopupDismissed()
        }
    }

    private fun enqueueDeferredIfNeeded() {
        if (this !in queue) queue.addFirst(this)
    }

    private fun performShow() {
        if (dismissed) return
        val anchor = anchor() ?: run {
            dismissOrphaned()
            return
        }
        if (!anchor.isAttachedToWindow || !anchor.isAnchorVisible()) {
            display()
            return
        }
        current = this
        unwatchAnchor(anchor)

        val context = anchor.context
        val layoutId = when (placement) {
            Placement.TOP -> R.layout.view_balloon_tooltip
            Placement.BOTTOM -> R.layout.view_balloon_tooltip_below
        }
        val tooltipView = LayoutInflater.from(context).inflate(layoutId, null)
        bindTooltipViews(tooltipView, context)

        val popupWindow = PopupWindow(
            tooltipView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            isOutsideTouchable = true
            isFocusable = false
            setOnDismissListener {
                removeAnchorVisibilityListener()
                mainHandler.removeCallbacks(dismissRunnable)
                dismissed = true
                performAfterPopupDismissed()
            }
        }

        tooltipView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )

        val geometry = computeTooltipGeometry(
            anchor = anchor,
            tooltipWidth = tooltipView.measuredWidth,
            tooltipHeight = tooltipView.measuredHeight,
            placement = placement,
            offsetPx = offset,
        )
        tooltipView.findViewById<ImageView>(R.id.tooltip_arrow).updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginStart = geometry.arrowMarginStart
        }

        popupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, geometry.xOffset, geometry.yOffset)
        popup = popupWindow

        runFadeIn(tooltipView)
        installAnchorVisibilityObserver(anchor)
    }

    private fun bindTooltipViews(tooltipView: View, context: Context) {
        tooltipView.findViewById<TextView>(R.id.tooltip_badge).text = badgeText
        tooltipView.findViewById<TextView>(R.id.tooltip_message).text = messageText
        tooltipView.findViewById<ImageView>(R.id.tooltip_arrow).setColorFilter(context.accentBlueColor)
        onClickListener?.let { listener ->
            tooltipView.setOnClickListener { listener() }
        }
    }

    private fun runFadeIn(content: View) {
        content.alpha = 0f
        content.animate().cancel()
        content.animate()
            .alpha(1f)
            .setDuration(FADE_DURATION_MS)
            .setInterpolator(fadeInInterpolator)
            .withEndAction {
                if (!dismissed) {
                    onShown?.invoke()
                    if (autoDismissMs > 0) {
                        mainHandler.postDelayed(dismissRunnable, autoDismissMs)
                    }
                }
            }
            .start()
    }

    private fun installAnchorVisibilityObserver(anchor: View) {
        visibilityAnchorRef = WeakReference(anchor)
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            if (dismissed) {
                removeAnchorVisibilityListener()
                return@OnGlobalLayoutListener
            }
            val a = anchor() ?: run {
                dismiss()
                return@OnGlobalLayoutListener
            }
            if (!a.isAnchorVisible()) {
                dismiss()
            }
        }
        anchorVisibilityListener = listener
        anchor.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    private fun removeAnchorVisibilityListener() {
        val listener = anchorVisibilityListener ?: return
        anchorVisibilityListener = null
        val observed = visibilityAnchorRef?.get()
        visibilityAnchorRef = null
        if (observed?.viewTreeObserver?.isAlive == true) {
            observed.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }

    fun dismiss() {
        assertBalloonTooltipMainThread()
        if (dismissed) return
        dismissed = true
        removeAnchorVisibilityListener()
        mainHandler.removeCallbacks(dismissRunnable)

        val content = popup?.takeIf { it.isShowing }?.contentView
        if (content != null) {
            runFadeOut(content) { finishDismissAfterAnimation() }
        } else {
            finishDismissAfterAnimation()
        }
    }

    private fun runFadeOut(content: View, onEnd: () -> Unit) {
        content.animate().cancel()
        content.animate()
            .alpha(0f)
            .setDuration(FADE_DURATION_MS)
            .setInterpolator(fadeOutInterpolator)
            .withEndAction(onEnd)
            .start()
    }

    private fun finishDismissAfterAnimation() {
        if (dismissCleanupDone) return
        if (popup?.isShowing == true) {
            popup?.dismiss()
        } else {
            performAfterPopupDismissed()
        }
    }

    private fun performAfterPopupDismissed() {
        if (dismissCleanupDone) return
        dismissCleanupDone = true
        popup = null

        if (current === this) {
            advanceQueue()
        } else {
            queue.remove(this)
            val anchor = anchor()
            if (anchor != null && queue.none { it.anchor() === anchor }) {
                unwatchAnchor(anchor)
            }
        }
    }

    private fun advanceQueue() {
        current = null
        mainHandler.removeCallbacks(advanceRunnable)
        if (queue.isEmpty()) return
        mainHandler.postDelayed(advanceRunnable, ADVANCE_QUEUE_DELAY_MS)
    }
}
