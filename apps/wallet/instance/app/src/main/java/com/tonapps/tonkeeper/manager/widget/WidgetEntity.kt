package com.tonapps.tonkeeper.manager.widget

import com.tonapps.tonkeeper.core.widget.Widget

data class WidgetEntity(
    val id: Int,
    val params: Widget.Params,
    val type: String
)