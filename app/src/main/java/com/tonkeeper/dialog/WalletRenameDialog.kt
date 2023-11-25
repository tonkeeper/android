package com.tonkeeper.dialog

import android.content.Context
import android.widget.Button
import com.tonkeeper.App
import com.tonkeeper.R
import uikit.base.BaseSheetDialog
import uikit.widget.InputView

class WalletRenameDialog(context: Context): BaseSheetDialog(context) {

    private val nameView: InputView
    private val addressView: InputView
    private val saveButton: Button

    init {
        setTitle(R.string.rename)
        setContentView(R.layout.dialog_wallet_rename)
        nameView = findViewById(R.id.name)!!
        addressView = findViewById(R.id.address)!!
        saveButton = findViewById(R.id.save)!!
    }

    fun show(name: String, address: String) {
        super.show()
        nameView.text = name
        nameView.focus()

        addressView.text = address

        saveButton.setOnClickListener {
            val newName = nameView.text.trim()
            if (newName != name) {
                App.instance.setWalletName(address, newName)
            }
            dismiss()
        }
    }
}