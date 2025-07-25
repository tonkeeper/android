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

import io.tonapi.models.ActionSimplePreview
import io.tonapi.models.AuctionBidAction
import io.tonapi.models.ContractDeployAction
import io.tonapi.models.DepositStakeAction
import io.tonapi.models.DomainRenewAction
import io.tonapi.models.ElectionsDepositStakeAction
import io.tonapi.models.ElectionsRecoverStakeAction
import io.tonapi.models.InscriptionMintAction
import io.tonapi.models.InscriptionTransferAction
import io.tonapi.models.JettonBurnAction
import io.tonapi.models.JettonMintAction
import io.tonapi.models.JettonSwapAction
import io.tonapi.models.JettonTransferAction
import io.tonapi.models.NftItemTransferAction
import io.tonapi.models.NftPurchaseAction
import io.tonapi.models.SmartContractAction
import io.tonapi.models.SubscriptionAction
import io.tonapi.models.TonTransferAction
import io.tonapi.models.UnSubscriptionAction
import io.tonapi.models.WithdrawStakeAction
import io.tonapi.models.WithdrawStakeRequestAction

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 
 *
 * @param type 
 * @param status 
 * @param simplePreview 
 * @param baseTransactions 
 * @param tonTransfer 
 * @param contractDeploy 
 * @param jettonTransfer 
 * @param jettonBurn 
 * @param jettonMint 
 * @param nftItemTransfer 
 * @param subscribe 
 * @param unSubscribe 
 * @param auctionBid 
 * @param nftPurchase 
 * @param depositStake 
 * @param withdrawStake 
 * @param withdrawStakeRequest 
 * @param electionsDepositStake 
 * @param electionsRecoverStake 
 * @param jettonSwap 
 * @param smartContractExec 
 * @param domainRenew 
 * @param inscriptionTransfer 
 * @param inscriptionMint 
 */


data class Action (

    @Json(name = "type")
    val type: String,

    @Json(name = "status")
    val status: Action.Status,

    @Json(name = "simple_preview")
    val simplePreview: ActionSimplePreview,

    @Json(name = "base_transactions")
    val baseTransactions: kotlin.collections.List<kotlin.String>,

    @Json(name = "TonTransfer")
    val tonTransfer: TonTransferAction? = null,

    @Json(name = "ContractDeploy")
    val contractDeploy: ContractDeployAction? = null,

    @Json(name = "JettonTransfer")
    val jettonTransfer: JettonTransferAction? = null,

    @Json(name = "JettonBurn")
    val jettonBurn: JettonBurnAction? = null,

    @Json(name = "JettonMint")
    val jettonMint: JettonMintAction? = null,

    @Json(name = "NftItemTransfer")
    val nftItemTransfer: NftItemTransferAction? = null,

    @Json(name = "Subscribe")
    val subscribe: SubscriptionAction? = null,

    @Json(name = "UnSubscribe")
    val unSubscribe: UnSubscriptionAction? = null,

    @Json(name = "AuctionBid")
    val auctionBid: AuctionBidAction? = null,

    @Json(name = "NftPurchase")
    val nftPurchase: NftPurchaseAction? = null,

    @Json(name = "DepositStake")
    val depositStake: DepositStakeAction? = null,

    @Json(name = "WithdrawStake")
    val withdrawStake: WithdrawStakeAction? = null,

    @Json(name = "WithdrawStakeRequest")
    val withdrawStakeRequest: WithdrawStakeRequestAction? = null,

    @Json(name = "ElectionsDepositStake")
    val electionsDepositStake: ElectionsDepositStakeAction? = null,

    @Json(name = "ElectionsRecoverStake")
    val electionsRecoverStake: ElectionsRecoverStakeAction? = null,

    @Json(name = "JettonSwap")
    val jettonSwap: JettonSwapAction? = null,

    @Json(name = "SmartContractExec")
    val smartContractExec: SmartContractAction? = null,

    @Json(name = "DomainRenew")
    val domainRenew: DomainRenewAction? = null,

    @Json(name = "InscriptionTransfer")
    val inscriptionTransfer: InscriptionTransferAction? = null,

    @Json(name = "InscriptionMint")
    val inscriptionMint: InscriptionMintAction? = null

) {
    /**
     * 
     *
     * Values: ok,failed
     */
    @JsonClass(generateAdapter = false)
    enum class Status(val value: kotlin.String) {
        @Json(name = "ok") ok("ok"),
        @Json(name = "failed") failed("failed");
    }
}

