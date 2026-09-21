package com.tonapps.core.helper

import android.content.Context
import com.tonapps.core.navigation.NavigationDelegate
import uikit.extensions.activity

val Context.navigationDelegate: NavigationDelegate?
    get() {
        return activity as? NavigationDelegate
    }
