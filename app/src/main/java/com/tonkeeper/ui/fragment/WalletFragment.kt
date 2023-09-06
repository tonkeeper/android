package com.tonkeeper.ui.fragment

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatTextView
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.tonkeeper.R
import com.tonkeeper.WalletState
import com.tonkeeper.api.Network
import com.tonkeeper.api.Wallet
import com.tonkeeper.extensions.inflate
import com.tonkeeper.extensions.toUserLikeTON
import com.tonkeeper.extensions.toUserLikeUSD
import com.tonkeeper.ui.list.wallet.item.WalletCellItem
import com.tonkeeper.ui.list.wallet.item.WalletItem
import com.tonkeeper.ui.list.wallet.item.WalletJettonCellItem
import com.tonkeeper.ui.list.wallet.item.WalletNftItem
import com.tonkeeper.ui.list.wallet.item.WalletStakingCellItem
import com.tonkeeper.ui.list.wallet.item.WalletTonCellItem
import com.tonkeeper.ui.list.pager.PagerAdapter
import com.tonkeeper.ui.list.pager.PagerHolder
import com.tonkeeper.ui.list.pager.PagerItem
import com.tonkeeper.ui.list.wallet.item.WalletGhostItem
import com.tonkeeper.ui.widget.HeaderView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class WalletFragment: BaseFragment(R.layout.fragment_wallet) {

    private lateinit var headerView: HeaderView
    private lateinit var amountView: AppCompatTextView
    private lateinit var addressView: AppCompatTextView
    private lateinit var tabsContainerView: View
    private lateinit var tabsView: TabLayout
    private lateinit var pagerView: ViewPager2

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        headerView = view.findViewById(R.id.header)
        headerView.doOnActionClick = {
            openWalletDialog()
        }

        amountView = view.findViewById(R.id.amount)
        addressView = view.findViewById(R.id.address)

        tabsContainerView = view.findViewById(R.id.tabs_container)
        tabsView = view.findViewById(R.id.tabs)
        pagerView = view.findViewById(R.id.pager)

        loadWallet()
    }

    private fun openWalletDialog() {
        val dialogView = requireContext().inflate(R.layout.dialog_change_wallet)
        val inputView = dialogView.findViewById<EditText>(R.id.input)
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(dialogView)
        builder.setPositiveButton(R.string.ok) { dialog, _ ->
            val address = inputView.text.toString()
            loadWallet(address)
            dialog.dismiss()
        }
        builder.show()
    }

    private fun emptyState() {
        val state = WalletState(
            address = "loading",
            amountUSD = 0.0f,
            pages = emptyList()
        )
        initState(state)
    }

    private fun loadWallet(address: String = "EQD2NmD_lH5f5u1Kj3KfGyTvhZSX0Eg6qp2a5IQUKXxOG21n") {
        emptyState()

        lifecycleScope.launch(Dispatchers.IO) {
            val wallet = Network.getWalletOrNull(address) ?: return@launch

            val tokens = buildTokensList(wallet)
            val collectibles = buildCollectibles(wallet)
            val itemsSize = tokens.size + collectibles.size
            val pages = mutableListOf<PagerItem>()

            if (itemsSize >= 10) {
                val tokensPage = PagerItem(
                    title = getString(R.string.tokens),
                    items = tokens
                )

                pages.add(tokensPage)

                val collectiblesPage = PagerItem(
                    title = getString(R.string.collectibles),
                    items = collectibles
                )
                pages.add(collectiblesPage)
            } else {
                val fullPage = PagerItem(
                    title = getString(R.string.tokens),
                    items = tokens + collectibles
                )

                pages.add(fullPage)
            }

            val state = WalletState(
                address = address,
                amountUSD = wallet.balanceUSD,
                pages = pages
            )

            withContext(Dispatchers.Main) {
                initState(state)
            }
        }
    }

    private fun buildTokensList(wallet: Wallet): List<WalletItem> {
        val tonItem = WalletTonCellItem(
            balance = wallet.balanceTON.toUserLikeTON(),
            balanceUSD = wallet.balanceUSD.toUserLikeUSD(),
            rate = wallet.rate.toUserLikeUSD(),
            rateDiff24h = wallet.rateDiff24h
        )

        val tokenItems = mutableListOf<WalletItem>()
        tokenItems.add(tonItem)
        tokenItems.add(WalletStakingCellItem)
        for ((index, jetton) in wallet.jettons.withIndex()) {
            val cellPosition = WalletCellItem.getPosition(wallet.jettons.size, index)
            val item = WalletJettonCellItem(
                position = cellPosition,
                iconURI = Uri.parse(jetton.imageURL),
                code = jetton.symbol,
                balance = String.format("%.2f", jetton.amount)
            )
            tokenItems.add(item)
        }

        val column = tokenItems.size % PagerHolder.spanCount
        if (column == 1) {
            tokenItems.add(WalletGhostItem)
            tokenItems.add(WalletGhostItem)
        }

        return tokenItems
    }

    private fun buildCollectibles(wallet: Wallet): List<WalletItem> {
        val nftItems = mutableListOf<WalletItem>()
        for (nft in wallet.nfts) {
            val item = WalletNftItem(
                imageURI = Uri.parse(nft.displayImageURL),
                title = nft.displayTitle,
                description = nft.displayDescription,
                mark = Random.nextBoolean()
            )
            nftItems.add(item)
        }
        return nftItems
    }

    private fun initState(state: WalletState) {
        amountView.text = state.amountUserLikeUSD
        addressView.text = state.shortAddress
        pagerView.adapter = PagerAdapter(state.pages)

        if (state.pages.size == 1) {
            tabsContainerView.visibility = TabLayout.GONE
        } else {
            tabsContainerView.visibility = TabLayout.VISIBLE

            TabLayoutMediator(tabsView, pagerView) { tab, position ->
                tab.text = state.pages[position].title
            }.attach()
        }
    }
}