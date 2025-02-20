package uikit.navigation

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.tonapps.uikit.color.UIKitColor
import com.tonapps.uikit.color.backgroundContentTintColor
import uikit.R
import uikit.base.BaseActivity
import uikit.base.BaseFragment
import uikit.extensions.doOnEnd
import uikit.extensions.findFragment
import uikit.extensions.hapticConfirm
import uikit.extensions.primaryFragment
import uikit.extensions.runAnimation
import uikit.extensions.scale
import uikit.widget.ToastView
import java.util.concurrent.atomic.AtomicInteger

abstract class NavigationActivity: BaseActivity(), Navigation, ViewTreeObserver.OnPreDrawListener {

    open val hostFragmentId: Int = R.id.root_container
    open val hostSheetId: Int = R.id.sheet_container

    open val isInitialized: Boolean
        get() = supportFragmentManager.fragments.isNotEmpty()

    private val modals: List<BaseFragment.Modal>
        get() = supportFragmentManager.fragments.filterIsInstance<BaseFragment.Modal>()

    private lateinit var hostFragmentView: View
    private lateinit var baseView: View
    private lateinit var contentView: View
    private lateinit var toastView: ToastView

    private val nextFragmentRequestCode = AtomicInteger()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        enableEdgeToEdge()
        setContentView(R.layout.activity_navigation)

        hostFragmentView = findViewById(hostFragmentId)
        baseView = findViewById(R.id.base)

        contentView = findViewById(android.R.id.content)
        contentView.viewTreeObserver.addOnPreDrawListener(this)

        toastView = findViewById(R.id.toast)

        val callback = object : OnBackPressedCallback(true) {

            private val target: BaseFragment.PredictiveBackGesture?
                get() = supportFragmentManager.fragments.lastOrNull() as? BaseFragment.PredictiveBackGesture

            override fun handleOnBackCancelled() {
                super.handleOnBackCancelled()
                target?.onPredictiveBackCancelled()
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                super.handleOnBackProgressed(backEvent)
                target?.onPredictiveBackProgressed(backEvent)
            }

            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                super.handleOnBackStarted(backEvent)
                target?.onPredictiveOnBackStarted(backEvent)
            }

            override fun handleOnBackPressed() {
                onBackPress()
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
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

    override fun resetFragmentResult(requestKey: String) {
        supportFragmentManager.apply {
            clearFragmentResult(requestKey)
            clearFragmentResultListener(requestKey)
        }
    }

    fun setPrimaryFragment(
        fragment: BaseFragment,
        recreate: Boolean = false,
        runnable: Runnable? = null
    ): Boolean {
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
        transaction.runOnCommit {
            runnable?.run()
        }
        transaction.commitAllowingStateLoss()
        return true
    }

    open fun isNeedRemoveModals(fragment: BaseFragment): Boolean {
        return fragment !is BaseFragment.Modal
    }

    override fun add(fragment: BaseFragment) {
        if (supportFragmentManager.primaryNavigationFragment == null) {
            baseView.postDelayed({
                add(fragment)
            }, 800)
            return
        }

        if (fragment is BaseFragment.SingleTask) {
            removeOldSingleTaskFragments()
        }
        val removeModals = isNeedRemoveModals(fragment)
        val transaction = supportFragmentManager.beginTransaction()
        if (fragment is BaseFragment.BottomSheet || fragment is BaseFragment.Modal) {
            transaction.add(hostSheetId, fragment)
        } else {
            transaction.add(hostFragmentId, fragment)
        }
        if (removeModals) {
            dismissModals(transaction)
        }
        transaction.setReorderingAllowed(true)
        transaction.commitAllowingStateLoss()
    }

    private fun removeOldSingleTaskFragments() {
        val fragments = supportFragmentManager.fragments.filterIsInstance<BaseFragment.SingleTask>()
        for (fragment in fragments) {
            if (fragment is BaseFragment) {
                fragment.finish()
            }
        }
    }

    override fun addForResult(
        fragment: BaseFragment,
        callback: (Bundle) -> Unit
    ) {
        val requestKey = "fragment_rq#" + nextFragmentRequestCode.getAndIncrement()
        fragment.setResultKey(requestKey)
        setFragmentResultListener(requestKey) { bundle ->
            resetFragmentResult(requestKey)
            callback(bundle)
        }
        add(fragment)
    }

    override fun remove(fragment: Fragment) {
        if (supportFragmentManager.primaryNavigationFragment == fragment) {
            finish()
        } else if (!isStateSaved()) {
            val transaction = supportFragmentManager.beginTransaction()
            transaction.remove(fragment)
            transaction.commitAllowingStateLoss()
        }
    }

    override fun removeByClass(runnable: Runnable, vararg clazz: Class<out Fragment>) {
        if (isStateSaved()) {
            return
        }

        val invalidate = Runnable {
            hostFragmentView.scale = 1f
            hostFragmentView.alpha = 1f
            runnable.run()
        }

        val fragments = supportFragmentManager.fragments.filter { it.javaClass in clazz }
        if (fragments.isEmpty()) {
            invalidate.run()
        } else {
            supportFragmentManager.commitNow {
                fragments.forEach(::remove)
                runOnCommit(invalidate)
            }
        }
    }


    private fun isStateSaved(): Boolean {
        return supportFragmentManager.isStateSaved || isFinishing || isDestroyed
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

    override fun toast(
        message: String,
        loading: Boolean,
        color: Int
    ) {
        toastView.show(message, loading, color)
    }
}