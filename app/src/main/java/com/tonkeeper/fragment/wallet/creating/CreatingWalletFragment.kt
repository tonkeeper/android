package com.tonkeeper.fragment.wallet.creating

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.tonkeeper.R
import com.tonkeeper.fragment.wallet.creating.list.PagerAdapter
import com.tonkeeper.uikit.base.BaseFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CreatingWalletFragment: BaseFragment(R.layout.fragment_creating_wallet) {

    companion object {
        fun newInstance() = CreatingWalletFragment()
    }

    private lateinit var pagerView: ViewPager2

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pagerView = view.findViewById(R.id.pager)
        pagerView.isUserInputEnabled = false
        pagerView.adapter = PagerAdapter

        createWallet()
    }

    private fun createWallet() {
        lifecycleScope.launch(Dispatchers.Main) {
            delay(2000)
            walletCreated()
            delay(3000)
            walletAttention()
        }
    }

    private fun walletCreated() {
        pagerView.currentItem = 1
    }

    private fun walletAttention() {
        pagerView.currentItem = 2
    }
}