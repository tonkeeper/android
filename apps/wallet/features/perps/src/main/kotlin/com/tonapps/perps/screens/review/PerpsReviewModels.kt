package com.tonapps.perps.screens.review

import androidx.annotation.StringRes
import com.tonapps.perps.data.PerpsAutoCloseDraft
import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.perps.data.PerpsSizeDirection
import com.tonapps.perps.screens.order.PerpsAutoCloseBounds
import com.tonapps.perps.screens.order.PerpsAutoCloseEstimate
import com.tonapps.wallet.localization.Localization
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
sealed interface PerpsReviewArgs {

    val marketIndex: Int
    val symbol: String
    val side: PerpsOrderSide
    val iconUrl: String?

    // A refusal returns to the form that produced the plan; Close has none.
    val hasForm: Boolean
        get() = this is Open || this is SizeChange || this is Margin

    @Serializable
    data class Open(
        override val marketIndex: Int,
        override val symbol: String,
        override val side: PerpsOrderSide,
        val marginQuote: String,
        val leverage: Int,
        val takeProfit: String? = null,
        val stopLoss: String? = null,
        val takeProfitRoi: String? = null,
        val stopLossRoi: String? = null,
        val limitPrice: String? = null,
        val autoCloseEditable: Boolean = false,
        override val iconUrl: String? = null,
    ) : PerpsReviewArgs

    @Serializable
    data class Close(
        override val marketIndex: Int,
        override val symbol: String,
        override val side: PerpsOrderSide,
        override val iconUrl: String? = null,
    ) : PerpsReviewArgs

    @Serializable
    data class SizeChange(
        override val marketIndex: Int,
        override val symbol: String,
        override val side: PerpsOrderSide,
        val direction: PerpsSizeDirection,
        val marginDelta: String,
        val autoClose: PerpsAutoCloseArgs,
        val restingTakeProfit: String? = null,
        val restingStopLoss: String? = null,
        val autoCloseEditable: Boolean = false,
        override val iconUrl: String? = null,
    ) : PerpsReviewArgs

    @Serializable
    data class Margin(
        override val marketIndex: Int,
        override val symbol: String,
        override val side: PerpsOrderSide,
        val amountQuote: String,
        val add: Boolean,
        override val iconUrl: String? = null,
    ) : PerpsReviewArgs
}

@Serializable
sealed interface PerpsAutoCloseArgs {

    @Serializable
    data object Keep : PerpsAutoCloseArgs

    @Serializable
    data class Set(
        val takeProfit: String? = null,
        val stopLoss: String? = null,
        val takeProfitRoi: String? = null,
        val stopLossRoi: String? = null,
    ) : PerpsAutoCloseArgs
}

data class PerpsReviewContent(
    @StringRes val title: Int,
    val rows: List<PerpsReviewRow>,
)

data class PerpsReviewRow(
    @StringRes val title: Int,
    val value: PerpsReviewValue,
    val previous: PerpsReviewValue? = null,
    val sub: PerpsReviewSub? = null,
    val tone: PerpsReviewTone = PerpsReviewTone.NEUTRAL,
)

sealed interface PerpsReviewValue {
    data class Text(val text: String) : PerpsReviewValue
    data class Leverage(val value: Int) : PerpsReviewValue
    data class AutoClose(val takeProfit: String?, val stopLoss: String?) : PerpsReviewValue {
        val isUnset: Boolean get() = takeProfit == null && stopLoss == null
    }
}

sealed interface PerpsReviewSub {
    data class Quote(val amount: String) : PerpsReviewSub
    data class Base(val amount: String) : PerpsReviewSub
    data class BaseDelta(val amount: String, val added: Boolean) : PerpsReviewSub
    data class Percent(val text: String) : PerpsReviewSub
}

enum class PerpsReviewTone {
    NEUTRAL,
    POSITIVE,
    NEGATIVE,
    WARNING,
}

enum class PerpsAutoCloseField {
    TAKE_PROFIT,
    STOP_LOSS,
}

internal fun String?.toAutoCloseField(): PerpsAutoCloseField? {
    return when (this) {
        "takeProfit" -> PerpsAutoCloseField.TAKE_PROFIT
        "stopLoss" -> PerpsAutoCloseField.STOP_LOSS
        else -> null
    }
}

@StringRes
internal fun PerpsAutoCloseField?.invalidAutoCloseText(): Int {
    return when (this) {
        PerpsAutoCloseField.TAKE_PROFIT -> Localization.perps_review_tp_update_title
        PerpsAutoCloseField.STOP_LOSS -> Localization.perps_review_sl_update_title
        null -> Localization.perps_review_auto_close_invalid
    }
}

data class PerpsAutoCloseFix(
    val field: PerpsAutoCloseField,
    val suggestion: BigDecimal?,
    val draft: PerpsAutoCloseDraft?,
)

data class PerpsReviewAutoCloseEditor(
    val priceDecimals: Int,
    val bounds: PerpsAutoCloseBounds,
    val initial: PerpsAutoCloseDraft?,
    val estimate: PerpsAutoCloseEstimate?,
    val limitPrice: BigDecimal? = null,
)

sealed interface PerpsReviewSheet {
    data class OrderNotPlaced(val slippagePpm: Long) : PerpsReviewSheet
    data class AutoCloseUpdate(val fix: PerpsAutoCloseFix) : PerpsReviewSheet
}

sealed interface PerpsReviewStatus {
    data object Loading : PerpsReviewStatus
    data object Ready : PerpsReviewStatus
    data object PriceUpdated : PerpsReviewStatus
    data object Submitting : PerpsReviewStatus
    data class Failed(@StringRes val text: Int) : PerpsReviewStatus
}
