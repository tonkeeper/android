package com.tonapps.wallet.data.purchase.entity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import org.json.JSONObject

@Parcelize
data class PurchaseMethodEntity(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val iconUrl: String,
    val infoButtons: List<Button>,
    val actionButton: Button,
    val successUrlPattern: SuccessUrlPattern?,
): Parcelable {

    val useCustomTabs: Boolean
        get() = id == "mercuryo"

    companion object {

        fun parse(array: JSONArray): List<PurchaseMethodEntity> {
            val list = mutableListOf<PurchaseMethodEntity>()
            for (i in 0 until array.length()) {
                list.add(PurchaseMethodEntity(array.getJSONObject(i)))
            }
            return list
        }
    }


    @Parcelize
    data class Button(
        val title: String,
        val url: String
    ): Parcelable {

        companion object {
            fun parse(array: JSONArray): List<Button> {
                val list = mutableListOf<Button>()
                for (i in 0 until array.length()) {
                    list.add(Button(array.getJSONObject(i)))
                }
                return list
            }
        }

        constructor(json: JSONObject) : this(
            title = json.getString("title"),
            url = json.getString("url")
        )
    }

    @Parcelize
    data class SuccessUrlPattern(
        val pattern: String,
        val purchaseIdIndex: Int
    ): Parcelable {

        constructor(json: JSONObject) : this(
            pattern = json.getString("pattern"),
            purchaseIdIndex = json.optInt("purchaseIdIndex", -1)
        )
    }

    constructor(json: JSONObject) : this(
        id = json.getString("id"),
        title = json.getString("title"),
        subtitle = json.getString("subtitle"),
        description = json.getString("description"),
        iconUrl = json.getString("icon_url"),
        successUrlPattern = json.optJSONObject("successUrlPattern")?.let { SuccessUrlPattern(it) },
        infoButtons = json.optJSONArray("info_buttons")?.let { array ->
            Button.parse(array)
        } ?: emptyList(),
        actionButton = Button(json.getJSONObject("action_button")),
    )
}