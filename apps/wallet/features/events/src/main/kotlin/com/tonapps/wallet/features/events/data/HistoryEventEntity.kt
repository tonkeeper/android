package com.tonapps.wallet.features.events.data

import com.tonapps.blockchain.ton.extensions.isValidTonAddress
import com.tonapps.chainkit.core.chain.model.num.BaseUnit
import com.tonapps.extensions.lazyUnsafe
import com.tonapps.icu.Coins
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import io.walletapi.models.ActivityDirection
import io.walletapi.models.ActivityStatus
import io.walletapi.models.ActivityType
import io.walletapi.models.Chain
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Wallet activity row for the Events feature (mapped from multichain wallet API).
 */
data class HistoryEventEntity(
    val activityType: ActivityType,
    val status: ActivityStatus,
    val blockTime: OffsetDateTime,
    val walletAddress: String?,
    val fromAddress: String?,
    val toAddress: String?,
    val fromChain: Chain?,
    val toChain: Chain?,
    val inAmount: String?,
    val outAmount: String?,
    val inAmountUsd: Double?,
    val outAmountUsd: Double?,
    val feeAmount: String?,
    val feeAmountUsd: Double?,
    val inAsset: AssetEntity?,
    val outAsset: AssetEntity?,
    val feeAsset: AssetEntity?,
    val txIds: List<String>,
    val blockNumber: Long?,
    val protocol: String?,
    val explorerUrl: String?,
    val comment: String?,
    val direction: ActivityDirection,
    val isSpam: Boolean = false,
    val tronResource: TronResourceEntity? = null,
    val batteryCharges: Int? = null,
    val perps: PerpsMetaEntity? = null,
    val encryptedComment: EncryptedCommentEntity? = null,
) {
    val isPending: Boolean
        get() = status == ActivityStatus.pending

    val isFailed: Boolean
        get() = status == ActivityStatus.failed || status == ActivityStatus.dropped

    val isIncoming: Boolean
        get() = direction == ActivityDirection.`in`

    val isDomainRenew: Boolean
        get() = activityType == ActivityType.dns_renew

    val renewedDomain: String? by lazyUnsafe {
        protocol?.trim()?.takeIf { isDomainRenew && it.isNotEmpty() }
    }

    val displayComment: String?
        get() = renewedDomain ?: comment?.takeIf { it.isNotBlank() }

    val nftAssetId: String?
        get() {
            if (isSpam) {
                return null
            }
            val asset = when (direction) {
                ActivityDirection.`in` -> inAsset
                ActivityDirection.out, ActivityDirection.self -> outAsset
                ActivityDirection.unknown -> null
            } ?: return null
            return asset.id.takeIf(::isTonNftAssetId)
        }

    val isNftActivity: Boolean
        get() = nftAssetId != null

    val nftAddress: String? by lazyUnsafe {
        when {
            isSpam -> null
            isDomainRenew -> toAddress?.takeIf { toChain == Chain.ton && it.isValidTonAddress() }
            else -> nftAssetId?.let(::tonNftAddressOrNull)
        }
    }

    val isNftTransfer: Boolean
        get() = inAsset?.id?.let(::isTonNftAssetId) == true ||
            outAsset?.id?.let(::isTonNftAssetId) == true

    val isPerps: Boolean
        get() = perpsKind != null

    val perpsKind: PerpsEventKind? by lazyUnsafe {
        when (activityType) {
            ActivityType.perpsPeriodPosition_opened -> PerpsEventKind.Opened(perps?.side)
            ActivityType.perpsPeriodPosition_increased -> PerpsEventKind.Increased(perps?.side)
            ActivityType.perpsPeriodPosition_reduced -> perpsClosingKind(isFullClose = false)
            ActivityType.perpsPeriodPosition_closed -> perpsClosingKind(isFullClose = true)
            ActivityType.perpsPeriodBalance_deposit -> PerpsEventKind.Deposit
            ActivityType.perpsPeriodBalance_withdrawal -> PerpsEventKind.Withdrawal
            else -> null
        }
    }

    private val perpsResultLeg: Pair<AssetEntity, PerpsResultEntity>? by lazyUnsafe {
        if (!isPerps) {
            return@lazyUnsafe null
        }
        val incoming = inAmount?.let { amount ->
            inAsset?.let { it to perpsResult(amount, it, incoming = true) }
        }
        val outgoing = outAmount?.let { amount ->
            outAsset?.let { it to perpsResult(amount, it, incoming = false) }
        }
        val leg = if (perpsKind == PerpsEventKind.Withdrawal) {
            outgoing?.let { (asset, result) -> asset to result.withFeeIn(asset) } ?: incoming
        } else {
            incoming ?: outgoing
        } ?: return@lazyUnsafe null
        if (isPerpsBalanceChange) {
            leg.first to leg.second.copy(isPositive = perpsKind == PerpsEventKind.Deposit)
        } else {
            leg
        }
    }

    val perpsResult: PerpsResultEntity?
        get() = perpsResultLeg?.second

    val perpsResultAsset: AssetEntity?
        get() = perpsResultLeg?.first

    val isPerpsBalanceChange: Boolean
        get() = perpsKind == PerpsEventKind.Deposit || perpsKind == PerpsEventKind.Withdrawal

    val isPerpsOpenOrIncrease: Boolean
        get() = perpsKind is PerpsEventKind.Opened || perpsKind is PerpsEventKind.Increased

    val perpsFee: PerpsResultEntity? by lazyUnsafe {
        if (!isPerpsBalanceChange) {
            return@lazyUnsafe null
        }
        val amount = feeAmount ?: return@lazyUnsafe null
        val asset = feeAsset ?: return@lazyUnsafe null
        perpsResult(amount, asset, incoming = false).takeIf { it.value.isPositive }
    }

    val isIncomingLike: Boolean
        get() = when {
            isPerps -> perpsResult?.isPositive ?: true
            else -> when (activityType) {
                ActivityType.receive, ActivityType.mint -> true
                ActivityType.send, ActivityType.stake, ActivityType.unstake, ActivityType.burn,
                ActivityType.dns_renew -> false
                ActivityType.swap -> isIncoming
                else -> isIncoming
            }
        }

    val counterpartyAddress: String?
        get() {
            val address = when {
                activityType == ActivityType.swap -> toAddress
                isIncomingLike -> fromAddress
                else -> toAddress
            }
            return address?.takeIf { it.isNotBlank() }
        }

    val unitInAmount: BaseUnit? by lazyUnsafe {
        val asset = inAsset?.valueOrNull ?: return@lazyUnsafe null
        inAmount?.let { asset.decimals.baseUnit(it) }
    }

    val unitOutAmount: BaseUnit? by lazyUnsafe {
        val asset = outAsset?.valueOrNull ?: return@lazyUnsafe null
        outAmount?.let { asset.decimals.baseUnit(it) }
    }

    val unitFeeAmount: BaseUnit? by lazyUnsafe {
        val asset = feeAsset?.valueOrNull ?: return@lazyUnsafe null
        feeAmount?.let { asset.decimals.baseUnit(it) }
    }

    val listKey by lazyUnsafe {
        //TODO: TK-1649 remove with stable backend
        UUID.randomUUID().toString()
//        txIds.joinToString(",")
//            .ifEmpty { "no_tx" } + "_" + activityType + "_" + blockNumber
    }

    private fun perpsClosingKind(isFullClose: Boolean): PerpsEventKind = when (perps?.reason) {
        PerpsCloseReason.Liquidation -> PerpsEventKind.Liquidated(perps.side)
        PerpsCloseReason.TakeProfit -> PerpsEventKind.TakeProfit
        PerpsCloseReason.StopLoss -> PerpsEventKind.StopLoss
        PerpsCloseReason.Manual, null -> if (isFullClose) {
            PerpsEventKind.Closed(perps?.side)
        } else {
            PerpsEventKind.Reduced(perps?.side)
        }
    }

    private fun PerpsResultEntity.withFeeIn(asset: AssetEntity): PerpsResultEntity {
        val fee = perpsFee?.takeIf { feeAsset?.id.equals(asset.id, ignoreCase = true) } ?: return this
        return copy(value = value + fee.value)
    }

    private fun perpsResult(amount: String, asset: AssetEntity, incoming: Boolean): PerpsResultEntity {
        val negative = amount.startsWith("-")
        return PerpsResultEntity(
            value = Coins.ofNano(amount.removePrefix("-"), asset.decimals),
            symbol = asset.symbol,
            isPositive = incoming != negative,
        )
    }
}

internal fun tonNftAddressOrNull(assetId: String): String? =
    assetId.substringAfterLast('/').takeIf { it.isNotBlank() }

private fun isTonNftAssetId(assetId: String): Boolean {
    val parts = assetId.split('/')
    if (parts.size < 4) {
        return false
    }
    if (!parts[0].equals(Chain.ton.value, ignoreCase = true)) {
        return false
    }
    return parts[2].startsWith("nft", ignoreCase = true)
}

data class TronResourceEntity(
    val energy: Long,
    val bandwidth: Long,
)

