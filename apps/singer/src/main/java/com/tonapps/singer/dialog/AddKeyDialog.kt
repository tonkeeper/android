package com.tonapps.singer.dialog

import android.content.Context
import android.widget.Button
import com.tonapps.singer.R
import com.tonapps.singer.screen.create.CreateFragment
import uikit.base.BaseSheetDialog
import uikit.navigation.Navigation.Companion.navigation

class AddKeyDialog(context: Context): BaseSheetDialog(context) {

    private val createButton: Button
    private val importButton: Button

    init {
        setContentView(R.layout.dialog_add_key)

        createButton = findViewById(R.id.create)!!
        createButton.setOnClickListener {
            openCreate(false)
        }

        importButton = findViewById(R.id.imprt)!!
        importButton.setOnClickListener {
            openCreate(true)
        }
    }

    private fun openCreate(import: Boolean) {
        navigation?.add(CreateFragment.newInstance(import))
        dismiss()
    }
}