package uikit.widget

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import com.tonapps.uikit.color.accentBlueColor
import uikit.R
import uikit.extensions.dp

class BalloonTooltip private constructor(
    private val popup: PopupWindow,
    private val anchorView: View,
) {

    companion object {

        private const val ARROW_CENTER_FROM_LEFT_DP =
            32 // arrow marginStart(24) + half arrow width(8)
        private const val AUTO_DISMISS_MS = 5000L
        const val NO_AUTO_DISMISS = 0L

        fun show(
            anchorView: View,
            badgeText: String,
            messageText: String,
            autoDismissMs: Long = AUTO_DISMISS_MS,
            onClickListener: (() -> Unit)? = null,
        ): BalloonTooltip {
            val context = anchorView.context
            val tooltipView =
                LayoutInflater.from(context).inflate(R.layout.view_balloon_tooltip, null)

            tooltipView.findViewById<TextView>(R.id.tooltip_badge).text = badgeText
            tooltipView.findViewById<TextView>(R.id.tooltip_message).text = messageText
            tooltipView.findViewById<ImageView>(R.id.tooltip_arrow)
                .setColorFilter(context.accentBlueColor)

            if (onClickListener != null) {
                tooltipView.setOnClickListener { onClickListener() }
            }

            val popupWindow = PopupWindow(
                tooltipView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
                isOutsideTouchable = true
                isFocusable = false
            }

            tooltipView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )

            val tooltipHeight = tooltipView.measuredHeight
            val arrowCenterFromLeft = ARROW_CENTER_FROM_LEFT_DP.dp

            val location = IntArray(2)
            anchorView.getLocationOnScreen(location)
            val anchorCenterX = location[0] + anchorView.width / 2
            val anchorTopY = location[1]

            val xOffset = anchorCenterX - arrowCenterFromLeft
            val yOffset = anchorTopY - tooltipHeight

            popupWindow.showAtLocation(anchorView, Gravity.NO_GRAVITY, xOffset, yOffset)

            val tooltip = BalloonTooltip(popupWindow, anchorView)

            if (autoDismissMs > 0) {
                anchorView.postDelayed(tooltip.dismissRunnable, autoDismissMs)
            }

            return tooltip
        }
    }

    private val dismissRunnable = Runnable { dismiss() }

    fun dismiss() {
        anchorView.removeCallbacks(dismissRunnable)
        if (popup.isShowing) {
            popup.dismiss()
        }
    }
}
