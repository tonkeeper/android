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

import io.batteryapi.models.PurchasesPurchasesInner

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 
 *
 * @param totalPurchases 
 * @param purchases 
 */


data class Purchases (

    @Json(name = "total_purchases")
    val totalPurchases: kotlin.Int,

    @Json(name = "purchases")
    val purchases: kotlin.collections.List<PurchasesPurchasesInner>

) {


}

