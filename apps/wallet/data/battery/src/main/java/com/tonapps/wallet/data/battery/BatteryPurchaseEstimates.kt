package com.tonapps.wallet.data.battery

import com.tonapps.wallet.data.battery.entity.BatteryPurchaseQuote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import java.math.BigInteger

/** What `/purchase-battery/estimate` answered for the amount typed in, in the asset's smallest units. */
sealed interface BatteryPurchaseEstimate {

    /** Nothing to estimate: no amount, or one that cannot be bought. */
    data object None : BatteryPurchaseEstimate

    data class Loading(val amount: BigInteger) : BatteryPurchaseEstimate

    data class Ready(val amount: BigInteger, val quote: BatteryPurchaseQuote) : BatteryPurchaseEstimate

    data class Failed(val amount: BigInteger, val error: Throwable) : BatteryPurchaseEstimate
}

/**
 * Estimates each input once it has stood for [debounceMs]: a newer one cancels the request of the one
 * before it, so only the last input's answer is ever shown. An input that is null, or whose [amountOf] is
 * not positive, is [BatteryPurchaseEstimate.None].
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T : Any> Flow<T?>.purchaseEstimates(
    debounceMs: Long,
    amountOf: (T) -> BigInteger,
    estimate: suspend (T) -> BatteryPurchaseQuote,
): Flow<BatteryPurchaseEstimate> = distinctUntilChanged().transformLatest { input ->
    val amount = input?.let(amountOf)
    if (input == null || amount == null || amount.signum() <= 0) {
        emit(BatteryPurchaseEstimate.None)
        return@transformLatest
    }

    emit(BatteryPurchaseEstimate.Loading(amount))
    delay(debounceMs)

    val result = try {
        BatteryPurchaseEstimate.Ready(amount, estimate(input))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        BatteryPurchaseEstimate.Failed(amount, e)
    }
    emit(result)
}
