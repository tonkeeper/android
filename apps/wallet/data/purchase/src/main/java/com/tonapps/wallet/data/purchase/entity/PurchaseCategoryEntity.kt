package com.tonapps.wallet.data.purchase.entity

import android.os.Parcelable
import com.tonapps.extensions.toStringList
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import org.json.JSONObject

@Parcelize
data class PurchaseCategoryEntity(
    val type: String,
    val title: String,
    val subtitle: String,
    val assets: List<String>,
    val items: List<PurchaseMethodEntity>,
): Parcelable {

    companion object {

        fun parse(array: JSONArray): List<PurchaseCategoryEntity> {
            val list = mutableListOf<PurchaseCategoryEntity>()
            for (i in 0 until array.length()) {
                list.add(PurchaseCategoryEntity(array.getJSONObject(i)))
            }
            return list
        }
    }

    constructor(json: JSONObject) : this(
        type = json.getString("type"),
        title = json.getString("title"),
        subtitle = json.getString("subtitle"),
        assets = json.optJSONArray("assets")?.toStringList() ?: emptyList(),
        items = json.optJSONArray("items")?.let { PurchaseMethodEntity.parse(it) } ?: emptyList()
    )
}