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

package io.tonapi.models

import io.tonapi.models.DecodedRawMessageMessage

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 
 *
 * @param message 
 * @param mode 
 */


data class DecodedRawMessage (

    @Json(name = "message")
    val message: DecodedRawMessageMessage,

    @Json(name = "mode")
    val mode: kotlin.Int

)
