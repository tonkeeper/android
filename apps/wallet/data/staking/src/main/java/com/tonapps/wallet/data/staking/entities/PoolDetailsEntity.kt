package com.tonapps.wallet.data.staking.entities

import android.os.Parcelable
import io.tonapi.models.PoolImplementation
import kotlinx.parcelize.Parcelize

@Parcelize
data class PoolDetailsEntity(
    val name: String,
    val description: String,
    val url: String,
    val socials: List<String>,
): Parcelable {

    constructor(model: PoolImplementation) : this(
        name = model.name,
        description = model.description,
        url = model.url,
        socials = model.socials
    )

    fun getLinks(address: String): List<String> {
        val links = mutableListOf(url, "https://tonviewer.com/${address}")
        links.addAll(socials)
        return links.toList()
    }

    companion object {

        val ethena = PoolDetailsEntity(
            name = "Ethena",
            description = "The tsUSDe token represents USDe held through Ethena, a decentralized staking service. tsUSDe automatically accrues staking rewards, with minimal lock up.",
            url = "https://ethena.fi/",
            socials = listOf("https://t.me/ethena_labs", "https://x.com/ethena_labs", "https://github.com/ethena-labs")
        )
    }
}