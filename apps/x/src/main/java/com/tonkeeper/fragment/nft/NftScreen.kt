package com.tonkeeper.fragment.nft

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.viewModels
import com.facebook.drawee.view.SimpleDraweeView
import com.tonapps.tonkeeperx.R
import com.tonkeeper.api.description
import com.tonkeeper.api.imageURL
import com.tonkeeper.api.ownerAddress
import com.tonkeeper.api.shortAddress
import com.tonkeeper.api.title
import com.tonkeeper.core.ExternalUrl
import com.tonkeeper.extensions.launch
import io.tonapi.models.NftItem
import ton.extensions.toUserFriendly
import uikit.base.BaseFragment
import uikit.extensions.verticalScrolled
import uikit.list.ListCell
import uikit.list.ListCell.Companion.drawable
import uikit.mvi.AsyncState
import uikit.mvi.UiScreen
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.HeaderView
import uikit.widget.LoaderView

class NftScreen: UiScreen<NftScreenState, NftScreenEffect, NftScreenFeature>(R.layout.fragment_nft), BaseFragment.BottomSheet {

    companion object {
        private const val NFT_ADDRESS_KEY = "nft_address"

        fun newInstance(nftAddress: String): NftScreen {
            val screen = NftScreen()
            screen.arguments = Bundle().apply {
                putString(NFT_ADDRESS_KEY, nftAddress)
            }
            return screen
        }
    }

    override val feature: NftScreenFeature by viewModels()

    private val nftAddress: String by lazy {
        arguments?.getString(NFT_ADDRESS_KEY) ?: ""
    }

    private lateinit var loaderView: LoaderView
    private lateinit var contentView: NestedScrollView
    private lateinit var headerView: HeaderView
    private lateinit var imageView: SimpleDraweeView
    private lateinit var nameView: AppCompatTextView
    private lateinit var collectionNameView: AppCompatTextView
    private lateinit var nftDescriptionView: AppCompatTextView
    private lateinit var collectionDescriptionView: AppCompatTextView
    private lateinit var openMarkerButton: Button
    private lateinit var openExplorerView: View
    private lateinit var ownerContainer: View
    private lateinit var ownerView: AppCompatTextView
    private lateinit var addressContainer: View
    private lateinit var addressView: AppCompatTextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loaderView = view.findViewById(R.id.loader)

        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }

        contentView = view.findViewById(R.id.content)
        contentView.verticalScrolled.launch(this) {
            headerView.divider = it
        }

        imageView = view.findViewById(R.id.image)

        nameView = view.findViewById(R.id.name)
        collectionNameView = view.findViewById(R.id.collection_name)
        nftDescriptionView = view.findViewById(R.id.nft_description)
        collectionDescriptionView = view.findViewById(R.id.collection_description)

        openMarkerButton = view.findViewById(R.id.open_marker)
        openMarkerButton.setOnClickListener {
            val url = ExternalUrl.nftMarketView(nftAddress.toUserFriendly(false))
            navigation?.openURL(url)
        }

        openExplorerView = view.findViewById(R.id.open_explorer)
        openExplorerView.setOnClickListener {
            val url = ExternalUrl.nftExplorerView(nftAddress)
            navigation?.openURL(url)
        }

        ownerContainer = view.findViewById(R.id.owner_container)
        ownerContainer.background = ListCell.Position.FIRST.drawable(view.context)
        ownerView = view.findViewById(R.id.owner)

        addressContainer = view.findViewById(R.id.address_container)
        addressContainer.background = ListCell.Position.LAST.drawable(view.context)

        addressView = view.findViewById(R.id.address)

        feature.load(nftAddress)
    }

    override fun newUiEffect(effect: NftScreenEffect) {
        super.newUiEffect(effect)
        if (effect is NftScreenEffect.FailedLoad) {
            finish()
        }
    }

    override fun newUiState(state: NftScreenState) {
        setAsyncState(state.asyncState)
        state.nftItem?.let { setNftItem(it) }
    }

    private fun setNftItem(nftItem: NftItem) {
        headerView.title = nftItem.title
        imageView.setImageURI(nftItem.imageURL)
        nameView.text = nftItem.title
        collectionNameView.text = nftItem.collection?.name
        collectionDescriptionView.text = nftItem.collection?.description
        ownerView.text = nftItem.ownerAddress?.shortAddress
        addressView.text = nftAddress.toUserFriendly(false).shortAddress
        setNftDescription(nftItem.description)
    }

    private fun setNftDescription(description: String?) {
        if (description.isNullOrBlank()) {
            nftDescriptionView.visibility = View.GONE
        } else {
            nftDescriptionView.visibility = View.VISIBLE
            nftDescriptionView.text = description
        }
    }

    private fun setAsyncState(asyncState: AsyncState) {
        if (asyncState == AsyncState.Loading) {
            loaderView.visibility = View.VISIBLE
            loaderView.resetAnimation()

            contentView.visibility = View.GONE
        } else {
            loaderView.visibility = View.GONE
            loaderView.stopAnimation()

            contentView.visibility = View.VISIBLE
        }
    }
}