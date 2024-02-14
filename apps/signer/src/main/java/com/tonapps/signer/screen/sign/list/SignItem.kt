package com.tonapps.signer.screen.sign.list

sealed class SignItem(
    type: Int,
    val position: com.tonapps.uikit.list.ListCell.Position
): com.tonapps.uikit.list.BaseListItem(type) {

    companion object {
        const val UNKNOWN = 1
        const val SEND = 2
    }

    class Unknown(position: com.tonapps.uikit.list.ListCell.Position): SignItem(UNKNOWN, position)

    class Send(
        val target: String,
        val value: String,
        val value2: String?,
        val comment: String?,
        val extra: Boolean,
        position: com.tonapps.uikit.list.ListCell.Position
    ): SignItem(SEND, position)
}