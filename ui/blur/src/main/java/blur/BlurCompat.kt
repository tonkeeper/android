package blur

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import blur.node.api31.BlurNode
import blur.node.api31.ContentNode
import blur.node.legacy.BlurNodeLegacy
import blur.node.legacy.ContentNodeLegacy
import kotlin.system.measureTimeMillis

class BlurCompat(
    context: Context,
    enable: Boolean,
    experimental: Boolean
) {

    private val impl = if (experimental) {
        ImplExperimental(context)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && enable) {
        Impl31(context)
    } else {
        Impl(context)
    }

    val hasBlur: Boolean
        get() = impl.hasBlur

    fun draw(input: Canvas, callback: (output: Canvas) -> Unit) {
        impl.draw(input, callback)
    }

    fun setBounds(rect: RectF) {
        impl.setBounds(rect)
    }

    fun setBounds(left: Float, top: Float, right: Float, bottom: Float) {
        setBounds(RectF(left, top, right, bottom))
    }

    fun setBounds(left: Float, top: Float, size: Int) {
        setBounds(left, top, left + size, top + size)
    }

    fun attached() {
        impl.attached()
    }

    fun detached() {
        impl.detached()
    }

    private open class Impl(val context: Context) {

        open val hasBlur: Boolean = false

        private val contentNode = ContentNodeLegacy()

        open fun draw(canvas: Canvas, callback: (output: Canvas) -> Unit) {
            contentNode.draw(canvas, callback)
        }

        open fun setBounds(rect: RectF) {
            contentNode.setBounds(rect)
        }

        open fun attached() {

        }

        open fun detached() {
            contentNode.release()
        }
    }

    private class ImplExperimental(context: Context): Impl(context) {

        override val hasBlur: Boolean = true

        private val contentNode = ContentNodeLegacy()
        private val blurNode = BlurNodeLegacy(context)

        override fun draw(canvas: Canvas, callback: (output: Canvas) -> Unit) {
            contentNode.draw(canvas, callback)
            blurNode.setSnapshot(contentNode.capture(callback))
            blurNode.draw(canvas, callback)
        }

        override fun setBounds(rect: RectF) {
            super.setBounds(rect)
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

        override fun setBounds(rect: RectF) {
            super.setBounds(rect)
            contentNode.setBounds(rect)
            blurNode.setBounds(rect)
        }
    }
}