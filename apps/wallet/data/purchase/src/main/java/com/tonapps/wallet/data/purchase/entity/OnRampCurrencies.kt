package com.tonapps.wallet.data.purchase.entity

import android.util.Log
import com.tonapps.wallet.data.purchase.entity.OnRamp.Asset

data class OnRampCurrencies(
    val currencies: List<String>,
    val inputs: List<String>,
    val outputs: List<String>,
) {

    companion object {

        private val supportedChain = listOf(
            "TON"
        )

        private val supportedTONAssets = listOf(
            "TON", "USDT", "CATI", "MAJOR", "HMSTR", "PEPE"
        )

        private val supportedCrypto = listOf(
            "TON", "USDT"
        )

        private fun create(assets: List<Asset>, type: String): OnRampCurrencies {
            val fiatAssets = assets.filter { it.type == type }
            val inputs = fiatAssets.map { it.inputMethods }.flatten().map { it.type }.distinct()
            val outputs = fiatAssets.map { it.outputMethods }.flatten().map { it.type }.distinct()

            return OnRampCurrencies(
                currencies = fiatAssets.map { it.slug }.distinct(),
                inputs = inputs,
                outputs = outputs,
            )
        }

        fun fiat(assets: List<Asset>): OnRampCurrencies {
            val data = create(assets, "fiat")
            return data.copy(
                inputs = data.inputs.filter { !it.equals("apple_pay", ignoreCase = true) },
                outputs = data.outputs.filter { !it.equals("apple_pay", ignoreCase = true) },
            )
        }
    }
}