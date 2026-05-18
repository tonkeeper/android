package com.tonapps.trading

import android.os.Bundle
import android.view.View
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.bus.generated.Events.AssetScreen.AssetScreenFrom
import com.tonapps.wallet.data.events.tx.model.TxEvent
import com.tonapps.core.ComposableFragment
import io.tradingapi.models.AssetsTab
import io.tradingapi.models.MarketItem
import uikit.extensions.activity

class AssetsFragment : ComposableFragment() {

    interface Delegate {
        fun onOpenToken(token: TokenEntity, eventsOnly: Boolean = false)
        fun onOpenUrl(url: String)
        fun onOpenSwap(fromToken: String, toToken: String)
        fun onOpenSend(tokenAddress: String)
        fun onOpenReceive(token: TokenEntity?)
        fun onOpenTxDetails(tx: TxEvent, actionIndex: Int)
        fun onOpenStaking()
        fun onOpenUnverifiedInfo()
    }

    private val initial: AssetsRoutes
        get() {
            val args = arguments
            val assetId = args?.getString(ARG_ASSET_ID)
            return if (assetId != null) {
                AssetsRoutes.AssetDetails(
                    assetId = assetId,
                    previewName = args.getString(ARG_PREVIEW_NAME).orEmpty(),
                    previewImageUrl = args.getString(ARG_PREVIEW_IMAGE_URL).orEmpty(),
                    from = args.getString(ARG_FROM)
                        ?.let { value -> AssetScreenFrom.entries.firstOrNull { it.key == value } }
                        ?: AssetScreenFrom.TradeScreen,
                )
            } else {
                AssetsRoutes.Assets(
                    focusSearch = args?.getBoolean(ARG_FOCUS_SEARCH) ?: false,
                    initialTab = args?.getString(ARG_INITIAL_TAB)?.let { tabValue ->
                        AssetsTab.entries.firstOrNull { it.value == tabValue }
                    } ?: AssetsTab.all,
                )
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val delegate = context?.activity as? Delegate

        setContent {
            AssetsRouter(
                initial = initial,
                onOpenToken = { token, eventsOnly -> delegate?.onOpenToken(token, eventsOnly) },
                onOpenUrl = { delegate?.onOpenUrl(it) },
                onOpenSwap = { from, to -> delegate?.onOpenSwap(from, to) },
                onOpenSend = { delegate?.onOpenSend(it) },
                onOpenReceive = { delegate?.onOpenReceive(it) },
                onOpenTxDetails = { tx, index -> delegate?.onOpenTxDetails(tx, index) },
                onOpenStaking = { delegate?.onOpenStaking() },
                onOpenUnverifiedInfo = { delegate?.onOpenUnverifiedInfo() },
                onClose = { finish() },
            )
        }
    }

    companion object {
        private const val ARG_ASSET_ID = "asset_id"
        private const val ARG_PREVIEW_NAME = "preview_name"
        private const val ARG_PREVIEW_IMAGE_URL = "preview_image_url"
        private const val ARG_FOCUS_SEARCH = "focus_search"
        private const val ARG_INITIAL_TAB = "initial_tab"
        private const val ARG_FROM = "from"

        fun newInstance(
            focusSearch: Boolean = false,
            initialTab: AssetsTab = AssetsTab.all,
        ) = AssetsFragment().apply {
            arguments = Bundle().apply {
                putBoolean(ARG_FOCUS_SEARCH, focusSearch)
                putString(ARG_INITIAL_TAB, initialTab.value)
            }
        }

        fun newInstance(
            assetId: String,
            from: AssetScreenFrom,
            name: String = "",
            imageUrl: String = "",
        ) = AssetsFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_ASSET_ID, assetId)
                putString(ARG_PREVIEW_NAME, name)
                putString(ARG_PREVIEW_IMAGE_URL, imageUrl)
                putString(ARG_FROM, from.key)
            }
        }

        fun newInstance(
            marketItem: MarketItem,
            from: AssetScreenFrom,
        ) = newInstance(
            assetId = marketItem.asset.id,
            name = marketItem.asset.name,
            imageUrl = marketItem.asset.imageUrl,
            from = from,
        )
    }
}
