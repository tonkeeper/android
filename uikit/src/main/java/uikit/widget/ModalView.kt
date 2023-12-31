package uikit.widget

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import uikit.R
import uikit.extensions.getColor
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.roundTop
import kotlin.math.abs

class ModalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    var doOnHide: (() -> Unit)? = null

    private val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                doOnHide?.invoke()
            }
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {
            val opacity = 1f - abs(slideOffset)
            bgView.alpha = opacity
        }
    }

    private val behavior: BottomSheetBehavior<FrameLayout>
    private val bgView: View
    private val containerView: FrameLayout
    private val coordinatorView: CoordinatorLayout
    private val bottomSheetView: FrameLayout

    init {
        inflate(context, R.layout.view_modal, this)
        bgView = findViewById(R.id.bg)

        containerView = findViewById(R.id.modal_container)
        bottomSheetView = findViewById(R.id.modal_design_bottom_sheet)
        bottomSheetView.roundTop(context.getDimensionPixelSize(R.dimen.cornerMedium))

        coordinatorView = findViewById(R.id.modal_coordinator)

        behavior = BottomSheetBehavior.from(bottomSheetView)
        behavior.addBottomSheetCallback(bottomSheetCallback)
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    fun setContentView(view: View) {
        bottomSheetView.removeAllViews()
        bottomSheetView.addView(view)
    }

    fun show() {
        if (behavior.state == BottomSheetBehavior.STATE_HIDDEN) {
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    fun hide() {
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

}