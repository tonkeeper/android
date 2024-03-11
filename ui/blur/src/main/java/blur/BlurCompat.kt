package blur

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Build
import androidx.annotation.RequiresApi
import blur.node.api31.BlurNode
import blur.node.api31.ContentNode
import blur.node.api26.BlurNodeLegacy
import blur.node.api26.ContentNodeLegacy

class BlurCompat(context: Context) {

    private val impl = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Impl31(context)
    /*} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Impl26(context) */
    } else {
        Impl(context)
    }

    val hasBlur: Boolean
        get() = impl.hasBlur

    fun draw(input: Canvas, callback: (output: Canvas) -> Unit) {
        impl.draw(input, callback)
    }

    fun setSafeArea(rect: RectF) {
        impl.setSafeArea(rect)
    }

    fun setSafeArea(left: Float, top: Float, right: Float, bottom: Float) {
        setSafeArea(RectF(left, top, right, bottom))
    }

    fun setSafeArea(left: Float, top: Float, size: Int) {
        setSafeArea(left, top, left + size, top + size)
    }

    fun attached() {
        impl.attached()
    }

    fun detached() {
        impl.detached()
    }

    private open class Impl(val context: Context) {

        open val hasBlur: Boolean = false

        open fun draw(canvas: Canvas, callback: (output: Canvas) -> Unit) {

        }

        open fun setSafeArea(rect: RectF) {

        }

        open fun attached() {

        }

        open fun detached() {

        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private class Impl26(context: Context): Impl(context) {

        override val hasBlur: Boolean = true

        private val contentNode = ContentNodeLegacy()
        private val blurNode = BlurNodeLegacy(context)

        override fun draw(canvas: Canvas, callback: (output: Canvas) -> Unit) {
            contentNode.draw(canvas, callback)
            blurNode.setSnapshot(contentNode.capture(callback))
            blurNode.draw(canvas, callback)
        }

        override fun setSafeArea(rect: RectF) {
            super.setSafeArea(rect)
            contentNode.setBounds(rect)
            blurNode.setBounds(rect)
        }

        override fun detached() {
            super.detached()
            contentNode.release()
            blurNode.release()
        }
    }

    @RequiresApi(31)
    private class Impl31(context: Context): Impl(context) {

        override val hasBlur: Boolean = true

        private val contentNode = ContentNode()
        private val blurNode = BlurNode(context)

        override fun draw(canvas: Canvas, callback: (output: Canvas) -> Unit) {
            contentNode.draw(canvas, callback)
            blurNode.draw(canvas) {
                contentNode.drawRecorded(it)
            }
        }

        override fun setSafeArea(rect: RectF) {
            super.setSafeArea(rect)
            contentNode.setBounds(rect)
            blurNode.setBounds(rect)
        }
    }
}