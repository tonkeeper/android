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

package io.tonapi.apis

import io.kotlintest.shouldBe
import io.kotlintest.specs.ShouldSpec

import io.tonapi.apis.InscriptionsApi
import io.tonapi.models.AccountEvents
import io.tonapi.models.GetInscriptionOpTemplate200Response
import io.tonapi.models.InscriptionBalances
import io.tonapi.models.StatusDefaultResponse

class InscriptionsApiTest : ShouldSpec() {
    init {
        // uncomment below to create an instance of InscriptionsApi
        //val apiInstance = InscriptionsApi()

        // to test getAccountInscriptions
        should("test getAccountInscriptions") {
            // uncomment below to test getAccountInscriptions
            //val accountId : kotlin.String = 0:97264395BD65A255A429B11326C84128B7D70FFED7949ABAE3036D506BA38621 // kotlin.String | account ID
            //val limit : kotlin.Int = 56 // kotlin.Int | 
            //val offset : kotlin.Int = 56 // kotlin.Int | 
            //val result : InscriptionBalances = apiInstance.getAccountInscriptions(accountId, limit, offset)
            //result shouldBe ("TODO")
        }

        // to test getAccountInscriptionsHistory
        should("test getAccountInscriptionsHistory") {
            // uncomment below to test getAccountInscriptionsHistory
            //val accountId : kotlin.String = 0:97264395BD65A255A429B11326C84128B7D70FFED7949ABAE3036D506BA38621 // kotlin.String | account ID
            //val acceptLanguage : kotlin.String = ru-RU,ru;q=0.5 // kotlin.String | 
            //val beforeLt : kotlin.Long = 25758317000002 // kotlin.Long | omit this parameter to get last events
            //val limit : kotlin.Int = 100 // kotlin.Int | 
            //val result : AccountEvents = apiInstance.getAccountInscriptionsHistory(accountId, acceptLanguage, beforeLt, limit)
            //result shouldBe ("TODO")
        }

        // to test getAccountInscriptionsHistoryByTicker
        should("test getAccountInscriptionsHistoryByTicker") {
            // uncomment below to test getAccountInscriptionsHistoryByTicker
            //val accountId : kotlin.String = 0:97264395BD65A255A429B11326C84128B7D70FFED7949ABAE3036D506BA38621 // kotlin.String | account ID
            //val ticker : kotlin.String = nano // kotlin.String | 
            //val acceptLanguage : kotlin.String = ru-RU,ru;q=0.5 // kotlin.String | 
            //val beforeLt : kotlin.Long = 25758317000002 // kotlin.Long | omit this parameter to get last events
            //val limit : kotlin.Int = 100 // kotlin.Int | 
            //val result : AccountEvents = apiInstance.getAccountInscriptionsHistoryByTicker(accountId, ticker, acceptLanguage, beforeLt, limit)
            //result shouldBe ("TODO")
        }

        // to test getInscriptionOpTemplate
        should("test getInscriptionOpTemplate") {
            // uncomment below to test getInscriptionOpTemplate
            //val type : kotlin.String = ton20 // kotlin.String | 
            //val operation : kotlin.String = transfer // kotlin.String | 
            //val amount : kotlin.String = 1000000000 // kotlin.String | 
            //val ticker : kotlin.String = nano // kotlin.String | 
            //val who : kotlin.String = UQAs87W4yJHlF8mt29ocA4agnMrLsOP69jC1HPyBUjJay7Mg // kotlin.String | 
            //val destination : kotlin.String = destination_example // kotlin.String | 
            //val comment : kotlin.String = comment_example // kotlin.String | 
            //val result : GetInscriptionOpTemplate200Response = apiInstance.getInscriptionOpTemplate(type, operation, amount, ticker, who, destination, comment)
            //result shouldBe ("TODO")
        }

    }
}