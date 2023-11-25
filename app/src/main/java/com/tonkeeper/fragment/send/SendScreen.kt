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

        private const val ADDRESS_KEY = "address"
        private const val COMMENT_KEY = "text"
        private const val AMOUNT_KEY = "amount"

        fun newInstance(
            address: String? = null,
            comment: String? = null,
            amount: Float = 0f
        ): SendScreen {
            val fragment = SendScreen()
            fragment.arguments = Bundle().apply {
                putString(ADDRESS_KEY, address)
                putString(COMMENT_KEY, comment)
                putFloat(AMOUNT_KEY, amount)
            }
            return fragment
        }
    }


    private val startAddress: String by lazy { arguments?.getString(ADDRESS_KEY) ?: "" }
    private val startComment: String by lazy { arguments?.getString(COMMENT_KEY) ?: "" }
    private val startAmount: Float by lazy { arguments?.getFloat(AMOUNT_KEY) ?: 0f }

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

    fun hideText() {
        headerView.hideText()
    }

    fun showText() {
        headerView.showText()
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

    override fun onResume() {
        super.onResume()
        var page = 0
        if (startAddress.isNotEmpty()) {
            feature.recipient = SendScreenFeature.Recipient(
                address = startAddress,
                comment = startComment,
                name = null
            )
            page++
        }
        if (startAmount > 0) {
            feature.amount = SendScreenFeature.Amount(
                amount = startAmount,
            )
            page++
        }
        pagerView.currentItem = page
    }
}