package com.tonkeeper.fragment.wallet.creating.list

import android.view.ViewGroup
import android.widget.Button
import androidx.annotation.LayoutRes
import com.tonkeeper.R
import com.tonkeeper.uikit.navigation.Navigation.Companion.nav
import com.tonkeeper.fragment.wallet.phrase.PhraseWalletFragment
import com.tonkeeper.uikit.list.BaseListHolder

internal open class PagerHolder(
    parent: ViewGroup,
    @LayoutRes resId: Int
): BaseListHolder<PagerItem>(parent, resId) {

    class Generating(parent: ViewGroup): PagerHolder(parent, R.layout.view_wallet_generating)
    class Created(parent: ViewGroup): PagerHolder(parent, R.layout.view_wallet_created)

    class Attention(parent: ViewGroup): PagerHolder(parent, R.layout.view_wallet_attention) {

        private val nextButton = findViewById<Button>(R.id.next)

        override fun onBind(item: PagerItem) {
            super.onBind(item)
            nextButton.setOnClickListener {
                context.nav()?.replace(PhraseWalletFragment.newInstance(), true)
            }
        }
    }

    override fun onBind(item: PagerItem) {

    }

}