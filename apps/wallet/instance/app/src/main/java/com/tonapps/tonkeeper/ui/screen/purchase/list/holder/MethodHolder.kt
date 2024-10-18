package com.tonapps.tonkeeper.ui.screen.purchase.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.purchase.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.purchase.entity.PurchaseMethodEntity
import uikit.extensions.drawable
import uikit.widget.FrescoView

class MethodHolder(
    parent: ViewGroup,
    private val onClick: (PurchaseMethodEntity) -> Unit
): Holder<Item.Method>(parent, R.layout.view_purchase_method) {

    private val iconView = findViewById<FrescoView>(R.id.icon)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val descriptionView = findViewById<AppCompatTextView>(R.id.description)

    override fun onBind(item: Item.Method) {
        itemView.setOnClickListener { onClick(item.entity) }
        itemView.background = item.position.drawable(context)
        iconView.setImageURI(item.iconUri)
        titleView.text = item.title
        descriptionView.text = item.description
    }

}