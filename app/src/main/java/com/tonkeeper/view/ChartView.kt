package uikit.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class ChartView  @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private val data = listOf(
        1702852200 to 2.16709302533331457f,
        1702852500 to 2.1633433333236974f,
        1702855762 to 2.1473308786411884f
    )

    private val paint = Paint().apply {
        color = 0xFF0000FF.toInt() // Blue color
        strokeWidth = 5f
        isAntiAlias = true
    }

    fun setData(list: List<ChartEntity>) {

    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.RED)

        // Scaling the data
        val minX = data.minOf { it.first }.toDouble()
        val maxX = data.maxOf { it.first }.toDouble()
        val minY = data.minOf { it.second }
        val maxY = data.maxOf { it.second }

        val widthScale = width / (maxX - minX)
        val heightScale = height / (maxY - minY)

        val scaledData = data.map { (x, y) ->
            ((x - minX) * widthScale).toFloat() to height - ((y - minY) * heightScale).toFloat()
        }

        // Drawing the graph
        for (i in 0 until scaledData.size - 1) {
            val (x1, y1) = scaledData[i]
            val (x2, y2) = scaledData[i + 1]
            canvas.drawLine(x1, y1, x2, y2, paint)
        }
    }
}