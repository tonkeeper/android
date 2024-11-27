package com.tonapps.tonkeeper.ui.screen.collectibles.manage

import android.content.Context
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.extensions.short8
import com.tonapps.tonkeeper.extensions.copyToClipboard
import com.tonapps.tonkeeper.ui.screen.collectibles.manage.list.Item
import com.tonapps.tonkeeperx.R
import uikit.dialog.modal.ModalDialog
import uikit.widget.FrescoView
import uikit.widget.ModalHeader

class CollectionSpamDialog(context: Context): ModalDialog(context, R.layout.dialog_token_spam) {

    private val headerView = findViewById<ModalHeader>(R.id.header)!!
    private val nameView = findViewById<AppCompatTextView>(R.id.name)!!
    private val iconView = findViewById<FrescoView>(R.id.icon)!!
    private val addressView = findViewById<AppCompatTextView>(R.id.address)!!
    private val button = findViewById<Button>(R.id.button)!!
    private val rowAddressView = findViewById<View>(R.id.row_address)!!

    init {
        headerView.onCloseClick = { dismiss() }
    }

    fun show(item: Item.Collection, notSpamCallback: () -> Unit) {
        super.show()
        nameView.text = item.title
        iconView.setImageURI(item.imageUri, null)
        addressView.text = item.address.short8
        button.setOnClickListener {
            notSpamCallback()
            dismiss()
        }
        rowAddressView.setOnClickListener {
            context.copyToClipboard(item.address)
        }
    }
}