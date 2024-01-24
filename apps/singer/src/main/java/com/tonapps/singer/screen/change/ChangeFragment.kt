package com.tonapps.singer.screen.change

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.tonapps.singer.R
import com.tonapps.singer.core.SimpleState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.pinToBottomInsets
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.HeaderView
import uikit.widget.LoaderView

class ChangeFragment: BaseFragment(R.layout.fragment_change), BaseFragment.SwipeBack {

    companion object {

        fun newInstance() = ChangeFragment()
    }

    private val changeViewModel: ChangeViewModel by viewModel()

    private val pagerCallback = object : ViewPager2.OnPageChangeCallback() {

        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)
            changeViewModel.setCurrentPage(position)
        }
    }

    private val adapter: FragmentStateAdapter by lazy {
        object : FragmentStateAdapter(this) {
            override fun getItemCount() = ChangeViewModel.PAGE_COUNT

            override fun createFragment(position: Int) = InputFragment.newInstance(position)
        }
    }

    private lateinit var headerView: HeaderView
    private lateinit var pagerView: ViewPager2
    private lateinit var actionView: View
    private lateinit var continueButton: Button
    private lateinit var loaderView: LoaderView
    private lateinit var successView: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }

        pagerView = view.findViewById(R.id.pager)
        pagerView.isUserInputEnabled = false
        pagerView.offscreenPageLimit = adapter.itemCount
        pagerView.adapter = adapter
        pagerView.registerOnPageChangeCallback(pagerCallback)

        actionView = view.findViewById(R.id.action)
        actionView.pinToBottomInsets()

        continueButton = view.findViewById(R.id.done)
        continueButton.setOnClickListener { changeViewModel.continuePassword() }

        loaderView = view.findViewById(R.id.loader)
        successView = view.findViewById(R.id.success)

        changeViewModel.uiState.onEach(::newUiState).launchIn(lifecycleScope)

        changeViewModel.uiPageIndex.onEach {
            pagerView.setCurrentItem(it, true)
        }.launchIn(lifecycleScope)

        changeViewModel.onReady.onEach {
            navigation?.toast(getString(R.string.password_changed))
            finish()
        }.launchIn(lifecycleScope)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pagerView.unregisterOnPageChangeCallback(pagerCallback)
    }

    private fun newUiState(state: UiState) {
        when (state) {
            is UiState.InputValid -> continueButton.isEnabled = state.valid
            is UiState.Task -> setTaskState(state.state == SimpleState.Loading)
        }
    }

    private fun setTaskState(loading: Boolean) {
        if (loading) {
            applyLoadingState()
        } else {
            applyDefaultState()
        }
    }

    private fun applyLoadingState() {
        loaderView.visibility = View.VISIBLE
        continueButton.visibility = View.GONE
        successView.visibility = View.GONE
    }

    private fun applyDefaultState() {
        continueButton.visibility = View.VISIBLE
        loaderView.visibility = View.GONE
        successView.visibility = View.GONE
    }
}