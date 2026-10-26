package com.tonapps.wallet.data.battery.entity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** The chains the gasless relayer carries, by its own `chain_id` (`eth/mainnet`). */
@Parcelize
data class GaslessChainsEntity(
    val chainIds: List<String>,
) : Parcelable
