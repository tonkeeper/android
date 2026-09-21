package com.tonapps.tonkeeper.ui.screen.wallet.main.list

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder.AlertHolder
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder.ApkHolder
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder.AssetsHeaderHolder
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder.BalanceHolder
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder.BannersHolder
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder.CollectiblesHolder
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder.MoreAssetsHolder
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder.NewActionsHolder
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder.NewSkeletonHolder
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder.PushHolder
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder.ReNewDomainsHolder
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder.SetupLinkHolder
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder.SetupSwitchHolder
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder.SetupTitleHolder
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder.SpaceHolder
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder.StakedHolder
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder.TokenHolder
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem

class WalletAdapter: BaseListAdapter() {

    override fun createHolder(
        parent: ViewGroup,
        viewType: Int
    ): BaseListHolder<out BaseListItem> {
        return when(viewType) {
            Item.TYPE_BALANCE -> BalanceHolder(parent)
            Item.TYPE_ACTIONS -> NewActionsHolder(parent)
            Item.TYPE_TOKEN -> TokenHolder(parent)
            Item.TYPE_SPACE -> SpaceHolder(parent)
            Item.TYPE_SKELETON -> NewSkeletonHolder(parent)
            Item.TYPE_PUSH -> PushHolder(parent)
            Item.TYPE_ALERT -> AlertHolder(parent)
            Item.TYPE_SETUP_TITLE -> SetupTitleHolder(parent)
            Item.TYPE_SETUP_SWITCH -> SetupSwitchHolder(parent)
            Item.TYPE_SETUP_LINK -> SetupLinkHolder(parent)
            Item.TYPE_STAKED -> StakedHolder(parent)
            Item.TYPE_APK_STATUS -> ApkHolder(parent)
            Item.TYPE_RENEW_DOMAINS -> ReNewDomainsHolder(parent)
            Item.TYPE_BANNERS -> BannersHolder(parent)
            Item.TYPE_COLLECTIBLES -> CollectiblesHolder(parent)
            Item.TYPE_MORE_ASSETS -> MoreAssetsHolder(parent)
            Item.TYPE_ASSETS_HEADER -> AssetsHeaderHolder(parent)
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

}