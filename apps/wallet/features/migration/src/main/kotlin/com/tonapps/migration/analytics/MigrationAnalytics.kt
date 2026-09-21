package com.tonapps.migration.analytics

import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.Migration.MigrationChain
import com.tonapps.bus.generated.Events.Migration.MigrationFailedPart
import com.tonapps.bus.generated.Events.Migration.MigrationFeeAsset
import com.tonapps.bus.generated.Events.Migration.MigrationFrom
import com.tonapps.migration.data.MigrationPrepareResult
import com.tonapps.migration.data.MigrationTronFee

object MigrationAnalytics {

    private val events: Events.Migration
        get() = AnalyticsHelper.Default.events.migration

    fun start(from: MigrationFrom) {
        events.migrationStart(from)
    }

    fun walletSelectionView() {
        events.migrateWalletSelectionView()
    }

    fun walletSelectionClick() {
        events.migrateWalletSelectionClick()
    }

    fun confirmationView(feeAsset: MigrationFeeAsset) {
        events.migrateTransactionConfirmationView(feeAsset)
    }

    fun transactionSuccess(feeAsset: MigrationFeeAsset) {
        events.migrateTransactionSuccess(feeAsset)
    }

    fun transactionError(
        feeAsset: MigrationFeeAsset,
        failedPart: MigrationFailedPart,
        isPartial: Boolean,
        error: Throwable?,
    ) {
        events.migrateTransactionError(
            feeAsset = feeAsset,
            failedPart = failedPart,
            isPartial = isPartial,
            errorType = error?.let { it::class.simpleName },
            errorCode = null,
            errorMessage = error?.message,
        )
    }

    fun prepareError(
        chain: MigrationChain,
        isBlocking: Boolean,
        error: Throwable? = null,
    ) {
        events.migrateTransactionPrepareError(
            chain = chain,
            isBlocking = isBlocking,
            errorType = error?.let { it::class.simpleName },
            errorCode = null,
            errorMessage = error?.message,
        )
    }

    fun from(source: String?): MigrationFrom {
        return when (source?.trim()?.lowercase()) {
            "settings" -> MigrationFrom.Settings
            "story", "stories" -> MigrationFrom.Story
            "banner" -> MigrationFrom.Banner
            "raffle", "raffles" -> MigrationFrom.Raffle
            else -> MigrationFrom.Deeplink
        }
    }

    fun feeAsset(result: MigrationPrepareResult): MigrationFeeAsset {
        if (result.paysTonFeeWithBattery) {
            return MigrationFeeAsset.BatteryCharges
        }
        return when (result.tronPrepare.fee) {
            is MigrationTronFee.Battery -> MigrationFeeAsset.BatteryCharges
            is MigrationTronFee.Ton -> MigrationFeeAsset.BatteryTonInstantFee
            is MigrationTronFee.Trx,
            MigrationTronFee.None,
            -> MigrationFeeAsset.Coin
        }
    }
}
