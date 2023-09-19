package com.tonkeeper.uikit.base

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tonkeeper.uikit.R

open class BaseActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_App)
        super.onCreate(savedInstanceState)
    }

}