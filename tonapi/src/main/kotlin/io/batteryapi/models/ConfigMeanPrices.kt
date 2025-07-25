/**
 *
 * Please note:
 * This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * Do not edit this file manually.
 *
 */

@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package io.batteryapi.models


import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 
 *
 * @param batteryMeanPriceSwap 
 * @param batteryMeanPriceJetton 
 * @param batteryMeanPriceNft 
 * @param batteryMeanPriceTronUsdt 
 */


data class ConfigMeanPrices (

    @Json(name = "battery_mean_price_swap")
    val batteryMeanPriceSwap: kotlin.Int,

    @Json(name = "battery_mean_price_jetton")
    val batteryMeanPriceJetton: kotlin.Int,

    @Json(name = "battery_mean_price_nft")
    val batteryMeanPriceNft: kotlin.Int,

    @Json(name = "battery_mean_price_tron_usdt")
    val batteryMeanPriceTronUsdt: kotlin.Int? = null

) {


}

