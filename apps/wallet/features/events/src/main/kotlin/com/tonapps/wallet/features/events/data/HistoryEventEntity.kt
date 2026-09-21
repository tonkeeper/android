package com.tonapps.wallet.features.events.data

import com.tonapps.blockchain.ton.extensions.isValidTonAddress
import com.tonapps.chainkit.core.chain.model.num.BaseUnit
import com.tonapps.extensions.lazyUnsafe
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
    val fromChain: Chain,
    val toChain: Chain,
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

    val isIncomingLike: Boolean
        get() = when (activityType) {
            ActivityType.receive, ActivityType.mint -> true
            ActivityType.send, ActivityType.stake, ActivityType.unstake, ActivityType.burn,
            ActivityType.dns_renew -> false
            ActivityType.swap -> isIncoming
            else -> isIncoming
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

