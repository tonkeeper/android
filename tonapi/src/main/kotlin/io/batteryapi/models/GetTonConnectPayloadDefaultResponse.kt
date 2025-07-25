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
 * @param error 
 * @param errorKey 
 * @param errorDetails 
 */


data class GetTonConnectPayloadDefaultResponse (

    @Json(name = "error")
    val error: kotlin.String,

    @Json(name = "error-key")
    val errorKey: kotlin.String? = null,

    @Json(name = "error-details")
    val errorDetails: kotlin.String? = null

) {


}

