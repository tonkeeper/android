package com.tonapps.tonkeeper.manager.widget

import android.content.Context
import android.content.SharedPreferences
import com.tonapps.extensions.constructor
import com.tonapps.tonkeeper.core.widget.Widget

internal class WidgetSettings(context: Context) {

    private val prefs = context.getSharedPreferences("widget", Context.MODE_PRIVATE)

    fun setParams(widgetId: Int, params: Widget.Params) {
        params.save(keyParamsPrefix(widgetId), prefs)
    }

     inline fun <reified T: Widget.Params> getParams(widgetId: Int): T? {
         val params = resolveParams<T>(widgetId) ?: return null
         if (params.isEmpty) {
             return null
         }
         return params
     }

    private inline fun <reified T : Widget.Params> resolveParams(widgetId: Int): T? {
        return try {
            val constructor = T::class.constructor(String::class, SharedPreferences::class)
            constructor.newInstance(keyParamsPrefix(widgetId), prefs)
        } catch (e: Throwable) {
            null
        }
    }

    private fun keyParamsPrefix(widgetId: Int): String {
        return "params.$widgetId"
    }
}