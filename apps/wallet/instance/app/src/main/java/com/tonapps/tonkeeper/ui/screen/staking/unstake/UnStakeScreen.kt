package com.tonapps.tonkeeper.ui.screen.staking.unstake

import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingEvent
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingScreen
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingScreen.ChildFragment
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingScreen.Companion
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingViewModel
import com.tonapps.tonkeeper.ui.screen.staking.stake.amount.StakeAmountFragment
import com.tonapps.tonkeeper.ui.screen.staking.stake.confirm.StakeConfirmFragment
import com.tonapps.tonkeeper.ui.screen.staking.stake.details.StakeDetailsFragment
import com.tonapps.tonkeeper.ui.screen.staking.stake.options.StakeOptionsFragment
import com.tonapps.tonkeeper.ui.screen.staking.unstake.amount.UnStakeAmountFragment
import com.tonapps.tonkeeper.ui.screen.staking.unstake.confirm.UnStakeConfirmFragment
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

class UnStakeScreen: BaseWalletScreen(R.layout.fragment_unstake), BaseFragment.BottomSheet {

    abstract class ChildFragment(layoutId: Int): SimpleFragment<UnStakeScreen>(layoutId) {

        val unStakeViewModel: UnStakeViewModel by viewModel(ownerProducer = { requireParentFragment() })

        open fun onKeyboardAnimation(offset: Int, progress: Float, isShowing: Boolean) {

        }

        override fun finish() {
            rootScreen?.backFragment()
        }
    }

    private val poolAddress: String by lazy { arguments?.getString(POOL_ADDRESS_KEY) ?:"" }
    override val viewModel: UnStakeViewModel by viewModel { parametersOf(poolAddress) }

    private lateinit var headerView: HeaderView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnActionClick = { finish() }

        applyPrimaryScreen()
        collectFlow(viewModel.eventFlow, ::onEvent)

        view.doKeyboardAnimation(::onKeyboardAnimation)
    }

    private fun onEvent(event: UnStakeEvent) {
        when(event) {
            is UnStakeEvent.OpenConfirm -> addFragment(UnStakeConfirmFragment.newInstance())
            is UnStakeEvent.Finish -> {
                navigation?.openURL("tonkeeper://activity")
                finish()
            }
        }
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

    private fun addFragment(fragment: ChildFragment) {
        (childFragmentManager.fragments.lastOrNull() as? ChildFragment)?.visibleState = false

        childFragmentManager.commit {
            add(fragmentContainerId, fragment)
            runOnCommit { updateHeader() }
        }
    }

    private fun applyPrimaryScreen() {
        val primaryScreen = UnStakeAmountFragment.newInstance()
        childFragmentManager.commit {
            setPrimaryNavigationFragment(primaryScreen)
            replace(fragmentContainerId, primaryScreen)
        }
    }

    private fun updateHeader() {
        val fragments = childFragmentManager.fragments
        headerView.title = (fragments.last() as ChildFragment).getTitle()
        if (1 >= fragments.size) {
            headerView.setIcon(0)
            headerView.doOnCloseClick = { }
        } else {
            headerView.setIcon(UIKitIcon.ic_chevron_left_16)
            headerView.doOnCloseClick = { backFragment() }
        }
    }

    companion object {

        private val fragmentContainerId = R.id.unstake_fragment_container

        private const val POOL_ADDRESS_KEY = "pool_address"

        fun newInstance(poolAddress: String): UnStakeScreen {
            val fragment = UnStakeScreen()
            fragment.arguments = Bundle().apply {
                putString(POOL_ADDRESS_KEY, poolAddress)
            }
            return fragment
        }

    }

}