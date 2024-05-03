package blur.node.api26

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.O)
internal class ContentNodeLegacy: BaseNode("content") {

    private companion object {
        private const val MAX_SIZE = 92
    }

    private var snapshotCanvas = SnapshotCanvas(1, 1)

    fun capture(drawOutput: (output: Canvas) -> Unit): Bitmap {
        return snapshotCanvas.capture(drawOutput)
    }

    override fun draw(input: Canvas, drawOutput: (output: Canvas) -> Unit) {
        val save = input.save()
        input.clipOutRect(bounds)
        drawOutput(input)
        input.restoreToCount(save)
    }

    override fun onBoundsChange(bounds: RectF) {
        super.onBoundsChange(bounds)
        newSnapshot(bounds.width().toInt(), bounds.height().toInt())
        snapshotCanvas.setTranslate(-bounds.left, -bounds.top)
    }

    private fun newSnapshot(originalWidth: Int, originalHeight: Int) {
        val (width, height) = calcNewSize(originalWidth, originalHeight)
        if (width == 0 || height == 0) {
            return
        }
        if (snapshotCanvas.width == width && snapshotCanvas.height == height) {
            return
        }

        snapshotCanvas.release()

        snapshotCanvas = SnapshotCanvas(width, height)
        snapshotCanvas.setScale(width.toFloat() / originalWidth, height.toFloat() / originalHeight)
    }

    private fun calcNewSize(width: Int, height: Int): Pair<Int, Int> {
        return if (width > height) {
            val ratio = width / MAX_SIZE
            val newWidth = MAX_SIZE
            val newHeight = height / ratio
            Pair(newWidth, newHeight)
        } else if (height > width) {
            val ratio = height / MAX_SIZE
            val newHeight = MAX_SIZE
            val newWidth = width / ratio
            Pair(newWidth, newHeight)
        } else {
            Pair(MAX_SIZE, MAX_SIZE)
        }
    }

    override fun release() {
        snapshotCanvas.release()
    }
}