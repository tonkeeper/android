package com.tonapps.core.helper

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast

@SuppressLint("StaticFieldLeak")
object T {

    private lateinit var context: Context

    fun initialize(context: Context) {
        this.context = context
    }

    fun show(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG)
            .show()
    }
}
