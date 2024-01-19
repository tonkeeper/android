package uikit.navigation

import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.addCallback
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import uikit.R
import uikit.base.BaseActivity
import uikit.base.BaseFragment
import uikit.extensions.doOnEnd
import uikit.extensions.hapticConfirm
import uikit.extensions.startAnimation

abstract class NavigationActivity: BaseActivity(), Navigation, ViewTreeObserver.OnPreDrawListener {

    companion object {
        val hostFragmentId = R.id.root_container
        val hostSheetId = R.id.sheet_container
    }

    private lateinit var contentView: View
    private lateinit var toastView: AppCompatTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigation)
        WindowCompat.setDecorFitsSystemWindows(window, false)

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
        if (isInitialized()) {
            contentView.viewTreeObserver.removeOnPreDrawListener(this)
            return true
        }
        return false
    }

    abstract fun isInitialized(): Boolean

    override fun setFragmentResult(requestKey: String, result: Bundle) {
        supportFragmentManager.setFragmentResult(requestKey, result)
    }

    override fun setFragmentResultListener(
        requestKey: String,
        listener: (requestKey: String, bundle: Bundle) -> Unit
    ) {
        supportFragmentManager.setFragmentResultListener(requestKey, this, listener)
    }

    fun setPrimaryFragment(fragment: BaseFragment) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(hostFragmentId, fragment)
        transaction.setPrimaryNavigationFragment(fragment)
        transaction.commitAllowingStateLoss()
    }

    override fun add(fragment: BaseFragment) {
        val transaction = supportFragmentManager.beginTransaction()
        if (fragment is BaseFragment.BottomSheet) {
            transaction.add(hostSheetId, fragment)
        } else {
            transaction.add(hostFragmentId, fragment)
        }
        transaction.commitAllowingStateLoss()
    }

    override fun remove(fragment: Fragment) {
        if (supportFragmentManager.primaryNavigationFragment == fragment) {
            finish()
        } else {
            supportFragmentManager.commitNow {
                remove(fragment)
            }
        }
    }

    override fun toast(message: String) {
        contentView.hapticConfirm()
        toastView.text = message

        if (toastView.visibility == View.VISIBLE) {
            return
        }

        toastView.visibility = View.VISIBLE
        toastView.startAnimation(R.anim.toast).doOnEnd {
            toastView.visibility = View.GONE
        }
    }
}