package com.tonapps.tonkeeper.fragment.send

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.viewpager2.widget.ViewPager2
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.tonkeeper.extensions.openCamera
import com.tonapps.tonkeeper.fragment.send.pager.PagerScreen
import com.tonapps.tonkeeper.fragment.send.pager.SendScreenAdapter
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.hideKeyboard
import uikit.mvi.UiScreen
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.HeaderView

class SendScreen: UiScreen<SendScreenState, SendScreenEffect, SendScreenFeature>(R.layout.fragment_send), BaseFragment.BottomSheet {

    companion object {

        private const val ADDRESS_KEY = "address"
        private const val COMMENT_KEY = "text"
        private const val AMOUNT_KEY = "amount"
        private const val JETTON_ADDRESS_KEY = "jetton_address"
        private const val NFT_ADDRESS_KEY = "nft_address"

        fun newInstance(
            address: String? = null,
            comment: String? = null,
            amount: Double = 0.0,
            jettonAddress: String? = null,
            nftAddress: String? = null
        ): SendScreen {
            val fragment = SendScreen()
            fragment.arguments = Bundle().apply {
                putString(ADDRESS_KEY, address)
                putString(COMMENT_KEY, comment)
                putDouble(AMOUNT_KEY, amount)
                putString(JETTON_ADDRESS_KEY, jettonAddress)
                putString(NFT_ADDRESS_KEY, nftAddress)
            }
            return fragment
        }
    }

    private val startAddress: String by lazy { arguments?.getString(ADDRESS_KEY) ?: "" }
    private val startComment: String by lazy { arguments?.getString(COMMENT_KEY) ?: "" }
    private val startAmount: Double by lazy { arguments?.getDouble(AMOUNT_KEY) ?: 0.0 }
    private val startJettonAddress: String? by lazy { arguments?.getString(JETTON_ADDRESS_KEY) }
    private val startNftAddress: String? by lazy { arguments?.getString(NFT_ADDRESS_KEY) }

    private val hasStartValues: Boolean
        get() = startAddress.isNotEmpty() || startComment.isNotEmpty() || startAmount > 0f || startJettonAddress != null

    override val feature: SendScreenFeature by viewModel()

    private lateinit var pageAdapter: SendScreenAdapter

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)
            feature.setCurrentPage(position)
            if (position == 0) {
                headerView.setDefault()
                headerView.setIcon(0)
            } else {
                headerView.setIcon(UIKitIcon.ic_chevron_left_16)
            }
            for (i in 0 until pageAdapter.itemCount) {
                val fragment = pageAdapter.findFragmentByPosition(i) as? PagerScreen<*, *, *>
                fragment?.visible = i == position
            }
        }
    }

    private lateinit var headerView: HeaderView
    private lateinit var pagerView: ViewPager2

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pageAdapter = SendScreenAdapter(this, feature.getPagerItems(startNftAddress != null))
        feature.setNftAddress(startNftAddress)

        headerView = view.findViewById(R.id.header)
        headerView.contentMatchParent()
        headerView.doOnActionClick = { finish() }
        headerView.doOnCloseClick = { feature.prevPage() }

        pagerView = view.findViewById(R.id.pager)
        pagerView.offscreenPageLimit = pageAdapter.itemCount
        pagerView.isUserInputEnabled = false
        pagerView.adapter = pageAdapter
        pagerView.registerOnPageChangeCallback(pageChangeCallback)

        if (hasStartValues) {
            view.postDelayed({
                forceSet()
            }, 1000)
        }
    }

    private fun forceSet() {
        forceSetAddress(startAddress)
        forceSetComment(startComment)
        forceSetAmount(startAmount)
        forceSetJetton(startJettonAddress)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pagerView.unregisterOnPageChangeCallback(pageChangeCallback)
    }

    override fun onEndShowingAnimation() {
        super.onEndShowingAnimation()
        feature.readyView()
    }

    override fun onDragging() {
        super.onDragging()
        getCurrentFocus()?.hideKeyboard()
    }

    override fun newUiState(state: SendScreenState) {
        pagerView.currentItem = state.currentPage
        headerView.title = state.headerTitle
        headerView.setSubtitle(state.headerSubtitle)
        if (state.headerVisible) {
            headerView.showText()
        } else {
            headerView.hideText()
        }
    }

    override fun newUiEffect(effect: SendScreenEffect) {
        super.newUiEffect(effect)

        if (effect is SendScreenEffect.OpenCamera) {
            navigation?.openCamera()
        } else if (effect is SendScreenEffect.Finish) {
            finish()
        }
    }

    fun forceSetAddress(address: String?) {
        pageAdapter.recipientScreen?.setAddress(address)
    }

    fun forceSetComment(text: String?) {
        pageAdapter.recipientScreen?.setComment(text)
    }

    fun forceSetAmount(amount: Double) {
        pageAdapter.amountScreen?.forceSetAmount(amount)
    }

    fun forceSetJetton(address: String?) {
        pageAdapter.amountScreen?.forceSetJetton(address)
    }
}