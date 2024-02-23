package uikit.navigation

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.addCallback
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import uikit.R
import uikit.base.BaseActivity
import uikit.base.BaseFragment
import uikit.extensions.doOnEnd
import uikit.extensions.hapticConfirm
import uikit.extensions.primaryFragment
import uikit.extensions.runAnimation

abstract class NavigationActivity: BaseActivity(), Navigation, ViewTreeObserver.OnPreDrawListener {

    companion object {
        val hostFragmentId = R.id.root_container
        val hostSheetId = R.id.sheet_container
    }

    private val isInitialized: Boolean
        get() = supportFragmentManager.fragments.isNotEmpty()

    private val modals: List<BaseFragment.Modal>
        get() = supportFragmentManager.fragments.filterIsInstance<BaseFragment.Modal>()

    private lateinit var baseView: View
    private lateinit var contentView: View
    private lateinit var toastView: AppCompatTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigation)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        baseView = findViewById(R.id.base)

        contentView = findViewById(android.R.id.content)
        contentView.viewTreeObserver.addOnPreDrawListener(this)

        toastView = findViewById(R.id.toast)

        onBackPressedDispatcher.addCallback(this) {
            onBackPress()
        }
    }

    private fun onBackPress() {
        val fragment = supportFragmentManager.fragments.lastOrNull() as? BaseFragment ?: return
        if (fragment.onBackPressed()) {
            remove(fragment)
        }
    }

    override fun onPreDraw(): Boolean {
        if (isInitialized) {
            contentView.viewTreeObserver.removeOnPreDrawListener(this)
            contentView.setBackgroundColor(Color.BLACK)
            return true
        }
        return false
    }

    override fun setFragmentResult(requestKey: String, result: Bundle) {
        supportFragmentManager.setFragmentResult(requestKey, result)
    }

    override fun setFragmentResultListener(
        requestKey: String,
        listener: (bundle: Bundle) -> Unit
    ) {
        supportFragmentManager.setFragmentResultListener(requestKey, this) { _, b ->
            listener(b)
        }
    }

    fun setPrimaryFragment(fragment: BaseFragment, recreate: Boolean = false): Boolean {
        if (primaryFragment?.javaClass == fragment.javaClass && !recreate) {
            return false
        }

        if (recreate) {
            clearBackStack()
        }

        val transaction = supportFragmentManager.beginTransaction()
        if (recreate) {
            supportFragmentManager.fragments.forEach { transaction.remove(it) }
        }
        transaction.replace(hostFragmentId, fragment)
        transaction.setPrimaryNavigationFragment(fragment)
        transaction.commitAllowingStateLoss()
        return true
    }

    override fun add(fragment: BaseFragment) {
        val removeModals = fragment !is BaseFragment.Modal
        val transaction = supportFragmentManager.beginTransaction()
        if (fragment is BaseFragment.BottomSheet || fragment is BaseFragment.Modal) {
            transaction.add(hostSheetId, fragment)
        } else {
            transaction.add(hostFragmentId, fragment)
        }
        if (removeModals) {
            dismissModals(transaction)
        }
        transaction.commitAllowingStateLoss()
    }

    override fun remove(fragment: Fragment) {
        if (supportFragmentManager.primaryNavigationFragment == fragment) {
            finish()
        } else {
            supportFragmentManager.commit {
                remove(fragment)
            }
        }
    }

    private fun clearBackStack() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
    }

    private fun dismissModals(transaction: FragmentTransaction) {
        val currentModals = modals
        if (currentModals.isEmpty()) {
            return
        }
        for (modal in currentModals) {
            transaction.remove(modal as Fragment)
        }
    }

    override fun toast(message: String) {
        contentView.hapticConfirm()
        toastView.text = message

        if (toastView.visibility == View.VISIBLE) {
            return
        }

        toastView.visibility = View.VISIBLE
        toastView.runAnimation(R.anim.toast).doOnEnd {
            toastView.visibility = View.GONE
        }
    }
}