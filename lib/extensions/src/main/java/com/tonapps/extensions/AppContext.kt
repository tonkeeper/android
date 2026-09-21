package com.tonapps.extensions

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context

@SuppressLint("StaticFieldLeak")
object AppContext {

    @Volatile
    private lateinit var context: Context

    fun initialize(application: Application) {
        context = application
    }

    val value get() = context
}
