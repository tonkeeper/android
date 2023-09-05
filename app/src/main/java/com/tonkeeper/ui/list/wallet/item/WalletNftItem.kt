package com.tonkeeper.ui.list.wallet.item

import android.net.Uri

data class WalletNftItem(
    val imageURI: Uri,
    val title: String,
    val description: String,
    val mark: Boolean
): WalletItem(TYPE_NFT)