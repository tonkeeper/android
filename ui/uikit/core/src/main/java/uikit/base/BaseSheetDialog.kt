package uikit.base

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import uikit.R

@Deprecated("Use BaseFragment with BottomSheet interface instead")
open class BaseSheetDialog(
    context: Context
): BottomSheetDialog(context) {

    val view: View

    private val headerView: View
    private val titleView: AppCompatTextView
    private val closeView: AppCompatImageView
    private val contentView: FrameLayout

    init {
        super.setContentView(R.layout.dialog_base)
        view = super.findViewById(R.id.dialog_root)!!

        headerView = super.findViewById(R.id.dialog_header)!!

        titleView = super.findViewById(R.id.header_title)!!

        closeView = super.findViewById(R.id.close)!!
        closeView.setOnClickListener {
            dismiss()
        }

        contentView = super.findViewById(R.id.dialog_content)!!
    }

    override fun setTitle(@StringRes resId: Int) {
        titleView.setText(resId)
    }

    fun hideHeader() {
        headerView.visibility = View.GONE
    }

    fun dismissDelay(delay: Long = 1000L) {
        contentView.postDelayed({
            dismiss()
        }, delay)
    }

    fun getString(@StringRes resId: Int): String {
        return context.getString(resId)
    }

    override fun setContentView(layoutResID: Int) {
        contentView.removeAllViews()
        layoutInflater.inflate(layoutResID, contentView, true)
    }

    fun fixPeekHeight() {
        view.requestLayout()
        view.post {
            behavior.peekHeight = view.measuredHeight
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }
}