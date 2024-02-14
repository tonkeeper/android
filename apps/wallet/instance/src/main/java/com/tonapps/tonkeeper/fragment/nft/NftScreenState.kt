package com.tonapps.tonkeeper.fragment.nft

import io.tonapi.models.NftItem
import uikit.mvi.AsyncState
import uikit.mvi.UiState

data class NftScreenState(
    val asyncState: AsyncState = AsyncState.Default,
    val testnet: Boolean = false,
    val nftItem: NftItem? = null
): UiState()