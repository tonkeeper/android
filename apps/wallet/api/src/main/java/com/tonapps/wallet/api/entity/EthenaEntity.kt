package com.tonapps.wallet.api.entity

import android.os.Parcelable
import io.tonendpointapi.models.GetStakingEthenaMethods200Response
import io.tonendpointapi.models.GetStakingEthenaMethods200ResponseAbout
import io.tonendpointapi.models.StakingEthenaMethod
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

@Parcelize
data class EthenaEntity(
    val methods: List<Method>,
    val about: About,
) : Parcelable {

    @Parcelize
    data class About(
        val description: String,
        val tsusdeDescription: String,
        val faqUrl: String,
        val aboutUrl: String,
        val stakeTitle: String,
        val stakeDescription: String,
    ) : Parcelable {
        constructor(about: GetStakingEthenaMethods200ResponseAbout) : this(
            description = about.description,
            tsusdeDescription = about.tsusdeDescription,
            faqUrl = about.faqUrl,
            aboutUrl = about.aboutUrl,
            stakeTitle = about.tsusdeStakeTitle,
            stakeDescription = about.tsusdeStakeDescription,
        )
    }

    @Parcelize
    data class Method(
        val type: Type,
        val name: String,
        val apy: BigDecimal,
        val apyTitle: String,
        val apyDescription: String,
        val bonusApy: BigDecimal?,
        val bonusTitle: String?,
        val bonusDescription: String?,
        val eligibleBonusUrl: String?,
        val depositUrl: String,
        val withdrawalUrl: String,
        val jettonMaster: String,
        val links: List<String>,
    ) : Parcelable {
        enum class Type(val id: String) {
            STONFI("stonfi"),
            AFFLUENT("affluent");

            companion object {
                fun fromId(id: String): Type {
                    return entries.find { it.id.equals(id, ignoreCase = true) }
                        ?: throw IllegalArgumentException("Invalid type: $id")
                }
            }
        }

        constructor(method: StakingEthenaMethod) : this(
            type = Type.fromId(method.type),
            name = method.name,
            apy = BigDecimal.valueOf(method.apy),
            apyTitle = method.apyTitle,
            apyDescription = method.apyDescription,
            // The spec has no bonus APY: the backend never sent `bonus_apy`.
            bonusApy = null,
            bonusTitle = method.apyBonusTitle,
            bonusDescription = method.apyBonusDescription,
            eligibleBonusUrl = method.eligibleBonusUrl,
            depositUrl = method.depositUrl,
            withdrawalUrl = method.withdrawalUrl,
            jettonMaster = method.jettonMaster,
            links = method.links
        )
    }

    constructor(response: GetStakingEthenaMethods200Response) : this(
        methods = response.methods.map(::Method),
        about = About(response.about)
    )
}
