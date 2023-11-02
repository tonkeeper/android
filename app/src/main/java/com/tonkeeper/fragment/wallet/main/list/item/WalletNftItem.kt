package com.tonkeeper.fragment.wallet.main.list.item

import android.net.Uri

data class WalletNftItem(
    val nftAddress: String,
    val imageURI: Uri,
    val title: String,
    val collectionName: String,
    val mark: Boolean
): WalletItem(TYPE_NFT)