package uikit.navigation

import android.content.Context
import android.os.Bundle

abstract class ScreenResultContract<O>(
    private val requestKey: String,
    private val context: Context
) {

    private val navigation: Navigation?
        get() = Navigation.from(context)

    fun setResult(data: O) {
        navigation?.setFragmentResult(requestKey, createResult(data))
    }

    abstract fun createResult(data: O): Bundle

    abstract fun parseResult(bundle: Bundle): O
}