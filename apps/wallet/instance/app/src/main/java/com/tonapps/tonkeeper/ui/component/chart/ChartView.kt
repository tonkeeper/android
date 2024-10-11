package com.tonapps.tonkeeper.ui.component.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.tonapps.wallet.api.entity.ChartEntity

class ChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private val chartDrawable = ChartDrawable(context)
    private val touchIndicatorDrawable = TouchIndicatorDrawable(context)
    private val loadingDrawable = LoadingDrawable(context)

    private var selectedEntity: ChartEntity? = null
    var onEntitySelected: ((ChartEntity?) -> Unit)? = null

    init {
        background = chartDrawable
        touchIndicatorDrawable.callback = this
        loadingDrawable.callback = this
    }

    fun setData(data: List<ChartEntity>, isSquare: Boolean) {
        if (data.isEmpty()) {
            chartDrawable.setData(emptyList(), isSquare)
        } else if (data.size > 100) {
            chartDrawable.setData(data, isSquare)
        } else {
            val newData = mutableListOf<ChartEntity>()
            val countCopy = 4
            for (entity in data) {
                for (i in 0 until countCopy) {
                    newData.add(entity)
                }
            }
            chartDrawable.setData(newData, isSquare)
        }

        if (width > 0 && height > 0) {
            checkLoadingAnimation()
        }
    }

    private fun checkLoadingAnimation() {
        if (chartDrawable.isEmpty) {
            loadingDrawable.startAnimation()
        } else {
            loadingDrawable.stopAnimation()
        }
    }

    override fun verifyDrawable(who: Drawable): Boolean {
        return super.verifyDrawable(who) || who == touchIndicatorDrawable || who == loadingDrawable
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        touchIndicatorDrawable.setBounds(0, 0, w, h)
        loadingDrawable.setBounds(0, 0, w, h)
        checkLoadingAnimation()
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
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                setTouch(event.x - paddingLeft)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                resetTouch()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun setTouch(x: Float) {
        selectedEntity = null
        onEntitySelected?.invoke(null)
        updateSelectedEntity(x)

        selectedEntity?.let {
            touchIndicatorDrawable.setData(x, chartDrawable.getDotY(it))
        }
    }

    private fun resetTouch() {
        selectedEntity = null
        onEntitySelected?.invoke(null)
        touchIndicatorDrawable.setData(-1f, -1f)
    }

    private fun updateSelectedEntity(x: Float) {
        val entities = chartDrawable.entities
        if (entities.isEmpty()) {
            return
        }
        val index = (x / chartDrawable.stepX).toInt().coerceIn(0, entities.size - 1)
        val selected = entities[index]
        if (selected != selectedEntity) {
            selectedEntity = selected
            onEntitySelected?.invoke(selectedEntity)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        loadingDrawable.stopAnimation()
    }
}