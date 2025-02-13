package com.tonapps.tonkeeper.ui.base

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.RemoteConfig
import com.tonapps.tonkeeper.koin.remoteConfig
import com.tonapps.tonkeeper.koin.serverConfig
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.api.entity.ConfigEntity
import uikit.base.SimpleFragment
import uikit.extensions.doKeyboardAnimation
import uikit.navigation.Navigation

abstract class BaseHolderWalletScreen<C: ScreenContext>(
    screenContext: C
): BaseWalletScreen<C>(
    layoutId = R.layout.fragment_holder,
    screenContext = screenContext
) {

    interface Child<P: BaseHolderWalletScreen<*>, VM: BaseWalletVM> {
        val primaryFragment: P
        val primaryViewModel: VM

        fun setFragment(fragment: Fragment)

        fun popBackStack()

        fun popBackStack(name: String, flags: Int = 0)

        fun finish()

        fun onKeyboardAnimation(offset: Int, progress: Float, isShowing: Boolean) {

        }
    }

    abstract class ChildListScreen<C: ScreenContext, P: BaseHolderWalletScreen<C>, VM: BaseWalletVM>(
        screenContext: C
    ): BaseListWalletScreen<C>(screenContext), Child<P, VM> {

        override val viewModel: BaseWalletVM = EmptyBaseWalletVM()

        @Suppress("UNCHECKED_CAST")
        override val primaryFragment: P
            get() = requireParentFragment() as P

        @Suppress("UNCHECKED_CAST")
        override val primaryViewModel: VM
            get() = primaryFragment.viewModel as VM

        override fun setFragment(fragment: Fragment) {
            primaryFragment.setFragment(fragment)
        }

        override fun popBackStack() {
            primaryFragment.popBackStack()
        }

        override fun popBackStack(name: String, flags: Int) {
            primaryFragment.popBackStack(name, flags)
        }

        override fun finish() {
            primaryFragment.finish()
        }

        private class EmptyBaseWalletVM: BaseWalletVM(App.instance)
    }

    abstract class ChildFragment<P: BaseHolderWalletScreen<*>, VM: BaseWalletVM>(
        layoutId: Int
    ): SimpleFragment<P>(layoutId), Child<P, VM> {

        val navigation: Navigation?
            get() = primaryFragment.navigation

        val serverConfig: ConfigEntity?
            get() = context?.serverConfig

        val remoteConfig: RemoteConfig?
            get() = context?.remoteConfig

        @Suppress("UNCHECKED_CAST")
        override val primaryFragment: P
            get() = requireParentFragment() as P

        @Suppress("UNCHECKED_CAST")
        override val primaryViewModel: VM
            get() = primaryFragment.viewModel as VM

        override fun setFragment(fragment: Fragment) {
            primaryFragment.setFragment(fragment)
        }

        override fun popBackStack(name: String, flags: Int) {
            primaryFragment.popBackStack(name, flags)
        }

        override fun popBackStack() {
            primaryFragment.popBackStack()
        }

        override fun getTitle() = ""

        override fun finish() {
            primaryFragment.finish()
        }
    }

    private var keyboardOffset: Int = 0
    private var keyboardProgress: Float = 0f
    private var isKeyboardShowing: Boolean = false

    private val backStackChangedListener = object : FragmentManager.OnBackStackChangedListener {
        override fun onBackStackChanged() { }

        override fun onBackStackChangeCommitted(fragment: Fragment, pop: Boolean) {
            super.onBackStackChangeCommitted(fragment, pop)
            if (fragment.view != null) {
                (fragment as Child<*, *>).onKeyboardAnimation(keyboardOffset, keyboardProgress, isKeyboardShowing)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.doKeyboardAnimation(block = ::onKeyboardAnimation)
        childFragmentManager.addOnBackStackChangedListener(backStackChangedListener)
    }

    override fun onDestroyView() {
        childFragmentManager.removeOnBackStackChangedListener(backStackChangedListener)
        super.onDestroyView()
    }

    private fun onKeyboardAnimation(offset: Int, progress: Float, isShowing: Boolean) {
        keyboardOffset = offset
        keyboardProgress = progress
        isKeyboardShowing = isShowing

        for (fragment in childFragmentManager.fragments) {
            if (fragment is Child<*, *>) {
                fragment.onKeyboardAnimation(offset, progress, isShowing)
            }
        }
    }

    fun setFragment(fragment: Fragment) {
        val fragmentTag = fragment.toString()
        val isFragmentAdded = childFragmentManager.findFragmentByTag(fragmentTag) != null
        if (isFragmentAdded) {
            return
        }

        val transaction = childFragmentManager.beginTransaction()
        transaction.setCustomAnimations(uikit.R.anim.fragment_enter_from_right, uikit.R.anim.fragment_exit_to_left, uikit.R.anim.fragment_enter_from_left, uikit.R.anim.fragment_exit_to_right)
        transaction.replace(R.id.fragment_holder, fragment, fragmentTag)
        transaction.addToBackStack(fragmentTag)
        transaction.commit()
        // transaction.runOnCommit {
            // (fragment as Child<*, *>).onKeyboardAnimation(keyboardOffset, keyboardProgress, isKeyboardShowing)
        // }
    }

    fun popBackStack() {
        if (childFragmentManager.backStackEntryCount > 0) {
            childFragmentManager.popBackStack()
        }
    }

    fun popBackStack(name: String, flags: Int = 0) {
        childFragmentManager.popBackStack(name, flags)
    }

    override fun onBackPressed(): Boolean {
        if (childFragmentManager.backStackEntryCount > 1) {
            childFragmentManager.popBackStack()
            return false
        }
        return super.onBackPressed()
    }
}