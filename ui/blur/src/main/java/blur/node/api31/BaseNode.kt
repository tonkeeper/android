package blur.node.api31

import android.graphics.Canvas
import android.graphics.RenderNode
import android.os.Build
import androidx.annotation.RequiresApi
import blur.node.NodeCompat

@RequiresApi(Build.VERSION_CODES.S)
internal abstract class BaseNode(name: String): NodeCompat(name) {

    val node = RenderNode(name).apply {
        setHasOverlappingRendering(false)
    }

    override fun drawRecorded(canvas: Canvas) {
        canvas.drawRenderNode(node)
    }
}