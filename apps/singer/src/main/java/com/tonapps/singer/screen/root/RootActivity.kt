package com.tonapps.singer.screen.root

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.lifecycle.lifecycleScope
import com.tonapps.singer.R
import com.tonapps.singer.screen.intro.IntroFragment
import com.tonapps.singer.screen.main.MainFragment
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseActivity
import uikit.base.BaseFragment
import uikit.extensions.doOnEnd
import uikit.extensions.hapticConfirm
import uikit.extensions.startAnimation
import uikit.navigation.Navigation

class RootActivity: BaseActivity(), Navigation, ViewTreeObserver.OnPreDrawListener {

    companion object {
        val hostFragmentId = R.id.nav_host_fragment
    }

    private val rootViewModel: RootViewModel by viewModel()

    private lateinit var contentView: View
    private lateinit var toastView: AppCompatTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_root)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        contentView = findViewById(android.R.id.content)
        contentView.viewTreeObserver.addOnPreDrawListener(this)

        toastView = findViewById(R.id.toast)

        rootViewModel.hasKeys.onEach {
            val hasKeys = it ?: return@onEach
            if (hasKeys) {
                setMainFragment()
            } else {
                setIntroFragment()
            }
        }.launchIn(lifecycleScope)
    }

    override fun onPreDraw(): Boolean {
        if (rootViewModel.initialized) {
            contentView.viewTreeObserver.removeOnPreDrawListener(this)
            return true
        }
        return false
    }

    override fun setFragmentResult(requestKey: String, result: Bundle) {
        supportFragmentManager.setFragmentResult(requestKey, result)
    }

    override fun setFragmentResultListener(
        requestKey: String,
        listener: (requestKey: String, bundle: Bundle) -> Unit
    ) {
        supportFragmentManager.setFragmentResultListener(requestKey, this, listener)
    }

    private fun setIntroFragment() {
        val introFragment = IntroFragment.newInstance()

        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(hostFragmentId, introFragment)
        transaction.setPrimaryNavigationFragment(introFragment)
        transaction.commitAllowingStateLoss()
    }

    private fun setMainFragment() {
        val mainFragment = MainFragment.newInstance()

        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(hostFragmentId, mainFragment)
        transaction.setPrimaryNavigationFragment(mainFragment)
        transaction.commitAllowingStateLoss()
    }

    override fun initRoot(skipPasscode: Boolean, intent: Intent?) {

    }

    override fun add(fragment: BaseFragment) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.add(hostFragmentId, fragment)
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

    override fun openURL(url: String, external: Boolean) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    override fun toast(message: String) {
        contentView.hapticConfirm()
        toastView.text = message

        if (toastView.visibility == View.VISIBLE) {
            return
        }

        toastView.visibility = View.VISIBLE
        toastView.startAnimation(uikit.R.anim.toast).doOnEnd {
            toastView.visibility = View.GONE
        }
    }
}