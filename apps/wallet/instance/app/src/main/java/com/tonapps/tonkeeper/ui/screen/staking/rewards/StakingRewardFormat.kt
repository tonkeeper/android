package com.tonapps.tonkeeper.ui.screen.staking.rewards

import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.icu.TransactionDateFormatter
import com.tonapps.wallet.data.staking.entities.StakingRewardEntity
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

object StakingRewardFormat {

    const val NO_VALUE = "—"

    private val MINUTE_SECONDS = TimeUnit.MINUTES.toSeconds(1)
    private val HOUR_SECONDS = TimeUnit.HOURS.toSeconds(1)

    fun amount(value: Coins): CharSequence {
        return CurrencyFormatter.format(TokenEntity.TON.symbol, value, compact = true)
    }

    fun reward(reward: StakingRewardEntity): CharSequence {
        return income(reward.amount)
    }

    fun income(value: Coins): CharSequence {
        return "+${amount(value)}"
    }

    fun estimate(value: Coins): CharSequence {
        return "≈ ${amount(value)}"
    }

    fun apy(reward: StakingRewardEntity): CharSequence? {
        return reward.apyPercent?.let(CurrencyFormatter::formatPercent)
    }

    fun date(reward: StakingRewardEntity, locale: Locale): String {
        return TransactionDateFormatter.format(
            epochMillis = TimeUnit.SECONDS.toMillis(reward.timestampSeconds),
            locale = locale,
            dateFrom = TransactionDateFormatter.DateFrom.Always,
        )
    }

    fun roundEnds(remainingSeconds: Long): RoundEnds {
        return when {
            remainingSeconds < MINUTE_SECONDS -> RoundEnds.Soon
            remainingSeconds >= HOUR_SECONDS - MINUTE_SECONDS / 2 -> RoundEnds.InHours(
                roundedCount(remainingSeconds, HOUR_SECONDS)
            )
            else -> RoundEnds.InMinutes(roundedCount(remainingSeconds, MINUTE_SECONDS))
        }
    }

    private fun roundedCount(seconds: Long, unitSeconds: Long): Int {
        return (seconds.toDouble() / unitSeconds).roundToInt()
    }

    sealed interface RoundEnds {
        data object Soon : RoundEnds
        data class InHours(val count: Int) : RoundEnds
        data class InMinutes(val count: Int) : RoundEnds
    }
}
