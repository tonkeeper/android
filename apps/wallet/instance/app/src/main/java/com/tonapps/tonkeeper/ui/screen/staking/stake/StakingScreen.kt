package com.tonapps.tonkeeper.ui.screen.staking.stake

import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.ui.screen.staking.stake.amount.StakeAmountFragment
import com.tonapps.tonkeeper.ui.screen.staking.stake.confirm.StakeConfirmFragment
import com.tonapps.tonkeeper.ui.screen.staking.stake.details.StakeDetailsFragment
import com.tonapps.tonkeeper.ui.screen.staking.stake.options.StakeOptionsFragment
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.icon.UIKitIcon
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.base.SimpleFragment
import uikit.extensions.collectFlow
import uikit.extensions.doKeyboardAnimation
import uikit.extensions.hideKeyboard
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.HeaderView

class StakingScreen: BaseFragment(R.layout.fragment_stake), BaseFragment.BottomSheet {

    abstract class ChildFragment(layoutId: Int): SimpleFragment<StakingScreen>(layoutId) {

        val stakeViewModel: StakingViewModel by viewModel(ownerProducer = { requireParentFragment() })

        open fun onKeyboardAnimation(offset: Int, progress: Float, isShowing: Boolean) {

        }

        override fun finish() {
            rootScreen?.backFragment()
        }
    }

    private val poolAddress: String by lazy { arguments?.getString(POOL_ADDRESS_KEY) ?:"" }
    private val stakeViewModel: StakingViewModel by viewModel { parametersOf(poolAddress) }

    private lateinit var headerView: HeaderView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AnalyticsHelper.trackEvent("staking_open")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnActionClick = { finish() }

        applyPrimaryScreen()
        collectFlow(stakeViewModel.eventFlow, ::onEvent)

        view.doKeyboardAnimation(::onKeyboardAnimation)
    }

    override fun onDragging() {
        super.onDragging()
        requireContext().hideKeyboard()
    }

    private fun onKeyboardAnimation(offset: Int, progress: Float, isShowing: Boolean) {
        childFragmentManager.fragments.forEach {
            if (it is ChildFragment) {
                it.onKeyboardAnimation(offset, progress, isShowing)
            }
        }
    }

    private fun onEvent(event: StakingEvent) {
        when(event) {
            is StakingEvent.OpenOptions -> addFragment(StakeOptionsFragment.newInstance())
            is StakingEvent.OpenDetails -> addFragment(StakeDetailsFragment.newInstance(event.pool))
            is StakingEvent.OpenConfirm -> addFragment(StakeConfirmFragment.newInstance())
            is StakingEvent.Finish -> {
                navigation?.openURL("tonkeeper://activity")
                finish()
            }
        }
    }

    private fun addFragment(fragment: ChildFragment) {
        (childFragmentManager.fragments.lastOrNull() as? ChildFragment)?.visibleState = false

        childFragmentManager.commit {
            add(fragmentContainerId, fragment)
            runOnCommit { updateHeader() }
        }
    }

    fun backFragment(): Boolean {
        val fragments = childFragmentManager.fragments
        if (1 >= fragments.size) {
            return false
        }
        childFragmentManager.commit {
            remove(fragments.last())
            runOnCommit {
                (childFragmentManager.fragments.lastOrNull() as? ChildFragment)?.visibleState = true
                updateHeader()
            }
        }
        return true
    }

    private fun applyPrimaryScreen() {
        val primaryScreen = StakeAmountFragment.newInstance()
        childFragmentManager.commit {
            setPrimaryNavigationFragment(primaryScreen)
            replace(fragmentContainerId, primaryScreen)
        }
    }

    override fun onBackPressed() = !backFragment()

    private fun updateHeader() {
        val fragments = childFragmentManager.fragments
        headerView.title = (fragments.last() as ChildFragment).getTitle()
        if (1 >= fragments.size) {
            headerView.setIcon(UIKitIcon.ic_information_circle_16)
            headerView.doOnCloseClick = { openDetails() }
        } else {
            headerView.setIcon(UIKitIcon.ic_chevron_left_16)
            headerView.doOnCloseClick = { backFragment() }
        }
    }

    private fun openDetails() {

    }

    companion object {

        private val fragmentContainerId = R.id.stake_fragment_container

        private const val POOL_ADDRESS_KEY = "pool_address"

        fun newInstance(poolAddress: String? = null): StakingScreen {
            val fragment = StakingScreen()
            fragment.arguments = Bundle().apply {
                putString(POOL_ADDRESS_KEY, poolAddress)
            }
            return fragment
        }
    }
}