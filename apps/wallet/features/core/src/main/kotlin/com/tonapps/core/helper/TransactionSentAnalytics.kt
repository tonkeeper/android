package com.tonapps.core.helper

import android.util.Log
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentCategory
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentCategoryDetail
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentFeeAsset
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentInitiatedBy
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentWalletInterface
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentWalletMode
import com.tonapps.bus.generated.Events.TransactionSent.TransactionSentWalletSource

object TransactionSentAnalytics {

    fun transactionSent(
        wallet: WalletEntity,
        category: TransactionSentCategory,
        categoryDetail: TransactionSentCategoryDetail,
        asset: String,
        amount: Double,
        feeAsset: TransactionSentFeeAsset,
        initiatedBy: TransactionSentInitiatedBy,
        appId: String? = null,
        dappUrl: String? = null,
        isMax: Boolean? = null,
        toAsset: String? = null,
        stakingProvider: String? = null,
        isLiquid: Boolean? = null,
    ) {
        val walletInterface = walletInterface(wallet, asset) ?: run {
            Log.w("TransactionSentAnalytics", "dropping transaction_sent: no interface for ${wallet.version}")
            return
        }
        AnalyticsHelper.Default.events.transactionSent.transactionSent(
            category = category,
            categoryDetail = categoryDetail,
            asset = asset,
            amount = amount,
            feeAsset = feeAsset,
            walletInterface = walletInterface,
            walletSource = walletSource(wallet),
            walletMode = walletMode(wallet),
            initiatedBy = initiatedBy,
            appId = appId,
            dappUrl = dappUrl,
            isMax = isMax,
            toAsset = toAsset,
            stakingProvider = stakingProvider,
            isLiquid = isLiquid,
        )
    }

    fun initiatedBy(from: Events.SendNative.SendNativeFrom): TransactionSentInitiatedBy {
        return when (from) {
            Events.SendNative.SendNativeFrom.WalletScreen,
            Events.SendNative.SendNativeFrom.JettonScreen -> TransactionSentInitiatedBy.User
            Events.SendNative.SendNativeFrom.DeepLink -> TransactionSentInitiatedBy.DeepLink
            Events.SendNative.SendNativeFrom.QrCode -> TransactionSentInitiatedBy.QrCode
            Events.SendNative.SendNativeFrom.TonconnectLocal -> TransactionSentInitiatedBy.TonconnectLocal
            Events.SendNative.SendNativeFrom.TonconnectRemote -> TransactionSentInitiatedBy.TonconnectRemote
            Events.SendNative.SendNativeFrom.Walletconnect -> TransactionSentInitiatedBy.Walletconnect
        }
    }

    fun feeAsset(feeAsset: Events.SendNative.SendNativeFeeAsset): TransactionSentFeeAsset {
        return when (feeAsset) {
            Events.SendNative.SendNativeFeeAsset.Coin -> TransactionSentFeeAsset.Coin
            Events.SendNative.SendNativeFeeAsset.BatteryCharges -> TransactionSentFeeAsset.BatteryCharges
            Events.SendNative.SendNativeFeeAsset.BatteryTonInstantFee -> TransactionSentFeeAsset.BatteryTonInstantFee
            Events.SendNative.SendNativeFeeAsset.Gasless -> TransactionSentFeeAsset.Gasless
        }
    }

    private fun walletMode(wallet: WalletEntity): TransactionSentWalletMode {
        return if (wallet.type == WalletType.Multichain) {
            TransactionSentWalletMode.Multi
        } else {
            TransactionSentWalletMode.Single
        }
    }

    private fun walletSource(wallet: WalletEntity): TransactionSentWalletSource {
        return when {
            wallet.isLedger -> TransactionSentWalletSource.Ledger
            wallet.isKeystone -> TransactionSentWalletSource.Keystone
            wallet.signer -> TransactionSentWalletSource.Signer
            wallet.isWatchOnly -> TransactionSentWalletSource.Watchonly
            else -> TransactionSentWalletSource.Mnemonic
        }
    }

    private fun walletInterface(wallet: WalletEntity, asset: String): TransactionSentWalletInterface? {
        if (asset.startsWith("tron/")) {
            return TransactionSentWalletInterface.Eoa
        }
        return when (wallet.version) {
            WalletVersion.V5R1 -> TransactionSentWalletInterface.V5R1
            WalletVersion.V5BETA -> TransactionSentWalletInterface.V5Beta
            WalletVersion.V4R2 -> TransactionSentWalletInterface.V4R2
            WalletVersion.V4R1 -> TransactionSentWalletInterface.V4R1
            WalletVersion.V3R2 -> TransactionSentWalletInterface.V3R2
            WalletVersion.V3R1 -> TransactionSentWalletInterface.V3R1
            WalletVersion.UNKNOWN -> null
        }
    }
}
