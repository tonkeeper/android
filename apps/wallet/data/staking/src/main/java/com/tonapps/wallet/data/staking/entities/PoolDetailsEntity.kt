package com.tonapps.wallet.data.staking.entities

import android.os.Parcelable
import io.tonapi.models.PoolImplementation
import kotlinx.parcelize.Parcelize

@Parcelize
data class PoolDetailsEntity(
    val name: String,
    val description: String,
    val url: String,
    val additionalUrl: String? = null,
    val socials: List<String>,
): Parcelable {

    constructor(model: PoolImplementation) : this(
        name = model.name,
        description = model.description,
        url = model.url,
        socials = model.socials
    )

    fun getLinks(address: String): List<String> {
        val links = mutableListOf(url)
        additionalUrl?.let { links.add(it) }
        links.add("https://tonviewer.com/${address}")
        links.addAll(socials)
        return links.toList()
    }

}