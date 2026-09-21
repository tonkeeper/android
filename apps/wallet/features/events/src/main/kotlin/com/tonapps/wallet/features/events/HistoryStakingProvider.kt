package com.tonapps.wallet.features.events

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tonapps.apps.wallet.data.staking.R as StakingR
import com.tonapps.wallet.data.staking.StakingPool
import com.tonapps.wallet.features.events.data.HistoryEventEntity
import io.walletapi.models.ActivityType

internal enum class HistoryStakingProvider(
    val protocol: String,
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
) {
    LiquidTF(
        protocol = StakingPool.Implementation.LiquidTF.title,
        iconRes = StakingR.drawable.ic_tonstakers,
        titleRes = StakingR.string.stake_tonstakers,
    ),
    Whales(
        protocol = StakingPool.Implementation.Whales.title,
        iconRes = StakingR.drawable.whales,
        titleRes = StakingR.string.stake_whales,
    ),
    TF(
        protocol = StakingPool.Implementation.TF.title,
        iconRes = StakingR.drawable.tf,
        titleRes = StakingR.string.stake_nominators,
    ),
    ;

    companion object {
        fun of(activity: HistoryEventEntity): HistoryStakingProvider? {
            if (activity.activityType != ActivityType.stake &&
                activity.activityType != ActivityType.unstake
            ) {
                return null
            }
            return of(activity.protocol)
        }

        fun of(protocol: String?): HistoryStakingProvider? {
            if (protocol.isNullOrBlank()) {
                return null
            }
            return entries.firstOrNull { it.protocol == protocol }
        }
    }
}

@Composable
internal fun HistoryEventEntity.stakingProviderName(): String? {
    val provider = HistoryStakingProvider.of(this) ?: return null
    return stringResource(provider.titleRes)
}

@Composable
internal fun protocolDisplayName(protocol: String?): String? {
    if (protocol.isNullOrBlank()) {
        return null
    }
    val provider = HistoryStakingProvider.of(protocol)
    return if (provider != null) {
        stringResource(provider.titleRes)
    } else {
        protocol
    }
}
