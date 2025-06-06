package com.tonapps.wallet.data.purchase.entity

import com.tonapps.wallet.data.purchase.entity.OnRamp.Asset
import com.tonapps.wallet.data.purchase.entity.OnRamp.PaymentMethod

data class TONAssetsEntity(
    val input: List<String>,
    val output: List<String>
) {

    companion object {

        private fun jettonAddresses(methods: List<PaymentMethod>): List<String> {
            val jettons = listOf("TON") + methods.filter { it.type == "jetton" }.mapNotNull { it.address }
            return jettons.distinct()
        }

        fun of(assets: List<Asset>): TONAssetsEntity {
            val inputMethods = jettonAddresses(assets.map { it.inputMethods }.flatten())
            val outputMethods = jettonAddresses(assets.map { it.outputMethods }.flatten())
            return TONAssetsEntity(
                input = inputMethods,
                output = outputMethods
            )
        }
    }
}