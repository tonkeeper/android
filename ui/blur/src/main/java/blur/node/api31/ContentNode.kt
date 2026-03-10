package blur.node.api31

import android.graphics.Canvas
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.S)
internal class ContentNode: BaseNode("content") {

    override fun draw(input: Canvas, drawOutput: (output: Canvas) -> Unit) {
        node.setPosition(0, 0, input.width, input.height)
        val renderCanvas = node.beginRecording()
        try {
            drawOutput(renderCanvas)
        } finally {
            node.endRecording()
        }
        val save = input.save()
        input.clipOutRect(bounds)
        input.drawRenderNode(node)
        input.restoreToCount(save)
    }
}