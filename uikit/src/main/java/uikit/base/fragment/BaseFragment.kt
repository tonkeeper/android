package uikit.base.fragment

import android.content.Context
import android.os.Bundle
import android.text.SpannableString
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.core.app.SharedElementCallback
import androidx.fragment.app.Fragment
import androidx.transition.TransitionInflater
import uikit.R
import uikit.extensions.dp
import uikit.extensions.getDimension
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.getSpannable
import uikit.extensions.pivot
import uikit.extensions.roundTop
import uikit.extensions.scale
import uikit.extensions.statusBarHeight
import uikit.widget.BottomSheetLayout
import uikit.widget.SwipeBackLayout

open class BaseFragment(
    @LayoutRes layoutId: Int
): Fragment(layoutId) {

    interface SwipeBack {
        var doOnDragging: ((Boolean) -> Unit)?
        var doOnDraggingProgress: ((Float) -> Unit)?
    }

    interface BottomSheet

    val window: Window?
        get() = activity?.window

    val onBackPressedDispatcher: OnBackPressedDispatcher?
        get() = activity?.onBackPressedDispatcher

    val parent: Fragment?
        get() {
            if (parentFragment != null) {
                return parentFragment
            }
            return activity?.supportFragmentManager?.fragments?.lastOrNull {
                it != this && it.isVisible
            }
        }

    open val secure: Boolean = false

    private val topRadius: Int by lazy {
        requireContext().getDimensionPixelSize(R.dimen.cornerSmall)
    }

    private val statusBarHeight: Int by lazy {
        requireContext().statusBarHeight
    }

    fun getSpannable(@StringRes id: Int): SpannableString {
        return requireContext().getSpannable(id)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)!!
        view.setBackgroundResource(R.color.backgroundPage)
        if (this is SwipeBack) {
            return onCreateSwipeBack(inflater.context, view)
        } else if (this is BottomSheet) {
            return onCreateBottomSheet(inflater.context, view)
        }
        return view
    }

    private fun onCreateSwipeBack(context: Context, view: View): SwipeBackLayout {
        this as SwipeBack

        val swipeBackLayout = SwipeBackLayout(context)
        swipeBackLayout.doOnCloseScreen = {
            finishInternal()
        }
        swipeBackLayout.doOnDragging = doOnDragging
        swipeBackLayout.doOnDraggingProgress = doOnDraggingProgress
        swipeBackLayout.setContentView(view)
        swipeBackLayout.startShowAnimation()
        return swipeBackLayout
    }

    private fun onCreateBottomSheet(context: Context, view: View): BottomSheetLayout {
        val bottomSheetLayout = BottomSheetLayout(context)
        bottomSheetLayout.doOnCloseScreen = {
            finishInternal()
        }
        bottomSheetLayout.fragment = this
        bottomSheetLayout.setContentView(view)
        bottomSheetLayout.startShowAnimation()
        return bottomSheetLayout
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        onBackPressedDispatcher?.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    fun finish() {
        val view = view ?: return
        when (view) {
            is SwipeBackLayout -> {
                view.startHideAnimation {
                    finishInternal()
                }
            }
            is BottomSheetLayout -> {
                view.startHideAnimation {
                    finishInternal()
                }
            }
            else -> finishInternal()
        }
    }

    private fun finishInternal() {
        activity?.supportFragmentManager?.popBackStack()
    }

    override fun onResume() {
        super.onResume()
        if (secure) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onPause() {
        super.onPause()
        if (secure) {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }


    fun removeCallbacks(callback: Runnable) {
        view?.removeCallbacks(callback)
    }

    fun postDelayed(delay: Long, action: Runnable) {
        view?.postDelayed(action, delay)
    }

    fun post(action: Runnable) {
        view?.post(action)
    }

    @ColorInt
    fun getColor(@ColorRes colorRes: Int): Int {
        return requireContext().getColor(colorRes)
    }

}