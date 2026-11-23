package com.tonapps.wallet.data.staking

import java.util.concurrent.TimeUnit

enum class StakingRewardsPeriod(private val days: Long?) {
    AllTime(null),
    Month(30),
    HalfYear(182),
    Year(365);

    fun startSeconds(nowMillis: Long): Long? {
        val days = days ?: return null
        return TimeUnit.MILLISECONDS.toSeconds(nowMillis - TimeUnit.DAYS.toMillis(days))
    }
}
