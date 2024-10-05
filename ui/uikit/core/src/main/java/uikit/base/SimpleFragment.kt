package uikit.base

import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.tonapps.uikit.color.backgroundPageColor

abstract class SimpleFragment<P: BaseFragment>(
    @LayoutRes layoutId: Int
): Fragment(layoutId) {

    val rootScreen: P?
        get() = parentFragment as? P

    val rootFragmentManager: FragmentManager?
        get() = rootScreen?.childFragmentManager

    var visibleState: Boolean = false
        set(value) {
            if (value != field) {
                field = value
                onVisibleState(value)
            }
        }

    fun setArgs(args: BaseArgs) {
        arguments = args.toBundle()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setOnClickListener {  }
        view.setBackgroundColor(requireContext().backgroundPageColor)
        view.postOnAnimation { visibleState = true }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        visibleState = false
    }

    open fun onVisibleState(visible: Boolean) {

    }

    abstract fun finish()

    abstract fun getTitle(): String
}