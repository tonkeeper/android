package com.tonkeeper.core.history

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.tonapps.tonkeeperx.R

@get:DrawableRes
val ActionType.iconRes: Int
    get() = when (this) {
        ActionType.Received, ActionType.NftReceived, ActionType.JettonMint -> R.drawable.ic_tray_arrow_down_28
        ActionType.Send, ActionType.NftSend, ActionType.AuctionBid -> R.drawable.ic_tray_arrow_up_28
        ActionType.CallContract, ActionType.DepositStake, ActionType.Unknown -> R.drawable.ic_gear_28
        ActionType.Swap -> R.drawable.ic_swap_horizontal_alternative_28
        ActionType.DeployContract, ActionType.WithdrawStakeRequest, ActionType.WithdrawStake -> R.drawable.ic_donemark_28
        ActionType.DomainRenewal -> R.drawable.ic_return_28
        ActionType.NftPurchase -> R.drawable.ic_shopping_bag_28
        ActionType.JettonBurn -> R.drawable.ic_fire_28
        ActionType.UnSubscribe -> R.drawable.ic_xmark_28
        ActionType.Subscribe -> R.drawable.ic_bell_28
    }


@get:StringRes
val ActionType.nameRes: Int
    get() = when (this) {
        ActionType.Received, ActionType.NftReceived, ActionType.JettonMint -> R.string.received
        ActionType.Send, ActionType.NftSend -> R.string.sent
        ActionType.CallContract -> R.string.call_contract
        ActionType.Swap -> R.string.swap
        ActionType.DeployContract -> R.string.wallet_initialized
        ActionType.DepositStake -> R.string.stake
        ActionType.AuctionBid -> R.string.bid
        ActionType.WithdrawStakeRequest -> R.string.unstake_request
        ActionType.DomainRenewal -> R.string.domain_renew
        ActionType.WithdrawStake -> R.string.unstake
        ActionType.Unknown -> R.string.unknown
        ActionType.NftPurchase -> R.string.nft_purchase
        ActionType.JettonBurn -> R.string.burned
        ActionType.UnSubscribe -> R.string.unsubscribed
        ActionType.Subscribe -> R.string.subscribed
    }