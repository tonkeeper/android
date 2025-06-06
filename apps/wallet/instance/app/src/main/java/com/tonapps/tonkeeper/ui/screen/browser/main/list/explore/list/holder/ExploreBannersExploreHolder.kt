package com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.list.holder

import android.view.ViewGroup
import androidx.viewpager2.widget.ViewPager2
import com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.banners.BannerAppItem
import com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.banners.BannersAdapter
import com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.list.ExploreItem
import com.tonapps.tonkeeperx.R
import uikit.extensions.dp

class ExploreBannersExploreHolder(parent: ViewGroup): ExploreHolder<ExploreItem.Banners>(parent, R.layout.view_browser_banners) {

    private companion object {
        private var currentPosition = 0
        private var interval = 3000L
    }

    private val offsetHorizontal = 20.dp
    private val nextRunnable = Runnable { nextCurrentItem() }

    private val adapter = BannersAdapter()
    private val pagerView = findViewById<ViewPager2>(R.id.pager)

    init {
        pagerView.offscreenPageLimit = 3
        pagerView.adapter = adapter
        pagerView.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position != 0) {
                    currentPosition = position
                }
                nextCurrentItemDelayed()
            }
        })
        pagerView.setPageTransformer { page, position ->
            val offset = position * -(2 * offsetHorizontal)
            page.translationX = offset
        }
    }

    override fun onBind(item: ExploreItem.Banners) {
        val items = BannerAppItem.createApps(item.wallet, item.apps, item.country)
        interval = item.interval.toLong()
        adapter.submitList(items) {
            if (currentPosition == 0) {
                currentPosition = items.size * 1000
            }
            setCurrentItem(currentPosition, false)
        }
    }

    private fun setCurrentItem(item: Int, smoothScroll: Boolean) {
        pagerView.setCurrentItem(item, smoothScroll)
    }

    private fun nextCurrentItem() {
        currentPosition += 1
        setCurrentItem(currentPosition, true)
        nextCurrentItemDelayed()
    }

    private fun nextCurrentItemDelayed() {
        stopNextCurrentItem()
        pagerView.postDelayed(nextRunnable, interval)
    }

    private fun stopNextCurrentItem() {
        pagerView.removeCallbacks(nextRunnable)
    }

    override fun onUnbind() {
        super.onUnbind()
        stopNextCurrentItem()
    }

}