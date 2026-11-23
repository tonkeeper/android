package com.tonapps.wallet.data.battery.entity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** The methods `/recharge-methods?multichain=true` lists: TON's and the other chains' alike. */
@Parcelize
data class RechargeMethodsEntity(
    val methods: List<RechargeMethodEntity>,
) : Parcelable
