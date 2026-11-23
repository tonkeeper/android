package com.tonapps.core.helper

import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.bus.core.contract.RedAttemptSource
import com.tonapps.bus.core.contract.RedMetadata
import com.tonapps.bus.generated.Events.SendNative.SendNativeFeeAsset
import com.tonapps.bus.generated.Events.SendNative.SendNativeFrom

/** `attempt_source` and `other_metadata` of the RED `op_attempt` for send operations. */
object SendRedMetadata {

    // The TRON chain key analyticsAssetId() maps to the native coin.
    private const val TRON_COIN_ADDRESS = "TRON"

    fun attemptSource(from: SendNativeFrom): String {
        return when (from) {
            SendNativeFrom.WalletScreen,
            SendNativeFrom.JettonScreen,
            SendNativeFrom.DeepLink,
            SendNativeFrom.QrCode -> RedAttemptSource.NATIVE_UI
            SendNativeFrom.TonconnectLocal,
            SendNativeFrom.TonconnectRemote,
            SendNativeFrom.Walletconnect -> from.key
        }
    }

    /**
     * [asset] is the analytics asset id (`ton/mainnet/jetton/0:…`); its chain segment is the
     * `asset_network`. [amount] is in display units.
     */
    fun transfer(
        source: String?,
        asset: String,
        amount: Double?,
        feePaidIn: String?,
        tokenSymbol: String?,
    ): String? {
        return RedMetadata.json(
            RedMetadata.SOURCE to source,
            RedMetadata.ASSET to asset,
            RedMetadata.AMOUNT to amount,
            RedMetadata.FEE_PAID_IN to feePaidIn,
            RedMetadata.ASSET_NETWORK to assetNetwork(asset),
            RedMetadata.TOKEN_SYMBOL to tokenSymbol?.takeIf { it.isNotBlank() },
        )
    }

    fun transfer(
        from: SendNativeFrom,
        asset: String,
        amount: Double?,
        feeAsset: SendNativeFeeAsset?,
        tokenSymbol: String?,
    ): String? {
        return transfer(from.key, asset, amount, feeAsset?.key, tokenSymbol)
    }

    /** An NFT is sent one at a time and has no token symbol. */
    fun nftTransfer(from: SendNativeFrom, asset: String, feeAsset: SendNativeFeeAsset?): String? {
        return transfer(from.key, asset, amount = 1.0, feePaidIn = feeAsset?.key, tokenSymbol = null)
    }

    /** A gasless swap fee is reported as `ton` too: only battery is told apart. */
    fun swap(
        from: WalletCurrency,
        to: WalletCurrency,
        fromAmount: Double?,
        toAmount: Double?,
        isBattery: Boolean,
    ): String? {
        return RedMetadata.json(
            RedMetadata.FROM_TOKEN to from.redSymbol(),
            RedMetadata.TO_TOKEN to to.redSymbol(),
            RedMetadata.FROM_AMOUNT to fromAmount,
            RedMetadata.TO_AMOUNT to toAmount,
            RedMetadata.FEE_PAID_IN to swapFeePaidIn(isBattery),
            RedMetadata.ASSET_NETWORK to assetNetwork(from.analyticsAssetId()),
            RedMetadata.TOKEN_SYMBOL to from.code.takeIf { it.isNotBlank() },
        )
    }

    private fun swapFeePaidIn(isBattery: Boolean): String {
        return when {
            isBattery -> "battery"
            else -> "ton"
        }
    }

    fun assetNetwork(asset: String): String? {
        return asset.substringBefore('/', missingDelimiterValue = "").takeIf { it.isNotEmpty() }
    }

    /** `ton_ton`, `usdt_ton`, `usdt_trc20`, `trx`. */
    fun WalletCurrency.redSymbol(): String {
        val symbol = code.replace("₮", "T").lowercase()
        return when (val chain = chain) {
            is WalletCurrency.Chain.TON -> when (chain.address) {
                WalletCurrency.TON_CHAIN_KEY -> "ton_ton"
                else -> "${symbol}_ton"
            }
            is WalletCurrency.Chain.TRON -> when (chain.address) {
                TRON_COIN_ADDRESS -> "trx"
                else -> "${symbol}_trc20"
            }
            else -> "${symbol}_${assetNetwork(analyticsAssetId())}"
        }
    }
}
