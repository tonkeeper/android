package com.tonapps.tonkeeper.manager.theme

import android.content.res.Resources

class MainResourcesWrapper(val resources: Resources): Resources(resources.assets, resources.displayMetrics, resources.configuration) {

    override fun getText(id: Int): CharSequence {
        return super.getText(id)
    }
}