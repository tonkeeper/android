package com.tonapps.tonkeeper.ui.component.chart

import android.content.Context
import com.tonapps.uikit.color.accentBlueColor
import uikit.base.BaseDrawable
import uikit.extensions.dp

abstract class BaseChartDrawable(val context: Context): BaseDrawable() {

    protected val strokeSize = 2f.dp
    protected val accentColor = context.accentBlueColor

    protected val chartWidth: Float
        get() = bounds.width() + strokeSize * 2

    protected val chartHeight: Float
        get() = bounds.height() - (strokeSize * 2)

}