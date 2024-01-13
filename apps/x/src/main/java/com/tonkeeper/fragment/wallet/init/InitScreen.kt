package com.tonkeeper.fragment.wallet.init

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.viewpager2.widget.ViewPager2
import com.tonapps.tonkeeperx.R
import com.tonkeeper.extensions.launch
import com.tonkeeper.fragment.wallet.init.pager.PagerAdapter
import kotlinx.coroutines.flow.filter
import uikit.base.BaseFragment
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.HeaderView

class InitScreen: BaseFragment(R.layout.fragment_pager), BaseFragment.SwipeBack {

    companion object {

        private const val ACTION_KEY = "action"

        fun newInstance(action: InitAction): InitScreen {
            val fragment = InitScreen()
            fragment.arguments = Bundle().apply {
                putInt(ACTION_KEY, action.ordinal)
            }
            return fragment
        }
    }

    private val action: InitAction by lazy {
        val value = arguments?.getInt(ACTION_KEY) ?: 0
        InitAction.entries[value]
    }

    private val viewModel: InitModel by viewModels(
        factoryProducer = { InitModel.Factory(action) },
    )

    private lateinit var headerView: HeaderView
    private lateinit var pagerView: ViewPager2

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }

        pagerView = view.findViewById(R.id.pager)
        pagerView.isUserInputEnabled = false
        pagerView.adapter = PagerAdapter(this, viewModel.pages)

        viewModel.currentPage.launch(this) {
            pagerView.setCurrentItem(it, true)
        }

        viewModel.ready.filter { it }.launch(this) {
            navigation?.initRoot(true)
        }
    }
}