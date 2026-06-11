package uikit.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class ChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private val chartDrawable = ChartDrawable(context)
    private val touchIndicatorDrawable = TouchIndicatorDrawable(context)
    private val loadingDrawable = LoadingDrawable(context)

    private var selectedPoint: ChartPoint? = null
    var onPointSelected: ((ChartPoint?) -> Unit)? = null

    init {
        background = chartDrawable
        touchIndicatorDrawable.callback = this
    }

    fun setData(data: List<ChartPoint>, isSquare: Boolean) {
        if (data.isEmpty()) {
            chartDrawable.setData(emptyList(), isSquare)
        } else if (data.size > 100) {
            chartDrawable.setData(data, isSquare)
        } else {
            val expanded = mutableListOf<ChartPoint>()
            for (point in data) repeat(4) { expanded.add(point) }
            chartDrawable.setData(expanded, isSquare)
        }
    }

    override fun verifyDrawable(who: Drawable): Boolean {
        return super.verifyDrawable(who) || who == touchIndicatorDrawable
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        touchIndicatorDrawable.setBounds(0, 0, w, h)
        loadingDrawable.setBounds(0, 0, w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (chartDrawable.isEmpty) {
            loadingDrawable.draw(canvas)
        } else {
            touchIndicatorDrawable.draw(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                setTouch(event.x - paddingLeft)
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                resetTouch()
                parent?.requestDisallowInterceptTouchEvent(false)
                true
            }
            else -> super.onTouchEvent(event)
        }
    }

    private fun setTouch(x: Float) {
        selectedPoint = null
        onPointSelected?.invoke(null)
        updateSelectedPoint(x)
        selectedPoint?.let { touchIndicatorDrawable.setData(x, chartDrawable.getDotY(it)) }
    }

    private fun resetTouch() {
        selectedPoint = null
        onPointSelected?.invoke(null)
        touchIndicatorDrawable.setData(-1f, -1f)
    }

    private fun updateSelectedPoint(x: Float) {
        val entities = chartDrawable.entities
        if (entities.isEmpty()) return
        val index = (x / chartDrawable.stepX).toInt().coerceIn(0, entities.size - 1)
        val selected = entities[index]
        if (selected != selectedPoint) {
            selectedPoint = selected
            onPointSelected?.invoke(selectedPoint)
        }
    }

}
