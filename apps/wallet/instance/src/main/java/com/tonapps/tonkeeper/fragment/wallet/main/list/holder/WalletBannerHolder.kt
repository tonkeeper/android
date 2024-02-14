package com.tonapps.tonkeeper.fragment.wallet.main.list.holder

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.fragment.wallet.main.list.item.WalletBannerItem

class WalletBannerHolder(
    parent: ViewGroup
): WalletHolder<WalletBannerItem>(parent, R.layout.view_wallet_banner) {

    init {
        itemView.setOnClickListener { openStore() }
    }

    override fun onBind(item: WalletBannerItem) {

    }

    private fun openStore() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.ton_keeper"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}