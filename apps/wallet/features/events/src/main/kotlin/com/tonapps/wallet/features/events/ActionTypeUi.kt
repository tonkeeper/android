package com.tonapps.wallet.features.events

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.events.ActionType
import com.tonapps.wallet.localization.Localization

@get:DrawableRes
val ActionType.iconRes: Int
    get() = when (this) {
        ActionType.Received, ActionType.NftReceived, ActionType.JettonMint -> UIKitIcon.ic_arrow_down_28
        ActionType.Send, ActionType.NftSend, ActionType.AuctionBid -> UIKitIcon.ic_tray_arrow_up_28
        ActionType.CallContract, ActionType.DepositStake, ActionType.Unknown -> UIKitIcon.ic_gear_28
        ActionType.Swap -> UIKitIcon.ic_swap_horizontal_outline_28
        ActionType.SetSignatureAllowed, ActionType.AddExtension, ActionType.DeployContract,
        ActionType.WithdrawStakeRequest, ActionType.WithdrawStake -> UIKitIcon.ic_donemark_28
        ActionType.DomainRenewal -> UIKitIcon.ic_update_24
        ActionType.NftPurchase, ActionType.Purchase -> UIKitIcon.ic_creditcard_28
        ActionType.JettonBurn, ActionType.GasRelay -> UIKitIcon.ic_flash_24
        ActionType.SetSignatureNotAllowed, ActionType.RemoveExtension, ActionType.UnSubscribe -> UIKitIcon.ic_xmark_circle_16
        ActionType.Subscribe -> UIKitIcon.ic_bell_28
        ActionType.Fee, ActionType.Refund -> UIKitIcon.ic_ton_28
    }

@get:StringRes
val ActionType.nameRes: Int
    get() = when (this) {
        ActionType.Received, ActionType.NftReceived, ActionType.JettonMint -> Localization.received
        ActionType.Send, ActionType.NftSend -> Localization.sent
        ActionType.CallContract -> Localization.call_contract
        ActionType.Swap -> Localization.swap
        ActionType.DeployContract -> Localization.wallet_initialized
        ActionType.DepositStake -> Localization.stake
        ActionType.AuctionBid -> Localization.bid
        ActionType.WithdrawStakeRequest -> Localization.unstake_request
        ActionType.DomainRenewal -> Localization.domain_renew
        ActionType.WithdrawStake -> Localization.unstake
        ActionType.Unknown -> Localization.unknown
        ActionType.NftPurchase -> Localization.nft_purchase
        ActionType.JettonBurn -> Localization.burned
        ActionType.UnSubscribe -> Localization.unsubscribed
        ActionType.Subscribe -> Localization.subscribed
        ActionType.Fee -> Localization.network_fee
        ActionType.Refund -> Localization.refund
        ActionType.Purchase -> Localization.purchase
        ActionType.GasRelay -> Localization.battery
        ActionType.AddExtension -> Localization.added_extension
        ActionType.RemoveExtension -> Localization.removed_extension
        ActionType.SetSignatureAllowed -> Localization.signature_allowed
        ActionType.SetSignatureNotAllowed -> Localization.signature_not_allowed
    }
