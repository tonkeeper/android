package com.tonapps.perps.data

import io.perpsapi.models.Integrator
import io.perpsapi.models.TradingScreen
import kotlinx.serialization.Serializable
import java.math.BigDecimal

const val LIGHTER_API_KEY_INDEX = 3
const val LIGHTER_NIL_ACCOUNT_INDEX = 281_474_976_710_655L
const val MIN_LEVERAGE = 1

@Serializable
enum class PerpsOrderSide {
    LONG,
    SHORT,
}

@Serializable
enum class PerpsSizeDirection {
    ADD,
    REDUCE,
}

data class PerpsMarketRules(
    val maxLeverage: Int,
    val priceDecimals: Int,
    val sizeDecimals: Int,
    val tickSize: BigDecimal?,
    val stepSize: BigDecimal?,
    val minSizeBase: BigDecimal?,
    val minSizeQuote: BigDecimal?,
    val takerFeePct: BigDecimal?,
    val initialMarginBps: Int?,
    val maintenanceMarginBps: Int?,
    val markPrice: BigDecimal?,
    val tradable: Boolean,
)

data class PerpsAutoCloseDraft(
    val takeProfit: BigDecimal?,
    val stopLoss: BigDecimal?,
    val takeProfitRoi: BigDecimal? = null,
    val stopLossRoi: BigDecimal? = null,
)

data class PerpsNextNonce(
    val accountIndex: Long,
    val nonce: Long,
)

data class PerpsAccount(
    val accountIndex: Long?,
    val l1Address: String?,
    val hasL2Key: Boolean?,
    val hasRoToken: Boolean?,
    val availableBalance: String?,
    val collateral: String?,
    val apiKeys: List<PerpsAccountKey>?,
)

data class PerpsAccountKey(
    val apiKeyIndex: Int,
    val publicKey: String,
)

data class PerpsSignedTx(
    val txType: Int,
    val txInfo: String,
    val txHash: String,
)

data class PerpsSentTx(
    val txType: Int,
    val txHash: String,
)

data class PerpsPositionState(
    val open: PerpsEpisode?,
    val lastClosed: PerpsEpisode?,
    val orders: List<PerpsStateOrder>,
)

data class PerpsEpisode(
    val episodeId: String,
    val openedAtMs: Long,
)

data class PerpsStateOrder(
    val orderIndex: Long,
    val status: String,
    val filledBase: BigDecimal?,
)

data class PerpsOrderBook(
    val truncated: Boolean,
    val levels: List<Pair<BigDecimal, BigDecimal>>,
    val updatedAtMs: Long?,
)

data class PerpsTradingSnapshot(
    val screen: TradingScreen,
    val integrator: Integrator,
    val receivedAtMs: Long,
    val serverTimeMs: Long?,
)

enum class PerpsSendReason {
    RESIGN_REQUIRED,
    NO_RO_TOKEN,
    ORDER_GONE,
    BATCH_NOT_ACCEPTED,
    INVALID_TRANSACTION,
    LIGHTER_REJECTED,
    INTEGRATOR_FEE_MISMATCH,
    INTEGRATOR_NOT_APPROVED,
    OTHER,
}

fun String.isDeadOrderStatus(): Boolean = this == "rejected" || startsWith("canceled")

fun String.isFilledOrderStatus(): Boolean = this == "filled"

fun String.isWorkingOrderStatus(): Boolean = !isDeadOrderStatus() && !isFilledOrderStatus()
