package com.tonapps.tonkeeper.ui.screen.country

import android.content.Context
import android.os.Bundle
import uikit.navigation.ScreenResultContract

class CountryResultContract(
    requestKey: String,
    context: Context
): ScreenResultContract<String>(requestKey, context) {

    private companion object {
        private const val KEY_CODE = "code"
    }

    override fun createResult(data: String) = Bundle().apply {
        putString(KEY_CODE, data)
    }

    override fun parseResult(bundle: Bundle): String {
        return bundle.getString(KEY_CODE)!!
    }

}