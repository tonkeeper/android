package com.tonapps.tonkeeper.core.history

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.icu.Coins
import com.tonapps.wallet.localization.Localization
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.wallet.api.extensions.toTokenEntity
import com.tonapps.wallet.data.events.ActionType
import com.tonapps.wallet.data.rates.RatesRepository
import io.tonapi.models.Action
import io.tonapi.models.JettonSwapAction
import io.tonapi.models.JettonTransferAction

suspend fun Action.getTonAmountRaw(network: TonNetwork, ratesRepository: RatesRepository): Coins {
    val tonAmount = tonTransfer?.let { Coins.of(it.amount) }
    val jettonAmountInTON = jettonTransfer?.let {
        val amountCoins = it.amountCoins
        val jettonAddress = it.jetton.address
        val rates = ratesRepository.getRates(network, WalletCurrency.TON, jettonAddress)
        rates.convert(jettonAddress, amountCoins)
    }
    return tonAmount ?: jettonAmountInTON ?: Coins.ZERO
}

val JettonSwapAction.tokenIn: TokenEntity
    get() {
        val jetton = jettonMasterIn?.toTokenEntity()
        return jetton ?: TokenEntity.TON
    }

val JettonTransferAction.amountCoins: Coins
    get() = Coins.ofNano(amount, jetton.decimals)

val JettonSwapAction.amountCoinsIn: Coins
    get() {
        val tonAmount = tonIn ?: return Coins.ofNano(amountIn, tokenIn.decimals)
        return Coins.of(tonAmount, tokenIn.decimals)
    }

val JettonSwapAction.tokenOut: TokenEntity
    get() {
        val jetton = jettonMasterOut?.toTokenEntity()
        return jetton ?: TokenEntity.TON
    }

val JettonSwapAction.amountCoinsOut: Coins
    get() {
        val tonAmount = tonOut ?: return Coins.ofNano(amountOut, tokenOut.decimals)
        return Coins.of(tonAmount, tokenOut.decimals)
    }


@get:DrawableRes
val ActionType.iconRes: Int
    get() = when (this) {
        ActionType.Received, ActionType.NftReceived, ActionType.JettonMint -> R.drawable.ic_tray_arrow_down_28
        ActionType.Send, ActionType.NftSend, ActionType.AuctionBid -> UIKitIcon.ic_tray_arrow_up_28
        ActionType.CallContract, ActionType.DepositStake, ActionType.Unknown -> UIKitIcon.ic_gear_28
        ActionType.Swap -> R.drawable.ic_swap_horizontal_alternative_28
        ActionType.SetSignatureAllowed, ActionType.AddExtension, ActionType.DeployContract, ActionType.WithdrawStakeRequest, ActionType.WithdrawStake -> UIKitIcon.ic_donemark_28
        ActionType.DomainRenewal -> R.drawable.ic_return_28
        ActionType.NftPurchase, ActionType.Purchase -> R.drawable.ic_shopping_bag_28
        ActionType.JettonBurn, ActionType.GasRelay -> R.drawable.ic_fire_28
        ActionType.SetSignatureNotAllowed, ActionType.RemoveExtension, ActionType.UnSubscribe -> R.drawable.ic_xmark_28
        ActionType.Subscribe -> R.drawable.ic_bell_28
        ActionType.Fee, ActionType.Refund -> R.drawable.ic_ton_28
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
