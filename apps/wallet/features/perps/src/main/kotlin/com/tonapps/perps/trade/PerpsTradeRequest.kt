package com.tonapps.perps.trade

import com.tonapps.perps.data.PerpsAutoCloseDraft
import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.wallet.data.multichain.perps.PerpsOperationKind
import java.math.BigDecimal

sealed interface PerpsTradeRequest {

    val walletId: String
    val marketIndex: Int
    val kind: PerpsOperationKind

    data class Open(
        override val walletId: String,
        override val marketIndex: Int,
        val side: PerpsOrderSide,
        val marginQuote: BigDecimal,
        val leverage: Int,
        val autoClose: PerpsAutoCloseDraft?,
        val limitPrice: BigDecimal?,
    ) : PerpsTradeRequest {

        override val kind: PerpsOperationKind
            get() = if (limitPrice == null) {
                PerpsOperationKind.OPEN
            } else {
                PerpsOperationKind.OPEN_LIMIT
            }
    }

    data class Close(
        override val walletId: String,
        override val marketIndex: Int,
    ) : PerpsTradeRequest {

        override val kind: PerpsOperationKind
            get() = PerpsOperationKind.CLOSE
    }

    data class Reduce(
        override val walletId: String,
        override val marketIndex: Int,
        val marginDelta: BigDecimal,
        val autoClose: PerpsAutoCloseEdit,
    ) : PerpsTradeRequest {

        override val kind: PerpsOperationKind
            get() = PerpsOperationKind.REDUCE
    }

    data class Add(
        override val walletId: String,
        override val marketIndex: Int,
        val marginDelta: BigDecimal,
        val autoClose: PerpsAutoCloseEdit,
    ) : PerpsTradeRequest {

        override val kind: PerpsOperationKind
            get() = PerpsOperationKind.ADD
    }

    data class Margin(
        override val walletId: String,
        override val marketIndex: Int,
        val amountQuote: BigDecimal,
        val add: Boolean,
    ) : PerpsTradeRequest {

        override val kind: PerpsOperationKind
            get() = if (add) {
                PerpsOperationKind.MARGIN_ADD
            } else {
                PerpsOperationKind.MARGIN_REMOVE
            }
    }

    data class OrderCancel(
        override val walletId: String,
        override val marketIndex: Int,
        val orderIndex: Long,
    ) : PerpsTradeRequest {

        override val kind: PerpsOperationKind
            get() = PerpsOperationKind.ORDER_CANCEL
    }

    data class OrderModify(
        override val walletId: String,
        override val marketIndex: Int,
        val orderIndex: Long,
        val price: BigDecimal,
    ) : PerpsTradeRequest {

        override val kind: PerpsOperationKind
            get() = PerpsOperationKind.ORDER_MODIFY
    }

    data class AutoClose(
        override val walletId: String,
        override val marketIndex: Int,
        val target: PerpsAutoCloseDraft?,
    ) : PerpsTradeRequest {

        override val kind: PerpsOperationKind
            get() = PerpsOperationKind.AUTO_CLOSE
    }
}

sealed interface PerpsAutoCloseEdit {
    data object Keep : PerpsAutoCloseEdit
    data class Set(val target: PerpsAutoCloseDraft?) : PerpsAutoCloseEdit
}
