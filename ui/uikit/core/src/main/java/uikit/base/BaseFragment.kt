package uikit.base

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Parcelable
import android.text.SpannableString
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.BackEventCompat
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.tonapps.uikit.color.backgroundPageColor
import uikit.extensions.getSpannable
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.BottomSheetLayout
import uikit.widget.ModalView
import uikit.widget.SwipeBackLayout
import java.io.Serializable
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

open class BaseFragment(
    @LayoutRes layoutId: Int
): Fragment(layoutId) {

    interface ResultContract<I, O> {
        fun parseResult(bundle: Bundle): O
        fun createResult(result: I): Bundle
    }

    interface SingleTask

    interface PredictiveBackGesture {
        fun onPredictiveBackCancelled() {

        }

        fun onPredictiveBackProgressed(backEvent: BackEventCompat) {

        }

        fun onPredictiveOnBackStarted(backEvent: BackEventCompat) {

        }
    }

    interface SwipeBack: PredictiveBackGesture {

        fun onEndShowingAnimation() {

        }
    }

    interface BottomSheet {

        fun onEndShowingAnimation() {

        }

        fun onDragging() {

        }
    }

    interface Modal {

        private val fragment: BaseFragment
            get() = this as BaseFragment

        private val view: ModalView
            get() = fragment.view as ModalView

        val behavior: BottomSheetBehavior<FrameLayout>
            get() = view.behavior

        val bottomSheetView: FrameLayout
            get() = view.bottomSheetView

        val coordinatorView: CoordinatorLayout
            get() = view.coordinatorView

        val scaleBackground: Boolean
            get() = false

        fun onEndShowingAnimation() {

        }
    }

    open val fragmentName: String
        get() = javaClass.simpleName

    val window: Window?
        get() = activity?.window

    val parent: Fragment?
        get() {
            if (parentFragment != null) {
                return parentFragment
            }
            return activity?.supportFragmentManager?.fragments?.lastOrNull {
                it != this && it.isVisible
            }
        }

    val mainExecutor: Executor by lazy {
        ContextCompat.getMainExecutor(requireContext())
    }

    private val resultIsSet = AtomicBoolean(false)

    open val disableShowAnimation: Boolean = false

    open val secure: Boolean = false

    open val title: CharSequence? = null

    private val isFinished = AtomicBoolean(false)

    private val resultKey: String? by lazy {
        arguments?.getString(ARG_RESULT_KEY)?.ifBlank { null }
    }

    fun setArgs(bundle: Bundle) {
        val args = arguments ?: Bundle()
        args.putAll(bundle)
        arguments = args
    }

    fun setArgs(args: BaseArgs) {
        setArgs(args.toBundle())
    }

    fun putParcelableArg(key: String, value: Parcelable) {
        setArgs(Bundle().apply {
            putParcelable(key, value)
        })
    }

    fun putParcelableArrayListArg(key: String, value: ArrayList<out Parcelable>) {
        setArgs(Bundle().apply {
            putParcelableArrayList(key, value)
        })
    }

    fun putParcelableListArg(key: String, value: List<out Parcelable>) {
        putParcelableArrayListArg(key, ArrayList(value))
    }

    fun putStringList(key: String, value: List<String>) {
        setArgs(Bundle().apply {
            putStringArrayList(key, ArrayList(value))
        })
    }

    fun putSerializable(key: String, value: Serializable) {
        setArgs(Bundle().apply {
            putSerializable(key, value)
        })
    }

    fun putStringArg(key: String, value: String? = null) {
        if (value != null) {
            setArgs(Bundle().apply {
                putString(key, value)
            })
        }
    }

    fun putBooleanArg(key: String, value: Boolean) {
        setArgs(Bundle().apply {
            putBoolean(key, value)
        })
    }

    fun putLongArg(key: String, value: Long) {
        setArgs(Bundle().apply {
            putLong(key, value)
        })
    }

    fun setResultKey(key: String) {
        putStringArg(ARG_RESULT_KEY, key)
    }

    fun setResult(bundle: Bundle, finish: Boolean = true) {
        val key = resultKey ?: throw IllegalStateException("For setting result you must set result key")
        navigation?.setFragmentResult(key, bundle)
        resultIsSet.set(true)
        if (finish) {
            finish()
        }
    }

    fun getSpannable(@StringRes id: Int): SpannableString {
        return requireContext().getSpannable(id)
    }

    fun registerForPermission(callback: ActivityResultCallback<Boolean>): ActivityResultLauncher<String> {
        return registerForActivityResult(ActivityResultContracts.RequestPermission(), callback)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val contentView = super.onCreateView(inflater, container, savedInstanceState)!!

        val view = if (this is Modal) {
            wrapInModal(inflater.context, contentView, savedInstanceState)
        } else {
            contentView.setBackgroundColor(requireContext().backgroundPageColor)
            when (this) {
                is SwipeBack -> wrapInSwipeBack(inflater.context, contentView, savedInstanceState)
                is BottomSheet -> wrapInBottomSheet(inflater.context, contentView, savedInstanceState)
                else -> contentView
            }
        }
        view.setOnClickListener {  }
        return view
    }

    private fun wrapInModal(context: Context, view: View, savedInstanceState: Bundle?): ModalView {
        this as Modal

        val modalView = ModalView(context)
        modalView.scaleBackground = scaleBackground
        modalView.setContentView(view)
        modalView.doOnHide = { finishInternal() }
        modalView.fragment = this
        if (savedInstanceState == null && !disableShowAnimation) {
            onEndShowingAnimation()
        } else {
            modalView.doOnLayout { onEndShowingAnimation() }
        }
        return modalView
    }

    private fun wrapInSwipeBack(context: Context, view: View, savedInstanceState: Bundle?): SwipeBackLayout {
        this as SwipeBack

        val swipeBackLayout = SwipeBackLayout(context)
        swipeBackLayout.doOnCloseScreen = ::finishInternal
        swipeBackLayout.doOnEndShowingAnimation = ::onEndShowingAnimation
        swipeBackLayout.setContentView(view)
        if (savedInstanceState == null && !disableShowAnimation) {
            swipeBackLayout.startShowAnimation()
        } else {
            swipeBackLayout.doOnLayout { onEndShowingAnimation() }
        }
        return swipeBackLayout
    }

    private fun wrapInBottomSheet(context: Context, view: View, savedInstanceState: Bundle?): BottomSheetLayout {
        this as BottomSheet

        val bottomSheetLayout = BottomSheetLayout(context)
        bottomSheetLayout.setContentView(view)
        bottomSheetLayout.doOnHide = ::finishInternal
        bottomSheetLayout.doOnAnimationEnd = ::onEndShowingAnimation
        bottomSheetLayout.doOnDragging = ::onDragging
        bottomSheetLayout.fragment = this
        if (savedInstanceState == null && !disableShowAnimation) {
            bottomSheetLayout.startShowAnimation()
        } else {
            bottomSheetLayout.doOnLayout { onEndShowingAnimation() }
        }
        return bottomSheetLayout
    }

    open fun onBackPressed(): Boolean {
        finish()
        return false
    }

    open fun finish() {
        if (isFinished.get()) {
            return
        }

        val view = view ?: return

        if (isFinished.compareAndSet(false, true)) {
            when (view) {
                is SwipeBackLayout -> view.startHideAnimation()
                is BottomSheetLayout -> view.hide(true)
                is ModalView -> view.hide(true)
                else -> finishInternal()
            }
        }
    }

    override fun onDestroy() {
        if (resultKey != null && !resultIsSet.get()) {
            navigation?.setFragmentResult(resultKey!!, Bundle())
        }
        super.onDestroy()
    }

    open fun finishInternal() {
        navigation?.remove(this)
    }

    override fun onResume() {
        super.onResume()
        if (secure) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        (view as? BottomSheetLayout)?.show()
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

    fun runOnUiThread(action: () -> Unit) {
        if (Thread.currentThread() == requireActivity().mainLooper.thread) {
            action()
        } else {
            post(Runnable(action))
        }
    }

    fun post(action: Runnable) {
        view?.post(action)
    }

    fun postOnAnimation(action: Runnable) {
        view?.postOnAnimation(action)
    }

    @ColorInt
    fun getColor(@ColorRes colorRes: Int): Int {
        return requireContext().getColor(colorRes)
    }

    fun getDrawable(
        drawableRes: Int,
        @ColorInt tintColor: Int = Color.TRANSPARENT
    ): Drawable {
        val drawable = ContextCompat.getDrawable(requireContext(), drawableRes)!!
        if (tintColor != Color.TRANSPARENT) {
            drawable.setTint(tintColor)
        }
        return drawable
    }

    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
    }

    fun getCurrentFocus(): EditText? {
        return requireActivity().currentFocus as? EditText
    }

    private companion object {
        private const val ARG_RESULT_KEY = "_uikit_result_key_"
    }
}