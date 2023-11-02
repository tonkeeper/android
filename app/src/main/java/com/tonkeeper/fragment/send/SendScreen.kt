package com.tonkeeper.fragment.send

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.viewModels
import androidx.viewpager2.widget.ViewPager2
import com.tonkeeper.R
import com.tonkeeper.fragment.send.amount.AmountScreen
import com.tonkeeper.fragment.send.confirm.ConfirmScreen
import com.tonkeeper.fragment.send.pager.PagerScreen
import com.tonkeeper.fragment.send.pager.SendScreenAdapter
import com.tonkeeper.fragment.send.recipient.RecipientScreen
import uikit.base.fragment.BaseFragment
import uikit.mvi.UiScreen
import uikit.widget.HeaderView

class SendScreen: UiScreen<SendScreenState, SendScreenEffect, SendScreenFeature>(R.layout.fragment_send), BaseFragment.BottomSheet {

    companion object {
        fun newInstance() = SendScreen()
    }

    override val feature: SendScreenFeature by viewModels()

    private lateinit var pageAdapter: SendScreenAdapter

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)
            if (position == 0) {
                headerView.setDefault()
                headerView.setIcon(0)
            } else {
                headerView.setIcon(uikit.R.drawable.ic_chevron_left_16)
            }
            for (i in 0 until pageAdapter.itemCount) {
                val fragment = pageAdapter.findFragmentByPosition(i) as? PagerScreen<*, *, *>
                fragment?.visible = i == position
            }
        }
    }

    private lateinit var headerView: HeaderView
    private lateinit var pagerView: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageAdapter = SendScreenAdapter(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnActionClick = { finish() }
        headerView.doOnCloseClick = { prev() }

        pagerView = view.findViewById(R.id.pager)
        pagerView.isUserInputEnabled = false
        pagerView.adapter = pageAdapter
        pagerView.registerOnPageChangeCallback(pageChangeCallback)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pagerView.unregisterOnPageChangeCallback(pageChangeCallback)
    }

    fun setSubtitle(subtitle: String) {
        headerView.showSubtitle(subtitle)
    }

    fun prev() {
        val prevItem = pagerView.currentItem - 1
        if (prevItem >= 0) {
            pagerView.currentItem = prevItem
        } else {
            finish()
        }
    }

    fun next() {
        pagerView.currentItem = pagerView.currentItem + 1
    }

    override fun newUiState(state: SendScreenState) {

    }
}